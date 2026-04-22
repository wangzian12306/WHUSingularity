package com.lubover.singularity.stock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 与 order 模块保持一致的 slot 配置结构。
 * 默认读取 singularity.order.slots，避免预热 key 与下游扣减 key 不一致。
 */
@Component
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
        private String id;
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
