package com.lubover.singularity.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 从配置中心（由 Eureka 注册的 Config Server 提供）读取 slot 配置
 * 每个 slot 对应一个 Redis 库存桶
 */
@ConfigurationProperties(prefix = "singularity.order")
public class SlotProperties {

    private List<SlotConfig> slots = new ArrayList<>();

    public List<SlotConfig> getSlots() {
        return slots;
    }

    public void setSlots(List<SlotConfig> slots) {
        this.slots = slots;
    }

    public static class SlotConfig {
        /** slot 唯一标识 */
        private String id;
        /** 对应的 Redis key，例如 stock:bucket-1 */
        private String redisKey;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getRedisKey() {
            return redisKey;
        }

        public void setRedisKey(String redisKey) {
            this.redisKey = redisKey;
        }
    }
}
