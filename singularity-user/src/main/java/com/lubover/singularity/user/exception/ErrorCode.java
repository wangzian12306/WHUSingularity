package com.lubover.singularity.user.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    REQ_INVALID_PARAM(HttpStatus.BAD_REQUEST, "REQ_INVALID_PARAM", "request param invalid"),
    USER_USERNAME_EXISTS(HttpStatus.CONFLICT, "USER_USERNAME_EXISTS", "username already exists"),
    AUTH_BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_BAD_CREDENTIALS", "bad credentials"),
    AUTH_TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_MISSING", "token missing"),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", "token invalid"),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_EXPIRED", "token expired"),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", "forbidden"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "internal server error");

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String code, String defaultMessage) {
        this.status = status;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
