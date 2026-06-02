package com.lubover.singularity.api;

public interface Allocator {

    /**
     * allocate 尝试为一个 actor 分配某个 slot 中的资源
     * @param actor
     * @return
     */
    Result allocate(Actor actor);
}
