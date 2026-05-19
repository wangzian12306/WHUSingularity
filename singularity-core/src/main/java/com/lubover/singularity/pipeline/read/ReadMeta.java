package com.lubover.singularity.pipeline.read;

public final class ReadMeta {

    public static final String SOURCE = "source";
    public static final String CACHE_STATE = "cacheState";
    public static final String LOCK_WAIT_COUNT = "lockWaitCount";
    public static final String READ_SLOT_ID = "readSlotId";
    public static final String DEGRADED = "degraded";

    public static final String SOURCE_CACHE = "CACHE";
    public static final String SOURCE_DB = "DB";
    public static final String SOURCE_FALLBACK = "FALLBACK";

    private ReadMeta() {
    }
}
