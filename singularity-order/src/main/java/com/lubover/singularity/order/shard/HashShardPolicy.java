package com.lubover.singularity.order.shard;

import com.lubover.singularity.api.Actor;
import com.lubover.singularity.api.ShardPolicy;
import com.lubover.singularity.api.Slot;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 基于 actor ID（用户 ID）简单哈希的分槽策略
 * 对 actor ID 取 hashCode 后取绝对值，再对槽数取模，保证同一用户始终路由到同一 slot
 */
@Component
public class HashShardPolicy implements ShardPolicy {

    @Override
    public Optional<Slot> selectSlot(Actor actor, List<Slot> slotList) {
        if (slotList.isEmpty()) {
            return Optional.empty();
        }
        int index = Math.abs(actor.getId().hashCode()) % slotList.size();
        return Optional.of(slotList.get(index));
    }
}
