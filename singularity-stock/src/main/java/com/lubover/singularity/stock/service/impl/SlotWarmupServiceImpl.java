package com.lubover.singularity.stock.service.impl;

import com.lubover.singularity.stock.dto.SlotPreheatResponse;
import com.lubover.singularity.stock.config.SlotProperties;
import com.lubover.singularity.stock.service.SlotWarmupService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class SlotWarmupServiceImpl implements SlotWarmupService {

    private static final String STOCK_KEY_PREFIX = "stock:";

    private final StringRedisTemplate redisTemplate;
    private final SlotProperties slotProperties;

    public SlotWarmupServiceImpl(StringRedisTemplate redisTemplate, SlotProperties slotProperties) {
        this.redisTemplate = redisTemplate;
        this.slotProperties = slotProperties;
    }

    @Override
    public SlotPreheatResponse warmupSlot(String slotId, String redisKey, Long quantity, boolean overwrite) {
        validate(slotId, quantity);

        String actualRedisKey = resolveRedisKey(slotId, redisKey);
        String value = String.valueOf(quantity);

        if (overwrite) {
            redisTemplate.opsForValue().set(actualRedisKey, value);
            return new SlotPreheatResponse(actualRedisKey, true, value, "slot已覆盖预热");
        }

        Boolean written = redisTemplate.opsForValue().setIfAbsent(actualRedisKey, value);
        if (Boolean.TRUE.equals(written)) {
            return new SlotPreheatResponse(actualRedisKey, true, value, "slot预热成功");
        }

        String currentValue = redisTemplate.opsForValue().get(actualRedisKey);
        return new SlotPreheatResponse(actualRedisKey, false, currentValue, "slot已存在，未覆盖");
    }

    private String resolveRedisKey(String slotId, String redisKey) {
        if (redisKey != null && !redisKey.isBlank()) {
            return redisKey;
        }

        return slotProperties.getSlots().stream()
                .filter(Objects::nonNull)
                .filter(slot -> slotId.equals(slot.getId()))
                .map(SlotProperties.SlotConfig::getRedisKey)
                .filter(key -> key != null && !key.isBlank())
                .findFirst()
                .orElse(STOCK_KEY_PREFIX + slotId);
    }

    private void validate(String slotId, Long quantity) {
        if (slotId == null || slotId.isBlank()) {
            throw new IllegalArgumentException("slotId不能为空");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("quantity必须大于0");
        }
    }
}
