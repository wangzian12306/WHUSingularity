package com.lubover.singularity.pipeline.interceptor;

import com.lubover.singularity.pipeline.ExecutionContext;
import com.lubover.singularity.pipeline.PipelineInterceptor;
import com.lubover.singularity.pipeline.read.ReadMeta;
import com.lubover.singularity.pipeline.read.ReadRegistry;
import com.lubover.singularity.pipeline.read.ReadShardPolicy;
import com.lubover.singularity.pipeline.read.ReadSlot;

public class ReadRoutingInterceptor<T> implements PipelineInterceptor<T> {

    private final ReadRegistry registry;
    private final ReadShardPolicy shardPolicy;

    public ReadRoutingInterceptor(ReadRegistry registry, ReadShardPolicy shardPolicy) {
        this.registry = registry;
        this.shardPolicy = shardPolicy;
    }

    @Override
    public void handle(ExecutionContext<T> context) {
        ReadSlot slot = shardPolicy.select(context.getOperation(), registry.availableSlots());
        context.putMeta(ReadMeta.READ_SLOT_ID, slot.getId());
        context.next();
    }
}
