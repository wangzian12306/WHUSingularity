package com.lubover.singularity.pipeline;

import java.util.Map;

public interface ExecutionContext<T> {

    Operation getOperation();

    Object getValue(String key);

    void putValue(String key, Object value);

    Map<String, Object> getValues();

    void putMeta(String key, Object value);

    Map<String, Object> getMeta();

    ExecutionResult<T> getResult();

    void setResult(ExecutionResult<T> result);

    void next();
}
