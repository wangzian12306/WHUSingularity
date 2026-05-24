package com.lubover.singularity.pipeline;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ExecutionResult<T> {

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;
    private final Map<String, Object> meta;

    public ExecutionResult(boolean success, String code, String message, T data, Map<String, Object> meta) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
        this.meta = meta == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(meta));
    }

    public static <T> ExecutionResult<T> success(T data) {
        return new ExecutionResult<>(true, "OK", "ok", data, Collections.emptyMap());
    }

    public static <T> ExecutionResult<T> failure(String code, String message) {
        return new ExecutionResult<>(false, code, message, null, Collections.emptyMap());
    }

    public ExecutionResult<T> withMeta(Map<String, Object> meta) {
        return new ExecutionResult<>(success, code, message, data, meta);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }
}
