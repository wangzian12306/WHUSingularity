package com.lubover.singularity.api;

import java.util.Map;

public interface Actor {

    /**
     * 获取 actor ID，用于在分配时进行记录
     * @return
     */
    String getId();

    /**
     * getMetadata 获取用于分片等操作的元数据
     * @return
     */
    Map<String, ?> getMetadata();
}
