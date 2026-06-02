package com.lubover.singularity.user.auth;

public class TokenValidationException extends RuntimeException {

    public enum Reason {
        INVALID,
        EXPIRED
    }

    private final Reason reason;

    private TokenValidationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public static TokenValidationException invalid(String message) {
        return new TokenValidationException(Reason.INVALID, message);
    }

    public static TokenValidationException expired(String message) {
        return new TokenValidationException(Reason.EXPIRED, message);
    }

    public Reason getReason() {
        return reason;
    }
}
