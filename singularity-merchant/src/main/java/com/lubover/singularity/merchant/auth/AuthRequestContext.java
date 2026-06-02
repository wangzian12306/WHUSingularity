package com.lubover.singularity.merchant.auth;

public class AuthRequestContext {
    private static final ThreadLocal<Long> MERCHANT_ID_HOLDER = new ThreadLocal<>();

    public static void setMerchantId(Long merchantId) {
        MERCHANT_ID_HOLDER.set(merchantId);
    }

    public static Long getMerchantId() {
        return MERCHANT_ID_HOLDER.get();
    }

    public static void clear() {
        MERCHANT_ID_HOLDER.remove();
    }
}
