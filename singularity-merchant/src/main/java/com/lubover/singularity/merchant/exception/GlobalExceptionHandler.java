package com.lubover.singularity.merchant.exception;

import com.lubover.singularity.merchant.dto.ApiResponse;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        log.info("Handling BusinessException: errorCode={}", exception.getErrorCode());
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.failure(errorCode.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiResponse<Void>> handleFeignException(FeignException exception) {
        log.error("Feign call failed: status={}, message={}", exception.status(), exception.getMessage());
        int status = exception.status();
        if (status >= 400 && status < 500) {
            ApiResponse<?> upstream = parseUpstreamResponse(exception.contentUTF8());
            if (upstream != null && upstream.getError() != null) {
                return ResponseEntity.status(status)
                        .body(ApiResponse.failure(upstream.getError().getCode(), upstream.getError().getMessage()));
            }
            return ResponseEntity.status(status)
                    .body(ApiResponse.failure(ErrorCode.REQ_INVALID_PARAM.getCode(), "upstream request rejected"));
        }
        return ResponseEntity.status(503)
                .body(ApiResponse.failure("SERVICE_UNAVAILABLE", "Product service is temporarily unavailable"));
    }

    private ApiResponse<?> parseUpstreamResponse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(body, ApiResponse.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        log.error("Unhandled exception: type={}", exception.getClass().getName(), exception);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getDefaultMessage()));
    }
}
