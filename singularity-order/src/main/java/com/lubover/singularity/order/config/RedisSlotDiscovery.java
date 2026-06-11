package com.lubover.singularity.order.config;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.lubover.singularity.order.registry.SlotRegistry;

@Component
public class RedisSlotDiscovery implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RedisSlotDiscovery.class);

    private final StringRedisTemplate redisTemplate;
    private final SlotRegistry slotRegistry;

    public RedisSlotDiscovery(StringRedisTemplate redisTemplate, SlotRegistry slotRegistry) {
        this.redisTemplate = redisTemplate;
        this.slotRegistry = slotRegistry;
    }

    @Override
    public void run(String... args) {
        try {
            Set<String> keys = redisTemplate.keys("stock:*");
            if (keys == null || keys.isEmpty()) {
                log.info("No stock keys found in Redis, skipping auto-discovery");
                return;
            }

            int registered = 0;
            for (String key : keys) {
                String slotId = key.substring("stock:".length());

                String productId = resolveProductId(slotId);
                String redisKey = key;

                Map<Object, Object> meta = redisTemplate.opsForHash().entries("slot-meta:" + slotId);
                if (meta != null && !meta.isEmpty()) {
                    if (meta.get("productId") != null) {
                        productId = String.valueOf(meta.get("productId"));
                    }
                    if (meta.get("redisKey") != null) {
                        redisKey = String.valueOf(meta.get("redisKey"));
                    }
                }

                if (!slotRegistry.hasSlot(slotId)) {
                    slotRegistry.addSlot(slotId, redisKey, productId);
                    registered++;
                    log.info("Auto-discovered slot from Redis: slotId={}, redisKey={}, productId={}", slotId, redisKey, productId);
                }
            }
            log.info("Redis slot auto-discovery completed: {} new slots registered, {} total slots",
                    registered, slotRegistry.getAllSlots().size());
        } catch (Exception e) {
            log.warn("Redis slot auto-discovery failed (non-fatal): {}", e.getMessage());
        }
    }

    private String resolveProductId(String slotId) {
        if (slotId.startsWith("slot-")) {
            return slotId.substring("slot-".length());
        }
        return slotId;
    }
}
