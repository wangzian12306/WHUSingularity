package com.lubover.singularity.pipeline.read;

import com.lubover.singularity.pipeline.Operation;

import java.util.List;

public class HashReadShardPolicy implements ReadShardPolicy {

    @Override
    public ReadSlot select(Operation operation, List<ReadSlot> slots) {
        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }
        if (slots == null || slots.isEmpty()) {
            throw new IllegalArgumentException("read slots must not be empty");
        }
        int index = Math.floorMod(operation.getKey().hashCode(), slots.size());
        return slots.get(index);
    }
}
