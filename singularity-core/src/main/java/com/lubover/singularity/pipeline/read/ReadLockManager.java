package com.lubover.singularity.pipeline.read;

import com.lubover.singularity.pipeline.ExecutionContext;
import com.lubover.singularity.pipeline.Operation;

public interface ReadLockManager {

    boolean tryLock(Operation operation, String token);

    default boolean tryLock(ExecutionContext<?> context, String token) {
        return tryLock(context.getOperation(), token);
    }

    void unlock(Operation operation, String token);

    default void unlock(ExecutionContext<?> context, String token) {
        unlock(context.getOperation(), token);
    }
}
