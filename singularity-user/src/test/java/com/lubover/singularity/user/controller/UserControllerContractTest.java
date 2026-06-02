package com.lubover.singularity.user.controller;

import com.lubover.singularity.user.auth.JwtProvider;
import com.lubover.singularity.user.auth.TokenBlacklistService;
import com.lubover.singularity.user.entity.User;
import com.lubover.singularity.user.exception.BusinessException;
import com.lubover.singularity.user.exception.ErrorCode;
import com.lubover.singularity.user.exception.GlobalExceptionHandler;
import com.lubover.singularity.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerContractTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @InjectMocks
    private UserController userController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void registerShouldReturn201AndNotExposePassword() throws Exception {
        User user = mockUser();
        when(userService.register(anyString(), anyString(), anyString())).thenReturn(user);

        mockMvc.perform(post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "alice01",
                      "password": "P@ssw0rd!",
                      "nickname": "Alice"
                    }
                    """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("alice01"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void loginShouldReturnContractShape() throws Exception {
        User user = mockUser();
        when(userService.login(anyString(), anyString())).thenReturn(user);
        when(jwtProvider.generateToken(anyLong(), anyString())).thenReturn("jwt-token-123");
        when(jwtProvider.getExpireSeconds()).thenReturn(7200L);

        mockMvc.perform(post("/api/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "alice01",
                      "password": "P@ssw0rd!"
                    }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("jwt-token-123"))
                .andExpect(jsonPath("$.data.accessToken", not(isEmptyOrNullString())))
                .andExpect(jsonPath("$.data.expiresIn").value(7200))
                .andExpect(jsonPath("$.data.user.username").value("alice01"))
                .andExpect(jsonPath("$.data.user.password").doesNotExist());
    }

    @Test
    void loginBadCredentialsShouldReturn401AndErrorCode() throws Exception {
        when(userService.login(anyString(), anyString())).thenThrow(new BusinessException(ErrorCode.AUTH_BAD_CREDENTIALS));

        mockMvc.perform(post("/api/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "alice01",
                      "password": "badpass"
                    }
                    """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_BAD_CREDENTIALS"));
    }

    @Test
    void duplicateRegisterShouldReturn409AndErrorCode() throws Exception {
        when(userService.register(anyString(), anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.USER_USERNAME_EXISTS));

        mockMvc.perform(post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "alice01",
                      "password": "P@ssw0rd!",
                      "nickname": "Alice"
                    }
                    """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_USERNAME_EXISTS"));
    }

        @Test
        void registerInvalidParamShouldReturn400AndErrorCode() throws Exception {
            when(userService.register(anyString(), anyString(), nullable(String.class)))
                                .thenThrow(new BusinessException(ErrorCode.REQ_INVALID_PARAM));

                mockMvc.perform(post("/api/user/register")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("""
                                        {
                                            "username": "bad!",
                                            "password": "123"
                                        }
                                        """))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.success").value(false))
                                .andExpect(jsonPath("$.error.code").value("REQ_INVALID_PARAM"));
        }

        @Test
        void loginInvalidParamShouldReturn400AndErrorCode() throws Exception {
                when(userService.login(anyString(), anyString()))
                                .thenThrow(new BusinessException(ErrorCode.REQ_INVALID_PARAM));

                mockMvc.perform(post("/api/user/login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("""
                                        {
                                            "username": "alice01",
                                            "password": ""
                                        }
                                        """))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.success").value(false))
                                .andExpect(jsonPath("$.error.code").value("REQ_INVALID_PARAM"));
        }

    private User mockUser() {
        User user = new User();
        user.setId(1001L);
        user.setUsername("alice01");
        user.setPassword("secret");
        user.setNickname("Alice");
        user.setRole("normal");
        return user;
    }
}
