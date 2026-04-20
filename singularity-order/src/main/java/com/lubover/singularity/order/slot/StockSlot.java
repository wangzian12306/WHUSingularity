package com.lubover.singularity.order.slot;

import com.lubover.singularity.api.Slot;

import java.util.Map;

/**
 * 代表一个 Redis 库存桶（bucket）的 Slot 实现
 */
public class StockSlot implements Slot {

    private final String id;
    private final String redisStockKey;

    public StockSlot(String id, String redisStockKey) {
        this.id = id;
        this.redisStockKey = redisStockKey;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getRedisStockKey() {
        return redisStockKey;
    }

    @Override
    public Map<String, ?> getMetadata() {
        return Map.of("redisStockKey", redisStockKey);
    }
}
