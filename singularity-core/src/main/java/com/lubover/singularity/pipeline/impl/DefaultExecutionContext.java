package com.lubover.singularity.pipeline.impl;

import com.lubover.singularity.pipeline.ExecutionContext;
import com.lubover.singularity.pipeline.ExecutionResult;
import com.lubover.singularity.pipeline.Operation;
import com.lubover.singularity.pipeline.PipelineHandler;
import com.lubover.singularity.pipeline.PipelineInterceptor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultExecutionContext<T> implements ExecutionContext<T> {

    private final Operation operation;
    private final List<PipelineInterceptor<T>> chain;
    private final PipelineHandler<T> handler;
    private final Map<String, Object> values = new HashMap<>();
    private final Map<String, Object> meta = new HashMap<>();
    private int index = -1;
    private ExecutionResult<T> result;

    public DefaultExecutionContext(
            Operation operation,
            List<PipelineInterceptor<T>> chain,
            PipelineHandler<T> handler) {
        this.operation = operation;
        this.chain = chain;
        this.handler = handler;
    }

    @Override
    public Operation getOperation() {
        return operation;
    }

    @Override
    public Object getValue(String key) {
        return values.get(key);
    }

    @Override
    public void putValue(String key, Object value) {
        values.put(key, value);
    }

    @Override
    public Map<String, Object> getValues() {
        return Collections.unmodifiableMap(values);
    }

    @Override
    public void putMeta(String key, Object value) {
        meta.put(key, value);
    }

    @Override
    public Map<String, Object> getMeta() {
        return Collections.unmodifiableMap(meta);
    }

    @Override
    public ExecutionResult<T> getResult() {
        return result;
    }

    @Override
    public void setResult(ExecutionResult<T> result) {
        this.result = result;
    }

    @Override
    public void next() {
        index++;
        if (index < chain.size()) {
            chain.get(index).handle(this);
            return;
        }
        handler.handle(this);
    }
}
