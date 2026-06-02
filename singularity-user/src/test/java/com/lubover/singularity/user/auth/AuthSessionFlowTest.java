package com.lubover.singularity.user.auth;

import com.lubover.singularity.user.controller.UserController;
import com.lubover.singularity.user.entity.User;
import com.lubover.singularity.user.exception.GlobalExceptionHandler;
import com.lubover.singularity.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthSessionFlowTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthFilter authFilter = new AuthFilter(jwtProvider, tokenBlacklistService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(userController)
                .addFilters(authFilter)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void meWithoutTokenShouldReturn401Missing() throws Exception {
        mockMvc.perform(get("/api/user/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_MISSING"));
    }

    @Test
    void meWithInvalidTokenShouldReturn401Invalid() throws Exception {
        when(jwtProvider.parseAndValidate(anyString()))
                .thenThrow(TokenValidationException.invalid("bad token"));

        mockMvc.perform(get("/api/user/me")
                .header("Authorization", "Bearer bad"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void meWithMalformedAuthorizationHeaderShouldReturn401Invalid() throws Exception {
        mockMvc.perform(get("/api/user/me")
                .header("Authorization", "Token abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void meWithExpiredTokenShouldReturn401Expired() throws Exception {
        when(jwtProvider.parseAndValidate(anyString()))
                .thenThrow(TokenValidationException.expired("expired token"));

        mockMvc.perform(get("/api/user/me")
                .header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_EXPIRED"));
    }

    @Test
    void meWithNonNumericSubjectShouldReturn401Invalid() throws Exception {
        JwtProvider.JwtClaims claims = claims("not-number", "normal", "jti-x", 4102444800L);
        when(jwtProvider.parseAndValidate("bad-sub-token")).thenReturn(claims);

        mockMvc.perform(get("/api/user/me")
                .header("Authorization", "Bearer bad-sub-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void meWithMissingRoleShouldReturn401Invalid() throws Exception {
        JwtProvider.JwtClaims claims = claims("1001", "", "jti-role", 4102444800L);
        when(jwtProvider.parseAndValidate("missing-role-token")).thenReturn(claims);

        mockMvc.perform(get("/api/user/me")
                .header("Authorization", "Bearer missing-role-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void logoutWithoutTokenShouldReturn401Missing() throws Exception {
        mockMvc.perform(post("/api/user/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_MISSING"));
    }

    @Test
    void logoutWithInvalidTokenShouldReturn401Invalid() throws Exception {
        when(jwtProvider.parseAndValidate(anyString()))
                .thenThrow(TokenValidationException.invalid("bad token"));

        mockMvc.perform(post("/api/user/logout")
            .header("Authorization", "Bearer bad"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void logoutWithExpiredTokenShouldReturn401Expired() throws Exception {
        when(jwtProvider.parseAndValidate(anyString()))
                .thenThrow(TokenValidationException.expired("expired token"));

        mockMvc.perform(post("/api/user/logout")
            .header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_EXPIRED"));
    }

    @Test
    void logoutShouldBlacklistAndMeShouldBeRejected() throws Exception {
        JwtProvider.JwtClaims claims = claims("1001", "normal", "jti-1", 4102444800L);
        when(jwtProvider.parseAndValidate("good-token")).thenReturn(claims);
        when(tokenBlacklistService.isBlacklisted("jti-1")).thenReturn(false, true);

        mockMvc.perform(post("/api/user/logout")
                .header("Authorization", "Bearer good-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/user/me")
                .header("Authorization", "Bearer good-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void repeatLogoutShouldReturn200() throws Exception {
        JwtProvider.JwtClaims claims = claims("1001", "normal", "jti-2", 4102444800L);
        when(jwtProvider.parseAndValidate("repeat-token")).thenReturn(claims);
        when(tokenBlacklistService.isBlacklisted("jti-2")).thenReturn(true);

        mockMvc.perform(post("/api/user/logout")
                .header("Authorization", "Bearer repeat-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void meShouldReturnPublicUserInfo() throws Exception {
        JwtProvider.JwtClaims claims = claims("1001", "normal", "jti-3", 4102444800L);
        when(jwtProvider.parseAndValidate("ok-token")).thenReturn(claims);
        when(tokenBlacklistService.isBlacklisted("jti-3")).thenReturn(false);
        when(userService.getUserById(anyLong())).thenReturn(mockUser());

        mockMvc.perform(get("/api/user/me")
                .header("Authorization", "Bearer ok-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("alice01"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void adminEndpointWithNormalRoleShouldReturn403() throws Exception {
        JwtProvider.JwtClaims claims = claims("1001", "normal", "jti-4", 4102444800L);
        when(jwtProvider.parseAndValidate("normal-token")).thenReturn(claims);
        when(tokenBlacklistService.isBlacklisted("jti-4")).thenReturn(false);

        mockMvc.perform(get("/api/user/admin/ping")
                .header("Authorization", "Bearer normal-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void adminEndpointWithoutTokenShouldReturn401Missing() throws Exception {
        mockMvc.perform(get("/api/user/admin/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_MISSING"));
    }

    @Test
    void adminEndpointWithAdminRoleShouldReturn200() throws Exception {
        JwtProvider.JwtClaims claims = claims("1002", "admin", "jti-5", 4102444800L);
        when(jwtProvider.parseAndValidate("admin-token")).thenReturn(claims);
        when(tokenBlacklistService.isBlacklisted("jti-5")).thenReturn(false);

        mockMvc.perform(get("/api/user/admin/ping")
                .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.message").value("admin ok"));
    }

    private JwtProvider.JwtClaims claims(String sub, String role, String jti, long exp) {
        JwtProvider.JwtClaims claims = new JwtProvider.JwtClaims();
        claims.setSub(sub);
        claims.setRole(role);
        claims.setJti(jti);
        claims.setIat(1700000000L);
        claims.setExp(exp);
        return claims;
    }

    private User mockUser() {
        User user = new User();
        user.setId(1001L);
        user.setUsername("alice01");
        user.setPassword("$2a$10$hash");
        user.setNickname("Alice");
        user.setRole("normal");
        return user;
    }
}
