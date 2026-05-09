package com.lubover.singularity.product.read;

public final class ProductReadMeta {

    public static final String SOURCE = "source";
    public static final String SOURCE_LOCAL_OR_REDIS = "CACHE";
    public static final String SOURCE_DB = "DB";
    public static final String LOCK_WAIT_COUNT = "lockWaitCount";

    private ProductReadMeta() {
    }
}
