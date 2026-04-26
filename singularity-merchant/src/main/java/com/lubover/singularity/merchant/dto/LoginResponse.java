package com.lubover.singularity.merchant.dto;

public class LoginResponse {

    private String tokenType;
    private String accessToken;
    private long expiresIn;
    private MerchantView merchant;

    public LoginResponse() {
    }

    public LoginResponse(String tokenType, String accessToken, long expiresIn, MerchantView merchant) {
        this.tokenType = tokenType;
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
        this.merchant = merchant;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public MerchantView getMerchant() {
        return merchant;
    }

    public void setMerchant(MerchantView merchant) {
        this.merchant = merchant;
    }
}
