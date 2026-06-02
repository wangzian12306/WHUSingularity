package com.lubover.singularity.api;

import java.util.List;
import java.util.Optional;

/**
 * ShardPolicy 用于决定 actor 到 slot 的分配机制
 * 通常来说，这代表着一种分配策略
 */
public interface ShardPolicy {

    /**
     * selectSlot 根据 actor 和 slotList 来选择一个 slot 进行分配
     * @param actor
     * @param slotList
     * @return
     */
    Optional<Slot> selectSlot(Actor actor, List<Slot> slotList);
}
