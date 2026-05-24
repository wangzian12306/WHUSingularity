package com.lubover.singularity.pipeline.read;

import com.lubover.singularity.pipeline.Operation;

public interface ReadLockManager {

    boolean tryLock(Operation operation, String token);

    void unlock(Operation operation, String token);
}
