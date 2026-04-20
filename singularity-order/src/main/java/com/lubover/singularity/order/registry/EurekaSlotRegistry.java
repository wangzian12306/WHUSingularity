package com.lubover.singularity.order.registry;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lubover.singularity.api.Registry;
import com.lubover.singularity.api.Slot;
import com.lubover.singularity.order.config.SlotProperties;
import com.lubover.singularity.order.slot.StockSlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于 Redis + Caffeine 的 Slot 注册表
 *
 * <p>
 * 初始化时，从 Spring Cloud Config Server（由 Eureka 注册）下发的配置中读取
 * 所有 slot（即 Redis 库存桶）的 key 列表，之后不再重新拉取。
 *
 * <p>
 * 当某个 slot 的库存首次被扣减至 0 时，由 {@link #markEmpty(String)} 将其写入
 * 本地 Caffeine 缓存，后续 {@link #getSlotList()} 将不再返回该 slot，
 * 避免无效的抢占请求继续击穿该桶。
 */
@Component
public class EurekaSlotRegistry implements Registry {

    private static final Logger log = LoggerFactory.getLogger(EurekaSlotRegistry.class);

    private final List<StockSlot> allSlots;

    /**
     * 本地 empty 标记缓存：key = slotId，value = true 表示已售罄
     * 不设置过期，售罄后无需自动恢复（由运营手动补库存并重启服务）
     */
    private final Cache<String, Boolean> emptyCache = Caffeine.newBuilder().build();

    public EurekaSlotRegistry(SlotProperties props) {
        this.allSlots = props.getSlots().stream()
                .map(c -> new StockSlot(c.getId(), c.getRedisKey()))
                .collect(Collectors.toList());
        log.info("EurekaSlotRegistry initialized with {} slots: {}",
                allSlots.size(),
                allSlots.stream().map(StockSlot::getId).collect(Collectors.joining(", ")));
    }

    /**
     * 返回当前未售罄的 slot 列表
     */
    @Override
    public List<Slot> getSlotList() {
        return allSlots.stream()
                .filter(s -> !Boolean.TRUE.equals(emptyCache.getIfPresent(s.getId())))
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * 将指定 slot 标记为已售罄，后续不再出现在 slot 列表中
     */
    public void markEmpty(String slotId) {
        emptyCache.put(slotId, Boolean.TRUE);
        log.info("slot [{}] marked as empty, excluded from future allocations", slotId);
    }

    /**
     * 通过 slotId 查找对应的 Redis 库存 key
     */
    public String getRedisStockKey(String slotId) {
        return allSlots.stream()
                .filter(s -> s.getId().equals(slotId))
                .map(StockSlot::getRedisStockKey)
                .findFirst()
                .orElse("stock:" + slotId);
    }
}
