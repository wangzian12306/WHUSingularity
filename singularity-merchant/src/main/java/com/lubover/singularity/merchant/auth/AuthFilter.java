package com.lubover.singularity.merchant.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lubover.singularity.merchant.dto.ApiResponse;
import com.lubover.singularity.merchant.exception.ErrorCode;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AuthFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String authHeader = request.getHeader(AUTH_HEADER);

            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                filterChain.doFilter(request, response);
                return;
            }

            String token = authHeader.substring(BEARER_PREFIX.length());

            if (tokenBlacklistService.isTokenBlacklisted(token)) {
                sendError(response, ErrorCode.AUTH_TOKEN_INVALID);
                return;
            }

            JWTClaimsSet claims = jwtProvider.verifyToken(token);
            Long merchantId = jwtProvider.getMerchantIdFromClaims(claims);

            if (merchantId == null) {
                sendError(response, ErrorCode.AUTH_TOKEN_INVALID);
                return;
            }

            AuthRequestContext.setMerchantId(merchantId);
            filterChain.doFilter(request, response);
        } catch (TokenValidationException e) {
            ErrorCode errorCode = e.getMessage().contains("expired") 
                ? ErrorCode.AUTH_TOKEN_EXPIRED 
                : ErrorCode.AUTH_TOKEN_INVALID;
            sendError(response, errorCode);
        } finally {
            AuthRequestContext.clear();
        }
    }

    private void sendError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> apiResponse = ApiResponse.failure(errorCode.getCode(), errorCode.getDefaultMessage());
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
