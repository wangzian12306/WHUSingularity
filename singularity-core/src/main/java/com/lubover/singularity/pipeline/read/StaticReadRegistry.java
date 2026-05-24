package com.lubover.singularity.pipeline.read;

import java.util.List;

public class StaticReadRegistry implements ReadRegistry {

    private final List<ReadSlot> slots;

    public StaticReadRegistry(List<ReadSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            throw new IllegalArgumentException("read slots must not be empty");
        }
        this.slots = List.copyOf(slots);
    }

    @Override
    public List<ReadSlot> availableSlots() {
        return slots;
    }
}
