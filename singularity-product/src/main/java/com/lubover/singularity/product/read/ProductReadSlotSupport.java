package com.lubover.singularity.product.read;

import com.lubover.singularity.pipeline.ExecutionContext;
import com.lubover.singularity.pipeline.read.ReadMeta;
import com.lubover.singularity.pipeline.read.ReadSlot;

public final class ProductReadSlotSupport {

    public static final String REDIS_KEY_PREFIX = "redisKeyPrefix";
    public static final String LEGACY_PREFIX = "product:";
    public static final String SHARD_PREFIX_0 = "product:s0:";
    public static final String SHARD_PREFIX_1 = "product:s1:";
    public static final String SHARD_PREFIX_2 = "product:s2:";
    public static final String SHARD_PREFIX_3 = "product:s3:";

    private ProductReadSlotSupport() {
    }

    public static String redisKeyPrefix(ExecutionContext<?> context) {
        if (context == null) {
            return LEGACY_PREFIX;
        }
        Object slotValue = context.getValue(ReadMeta.READ_SLOT);
        if (!(slotValue instanceof ReadSlot slot)) {
            return LEGACY_PREFIX;
        }
        Object prefix = slot.getMetadata().get(REDIS_KEY_PREFIX);
        if (!(prefix instanceof String value) || value.isBlank()) {
            return LEGACY_PREFIX;
        }
        return value;
    }
}
