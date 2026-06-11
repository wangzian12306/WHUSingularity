package com.lubover.singularity.merchant.controller;

import com.lubover.singularity.merchant.dto.ApiResponse;
import com.lubover.singularity.merchant.dto.LoginResponse;
import com.lubover.singularity.merchant.dto.MerchantView;
import com.lubover.singularity.merchant.entity.Merchant;
import com.lubover.singularity.merchant.mapper.MerchantProductMapper;
import com.lubover.singularity.merchant.service.MerchantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/merchant")
public class MerchantController {

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private MerchantProductMapper merchantProductMapper;

    @PostMapping("/register")
    public ApiResponse<MerchantView> register(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        String shopName = request.get("shopName");
        String contactName = request.get("contactName");
        String contactPhone = request.get("contactPhone");
        String address = request.get("address");
        String description = request.get("description");

        Merchant merchant = merchantService.register(username, password, shopName,
                contactName, contactPhone, address, description);

        MerchantView view = convertToView(merchant);
        return ApiResponse.success(view);
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        LoginResponse response = merchantService.login(username, password);
        return ApiResponse.success(response);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        merchantService.logout(token);
        return ApiResponse.successMessage("Logout successful");
    }

    @GetMapping("/profile")
    public ApiResponse<MerchantView> getProfile() {
        MerchantView view = merchantService.getCurrentMerchantView();
        return ApiResponse.success(view);
    }

    @PutMapping("/profile")
    public ApiResponse<MerchantView> updateProfile(@RequestBody Merchant merchant) {
        Merchant updated = merchantService.updateMerchant(merchant);
        MerchantView view = convertToView(updated);
        return ApiResponse.success(view);
    }

    @GetMapping("/{id}")
    public ApiResponse<MerchantView> getMerchant(@PathVariable("id") Long id) {
        MerchantView view = merchantService.getMerchantViewById(id);
        return ApiResponse.success(view);
    }

    @GetMapping("/list")
    public ApiResponse<List<MerchantView>> listMerchants() {
        List<MerchantView> list = merchantService.listAll();
        return ApiResponse.success(list);
    }

    @PostMapping("/recharge")
    public ApiResponse<MerchantView> recharge(@RequestBody Map<String, java.math.BigDecimal> request) {
        java.math.BigDecimal amount = request.get("amount");
        if (amount == null || amount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return ApiResponse.failure("INVALID_PARAM", "充值金额必须大于0");
        }
        MerchantView view = merchantService.recharge(amount);
        return ApiResponse.success(view);
    }

    @PostMapping("/internal/add-balance")
    public ApiResponse<Void> addBalanceByProduct(@RequestBody Map<String, Object> request) {
        String productId = (String) request.get("productId");
        java.math.BigDecimal amount = request.get("amount") instanceof Number
                ? new java.math.BigDecimal(request.get("amount").toString())
                : null;
        if (productId == null || productId.isBlank() || amount == null || amount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return ApiResponse.failure("INVALID_PARAM", "productId and positive amount required");
        }
        Long merchantId = merchantProductMapper.selectMerchantIdByProductId(productId);
        if (merchantId == null) {
            return ApiResponse.failure("NOT_FOUND", "No merchant found for product: " + productId);
        }
        merchantService.addBalance(merchantId, amount);
        return ApiResponse.successMessage("Balance added");
    }

    @PostMapping("/internal/deduct-balance")
    public ApiResponse<Void> deductBalance(@RequestBody Map<String, Object> request) {
        Long merchantId = request.get("merchantId") instanceof Number
                ? ((Number) request.get("merchantId")).longValue()
                : null;
        java.math.BigDecimal amount = request.get("amount") instanceof Number
                ? new java.math.BigDecimal(request.get("amount").toString())
                : null;
        if (merchantId == null || amount == null || amount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return ApiResponse.failure("INVALID_PARAM", "merchantId and positive amount required");
        }
        try {
            merchantService.deductBalance(merchantId, amount);
            return ApiResponse.successMessage("Balance deducted");
        } catch (Exception e) {
            return ApiResponse.failure("DEDUCT_FAILED", e.getMessage());
        }
    }

    private MerchantView convertToView(Merchant merchant) {
        MerchantView view = new MerchantView();
        view.setId(merchant.getId());
        view.setUsername(merchant.getUsername());
        view.setShopName(merchant.getShopName());
        view.setContactName(merchant.getContactName());
        view.setContactPhone(merchant.getContactPhone());
        view.setAddress(merchant.getAddress());
        view.setDescription(merchant.getDescription());
        view.setStatus(merchant.getStatus());
        view.setAvatar(merchant.getAvatar());
        view.setBalance(merchant.getBalance());
        view.setCreateTime(merchant.getCreateTime());
        return view;
    }
}
