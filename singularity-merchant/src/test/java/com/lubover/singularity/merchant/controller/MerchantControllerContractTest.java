package com.lubover.singularity.merchant.controller;

import com.lubover.singularity.merchant.auth.JwtProvider;
import com.lubover.singularity.merchant.auth.TokenBlacklistService;
import com.lubover.singularity.merchant.dto.LoginResponse;
import com.lubover.singularity.merchant.dto.MerchantView;
import com.lubover.singularity.merchant.entity.Merchant;
import com.lubover.singularity.merchant.exception.BusinessException;
import com.lubover.singularity.merchant.exception.ErrorCode;
import com.lubover.singularity.merchant.exception.GlobalExceptionHandler;
import com.lubover.singularity.merchant.service.MerchantService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MerchantControllerContractTest {

    private MockMvc mockMvc;

    @Mock
    private MerchantService merchantService;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @InjectMocks
    private MerchantController merchantController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(merchantController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void registerShouldReturn200AndNotExposePassword() throws Exception {
        Merchant merchant = mockMerchant();
        when(merchantService.register(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(merchant);

        mockMvc.perform(post("/api/merchant/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "shop01",
                              "password": "P@ssw0rd!",
                              "shopName": "Test Shop",
                              "contactName": "John Doe",
                              "contactPhone": "13800138000",
                              "address": "Test Address",
                              "description": "Test Description"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("shop01"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void loginShouldReturnContractShape() throws Exception {
        Merchant merchant = mockMerchant();
        LoginResponse loginResponse = new LoginResponse("Bearer", "jwt-token-123", 86400L, mockMerchantView());
        when(merchantService.login(anyString(), anyString())).thenReturn(loginResponse);

        mockMvc.perform(post("/api/merchant/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "shop01",
                              "password": "P@ssw0rd!"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("jwt-token-123"))
                .andExpect(jsonPath("$.data.accessToken", not(isEmptyOrNullString())))
                .andExpect(jsonPath("$.data.expiresIn").value(86400))
                .andExpect(jsonPath("$.data.merchant.username").value("shop01"))
                .andExpect(jsonPath("$.data.merchant.password").doesNotExist());
    }

    @Test
    void loginBadCredentialsShouldReturn401AndErrorCode() throws Exception {
        when(merchantService.login(anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.AUTH_BAD_CREDENTIALS));

        mockMvc.perform(post("/api/merchant/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "shop01",
                              "password": "badpass"
                            }
                            """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_BAD_CREDENTIALS"));
    }

    @Test
    void duplicateRegisterShouldReturn409AndErrorCode() throws Exception {
        when(merchantService.register(anyString(), anyString(), nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class)))
                .thenThrow(new BusinessException(ErrorCode.MERCHANT_USERNAME_EXISTS));

        mockMvc.perform(post("/api/merchant/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "shop01",
                              "password": "P@ssw0rd!",
                              "shopName": "Test Shop"
                            }
                            """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MERCHANT_USERNAME_EXISTS"));
    }

    @Test
    void registerInvalidParamShouldReturn400AndErrorCode() throws Exception {
        when(merchantService.register(anyString(), anyString(), nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class)))
                .thenThrow(new BusinessException(ErrorCode.REQ_INVALID_PARAM));

        mockMvc.perform(post("/api/merchant/register")
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
        when(merchantService.login(anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.REQ_INVALID_PARAM));

        mockMvc.perform(post("/api/merchant/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "shop01",
                              "password": ""
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("REQ_INVALID_PARAM"));
    }

    private Merchant mockMerchant() {
        Merchant merchant = new Merchant();
        merchant.setId(1001L);
        merchant.setUsername("shop01");
        merchant.setPassword("secret");
        merchant.setShopName("Test Shop");
        merchant.setContactName("John Doe");
        merchant.setContactPhone("13800138000");
        merchant.setAddress("Test Address");
        merchant.setDescription("Test Description");
        merchant.setStatus(1);
        return merchant;
    }

    private MerchantView mockMerchantView() {
        MerchantView view = new MerchantView();
        view.setId(1001L);
        view.setUsername("shop01");
        view.setShopName("Test Shop");
        view.setContactName("John Doe");
        view.setContactPhone("13800138000");
        view.setAddress("Test Address");
        view.setDescription("Test Description");
        view.setStatus(1);
        return view;
    }
}
