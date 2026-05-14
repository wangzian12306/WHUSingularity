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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Component
public class SlotRegistry implements Registry {

    private static final Logger log = LoggerFactory.getLogger(SlotRegistry.class);

    private final CopyOnWriteArrayList<StockSlot> slotList;

    private final Cache<String, Boolean> emptyCache = Caffeine.newBuilder().build();

    public SlotRegistry(SlotProperties props) {
        List<StockSlot> initial = props.getSlots().stream()
                .map(c -> new StockSlot(c.getId(), c.getRedisKey(), c.getProductId()))
                .collect(Collectors.toList());
        this.slotList = new CopyOnWriteArrayList<>(initial);

        log.info("SlotRegistry initialized with {} slots: {}",
                slotList.size(),
                slotList.stream().map(StockSlot::getId).collect(Collectors.joining(", ")));
    }

    @Override
    public List<Slot> getSlotList() {
        return slotList.stream()
                .filter(s -> !Boolean.TRUE.equals(emptyCache.getIfPresent(s.getId())))
                .collect(Collectors.toUnmodifiableList());
    }

    public void markEmpty(String slotId) {
        emptyCache.put(slotId, Boolean.TRUE);
        log.info("slot [{}] marked as empty, excluded from future allocations", slotId);
    }

    public void clearEmpty(String slotId) {
        emptyCache.invalidate(slotId);
        log.info("slot [{}] cleared empty mark", slotId);
    }

    public String getRedisStockKey(String slotId) {
        return slotList.stream()
                .filter(s -> s.getId().equals(slotId))
                .map(StockSlot::getRedisStockKey)
                .findFirst()
                .orElse("stock:" + slotId);
    }

    public String getProductId(String slotId) {
        return slotList.stream()
                .filter(s -> s.getId().equals(slotId))
                .map(StockSlot::getProductId)
                .findFirst()
                .orElse(null);
    }

    public synchronized void addSlot(String slotId, String redisKey, String productId) {
        boolean exists = slotList.stream().anyMatch(s -> s.getId().equals(slotId));
        if (!exists) {
            slotList.add(new StockSlot(slotId, redisKey, productId));
            emptyCache.invalidate(slotId);
            log.info("slot [{}] added dynamically: redisKey={}, productId={}", slotId, redisKey, productId);
        } else {
            emptyCache.invalidate(slotId);
            log.info("slot [{}] already exists, cleared empty mark", slotId);
        }
    }

    public List<StockSlot> getAllSlots() {
        return new ArrayList<>(slotList);
    }

    public Boolean getEmptyStatus(String slotId) {
        return emptyCache.getIfPresent(slotId);
    }
}
