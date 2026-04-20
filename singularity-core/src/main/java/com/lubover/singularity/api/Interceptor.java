package com.lubover.singularity.api;

/**
 * Interceptor 是在 allocate 过程中可以予以截断插入逻辑的拦截器
 */
@FunctionalInterface
public interface Interceptor {

    /**
     * handle 方法接受一个 Context 对象，调用 context.next() 将控制权交给下一个 Interceptor 或最终的
     * Handler，
     * 之后可以通过 context.getResult() 获取 Handler 的执行结果，并进行相应的处理（如日志记录、结果转换等）
     */
    void handle(Context context);
}
