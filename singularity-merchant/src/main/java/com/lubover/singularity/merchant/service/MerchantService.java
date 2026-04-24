package com.lubover.singularity.merchant.service;

import com.lubover.singularity.merchant.dto.LoginResponse;
import com.lubover.singularity.merchant.dto.MerchantView;
import com.lubover.singularity.merchant.entity.Merchant;

public interface MerchantService {

    Merchant register(String username, String password, String shopName, String contactName, String contactPhone, String address, String description);

    LoginResponse login(String username, String password);

    void logout(String token);

    Merchant getMerchantById(Long id);

    Merchant getCurrentMerchant();

    MerchantView getMerchantViewById(Long id);

    MerchantView getCurrentMerchantView();

    Merchant updateMerchant(Merchant merchant);

    void updateMerchantStatus(Long id, Integer status);
}
