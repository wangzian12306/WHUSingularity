package com.lubover.singularity.stock.service;

import com.lubover.singularity.stock.dto.SlotPreheatResponse;

public interface SlotWarmupService {

    /**
     * 预热 Redis slot 库存。
     *
     * @param slotId slot 标识
     * @param quantity 初始库存数量
     * @param overwrite 为 true 时覆盖已有值；为 false 时仅在 key 不存在时写入
     * @return 预热结果
     */
    SlotPreheatResponse warmupSlot(String slotId, String redisKey, Long quantity, boolean overwrite);

    /**
     * 兼容旧的 preheat 命名，内部统一委托给 warmupSlot。
     *
     * @deprecated 请使用 {@link #warmupSlot(String, String, Long, boolean)}
     */
    @Deprecated
    default SlotPreheatResponse preheatSlot(String slotId, Long quantity, boolean overwrite) {
        return warmupSlot(slotId, null, quantity, overwrite);
    }
}
