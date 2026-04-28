package com.lubover.singularity.user.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lubover.singularity.user.dto.ApiResponse;
import com.lubover.singularity.user.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class AuthFilter extends OncePerRequestFilter {

    public static final String ATTR_AUTH_CONTEXT = "auth.context";
    public static final String ATTR_USER_ID = "auth.userId";
    public static final String ATTR_ROLE = "auth.role";
    public static final String ATTR_JTI = "auth.jti";
    public static final String ATTR_EXP = "auth.exp";

    private static final String PATH_ME = "/api/user/me";
    private static final String PATH_LOGOUT = "/api/user/logout";
    private static final String PATH_ADMIN_PING = "/api/user/admin/ping";
    private static final Set<String> PROTECTED_PATHS = Set.of(PATH_ME, PATH_LOGOUT, PATH_ADMIN_PING);
    private static final Set<String> PROTECTED_PREFIXES = Set.of("/api/product/", "/api/inventory/");

    private final JwtProvider jwtProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthFilter(JwtProvider jwtProvider, TokenBlacklistService tokenBlacklistService) {
        this.jwtProvider = jwtProvider;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (PROTECTED_PATHS.contains(uri)) {
            return false;
        }
        for (String prefix : PROTECTED_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || authHeader.isBlank()) {
            writeUnauthorized(response, ErrorCode.AUTH_TOKEN_MISSING, "token missing");
            return;
        }
        if (!authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, ErrorCode.AUTH_TOKEN_INVALID, "token invalid");
            return;
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            writeUnauthorized(response, ErrorCode.AUTH_TOKEN_INVALID, "token invalid");
            return;
        }

        JwtProvider.JwtClaims claims;
        try {
            claims = jwtProvider.parseAndValidate(token);
        } catch (TokenValidationException exception) {
            if (exception.getReason() == TokenValidationException.Reason.EXPIRED) {
                writeUnauthorized(response, ErrorCode.AUTH_TOKEN_EXPIRED, "token expired");
            } else {
                writeUnauthorized(response, ErrorCode.AUTH_TOKEN_INVALID, "token invalid");
            }
            return;
        }

        AuthRequestContext authContext;
        try {
            authContext = buildAuthContext(claims);
        } catch (IllegalArgumentException exception) {
            writeUnauthorized(response, ErrorCode.AUTH_TOKEN_INVALID, "token invalid");
            return;
        }

        boolean blacklisted = tokenBlacklistService.isBlacklisted(claims.getJti());
        boolean isLogout = PATH_LOGOUT.equals(request.getRequestURI());
        if (blacklisted && !isLogout) {
            writeUnauthorized(response, ErrorCode.AUTH_TOKEN_INVALID, "token invalid");
            return;
        }

        request.setAttribute(ATTR_AUTH_CONTEXT, authContext);
        request.setAttribute(ATTR_USER_ID, authContext.getUserId());
        request.setAttribute(ATTR_ROLE, authContext.getRole());
        request.setAttribute(ATTR_JTI, authContext.getJti());
        request.setAttribute(ATTR_EXP, authContext.getExp());

        AuthRequestContext.setCurrentUserId(authContext.getUserId());
        AuthRequestContext.setCurrentRole(authContext.getRole());

        try {
            if (PATH_ADMIN_PING.equals(request.getRequestURI()) && !"admin".equalsIgnoreCase(authContext.getRole())) {
                writeForbidden(response, ErrorCode.AUTH_FORBIDDEN, "forbidden");
                return;
            }

            filterChain.doFilter(request, response);
        } finally {
            AuthRequestContext.clear();
        }
    }

    private AuthRequestContext buildAuthContext(JwtProvider.JwtClaims claims) {
        Long userId;
        try {
            userId = Long.parseLong(claims.getSub());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid sub");
        }

        String role = claims.getRole();
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Invalid role");
        }

        String jti = claims.getJti();
        if (jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("Invalid jti");
        }

        long exp = claims.getExp();
        if (exp <= 0) {
            throw new IllegalArgumentException("Invalid exp");
        }

        return new AuthRequestContext(userId, role, jti, exp);
    }

    private void writeUnauthorized(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        writeError(response, errorCode, message);
    }

    private void writeForbidden(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        writeError(response, errorCode, message);
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(errorCode.getCode(), message)));
    }
}
