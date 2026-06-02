package com.lubover.singularity.api;

import java.util.Map;

public interface Slot {

    /**
     * 获取 slot ID，用于在分配时进行记录
     * @return
     */
    String getId();

    /**
     * getMetadata 返回可能需要的元数据
     * 这可能是用于实现分片策略等操作
     * @return 元数据的 map，考虑到不可写性，返回类型为 ?
     */
    Map<String, ?> getMetadata();
}
