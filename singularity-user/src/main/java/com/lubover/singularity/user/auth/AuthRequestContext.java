package com.lubover.singularity.user.auth;

public class AuthRequestContext {

    private final Long userId;
    private final String role;
    private final String jti;
    private final long exp;

    public AuthRequestContext(Long userId, String role, String jti, long exp) {
        this.userId = userId;
        this.role = role;
        this.jti = jti;
        this.exp = exp;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public String getJti() {
        return jti;
    }

    public long getExp() {
        return exp;
    }
}
