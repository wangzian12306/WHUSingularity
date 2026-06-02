package com.lubover.singularity.api;

import java.util.List;

/**
 * Registry 用于动态地获取 slot 列表，进一步用于进行 slot 分片
 */
public interface Registry {

    /**
     * getSlotList 在必要的时刻获取 slot 列表，可用于负载均衡或者分片
     * @return
     */
    List<Slot> getSlotList();
}
