package com.lubover.singularity.pipeline.read;

import com.lubover.singularity.pipeline.Operation;

import java.util.List;

public interface ReadShardPolicy {

    ReadSlot select(Operation operation, List<ReadSlot> slots);
}
