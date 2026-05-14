package com.lubover.singularity.product.read;

import com.lubover.singularity.pipeline.read.ReadMeta;

public final class ProductReadMeta {

    public static final String SOURCE = ReadMeta.SOURCE;
    public static final String SOURCE_LOCAL_OR_REDIS = ReadMeta.SOURCE_CACHE;
    public static final String SOURCE_DB = ReadMeta.SOURCE_DB;
    public static final String LOCK_WAIT_COUNT = ReadMeta.LOCK_WAIT_COUNT;

    private ProductReadMeta() {
    }
}
