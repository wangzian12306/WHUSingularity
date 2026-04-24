package com.lubover.singularity.merchant.dto;

public class LoginResponse {

    private String accessToken;
    private MerchantView merchant;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public MerchantView getMerchant() {
        return merchant;
    }

    public void setMerchant(MerchantView merchant) {
        this.merchant = merchant;
    }
}
