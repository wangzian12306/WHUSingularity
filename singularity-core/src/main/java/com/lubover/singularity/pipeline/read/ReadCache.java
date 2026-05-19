package com.lubover.singularity.pipeline.read;

import com.lubover.singularity.pipeline.Operation;

public interface ReadCache<T> {

    CacheLookup<T> get(Operation operation);

    void put(Operation operation, T value);
}
