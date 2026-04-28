package com.lubover.singularity.user.auth;

public class AuthRequestContext {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE_HOLDER = new ThreadLocal<>();

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

    public static void setCurrentUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static Long getCurrentUserId() {
        return USER_ID_HOLDER.get();
    }

    public static void setCurrentRole(String role) {
        ROLE_HOLDER.set(role);
    }

    public static String getCurrentRole() {
        return ROLE_HOLDER.get();
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
        ROLE_HOLDER.remove();
    }
}
