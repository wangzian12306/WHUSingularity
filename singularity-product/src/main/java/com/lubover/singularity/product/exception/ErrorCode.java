package com.lubover.singularity.product.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    REQ_INVALID_PARAM(HttpStatus.BAD_REQUEST, "REQ_INVALID_PARAM", "request param invalid"),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "product not found"),
    PRODUCT_ALREADY_EXISTS(HttpStatus.CONFLICT, "PRODUCT_ALREADY_EXISTS", "product already exists"),
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
