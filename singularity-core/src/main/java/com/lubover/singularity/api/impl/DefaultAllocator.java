package com.lubover.singularity.api.impl;

import com.lubover.singularity.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class DefaultAllocator implements Allocator {

    private final Registry registry;
    private final ShardPolicy shardPolicy;
    private final List<Interceptor> interceptors;
    private final Interceptor handler;

    public DefaultAllocator(Registry registry, ShardPolicy shardPolicy,
                            List<Interceptor> interceptors, Interceptor handler) {
        this.registry = registry;
        this.shardPolicy = shardPolicy;
        this.interceptors = interceptors != null ? interceptors : Collections.emptyList();
        this.handler = handler;
    }

    @Override
    public Result allocate(Actor actor) {
        List<Slot> slots = registry.getSlotList();
        if (slots == null || slots.isEmpty()) {
            return new Result(false, "no available slots from registry");
        }

        Optional<Slot> selected = shardPolicy.selectSlot(actor, slots);
        if (selected.isEmpty()) {
            return new Result(false, "no matching slot for actor: " + actor.getId());
        }

        List<Interceptor> chain = new ArrayList<>(interceptors);
        chain.add(handler);

        DefaultContext context = new DefaultContext(actor, selected.get(), chain);
        context.next();

        Result result = context.getResult();

        return result != null ? result : new Result(false, "no result produced by handler chain");
    }
}
