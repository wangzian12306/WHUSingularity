package com.lubover.singularity.user.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    REQ_INVALID_PARAM(HttpStatus.BAD_REQUEST, "REQ_INVALID_PARAM", "request param invalid"),
    USER_USERNAME_EXISTS(HttpStatus.CONFLICT, "USER_USERNAME_EXISTS", "username already exists"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "user not found"),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "product not found"),
    PRODUCT_NOT_BELONG_TO_MERCHANT(HttpStatus.FORBIDDEN, "PRODUCT_NOT_BELONG_TO_MERCHANT", "product not belong to this merchant"),
    INVENTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "INVENTORY_NOT_FOUND", "inventory not found"),
    INVENTORY_INSUFFICIENT(HttpStatus.BAD_REQUEST, "INVENTORY_INSUFFICIENT", "inventory insufficient"),
    INVENTORY_VERSION_MISMATCH(HttpStatus.CONFLICT, "INVENTORY_VERSION_MISMATCH", "inventory version mismatch"),
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
