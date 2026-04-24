package com.lubover.singularity.merchant.controller;

import com.lubover.singularity.merchant.dto.ApiResponse;
import com.lubover.singularity.merchant.dto.LoginResponse;
import com.lubover.singularity.merchant.dto.MerchantView;
import com.lubover.singularity.merchant.entity.Merchant;
import com.lubover.singularity.merchant.service.MerchantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/merchant")
public class MerchantController {

    @Autowired
    private MerchantService merchantService;

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
    public ApiResponse<MerchantView> getMerchant(@PathVariable Long id) {
        MerchantView view = merchantService.getMerchantViewById(id);
        return ApiResponse.success(view);
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
        view.setCreateTime(merchant.getCreateTime());
        return view;
    }
}
