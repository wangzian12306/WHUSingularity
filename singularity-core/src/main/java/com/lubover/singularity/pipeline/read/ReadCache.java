package com.lubover.singularity.pipeline.read;

import com.lubover.singularity.pipeline.ExecutionContext;
import com.lubover.singularity.pipeline.Operation;

public interface ReadCache<T> {

    CacheLookup<T> get(Operation operation);

    default CacheLookup<T> get(ExecutionContext<T> context) {
        return get(context.getOperation());
    }

    void put(Operation operation, T value);

    default void put(ExecutionContext<T> context, T value) {
        put(context.getOperation(), value);
    }
}
