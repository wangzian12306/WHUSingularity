package com.lubover.singularity.api;

/**
 * Context 是在整个 allocate 过程中，所有处理器函数中传递的共有上下文
 * 其可以用来传递一定的上下文信息
 */
public interface Context {

    /**
     * getValue 获取上下文中的某个键值对，可能为 null
     * @param key
     * @return
     */
    Object getValue(String key);

    /**
     * withValue 在上下文中存储一个新的键值对，如果已经存在，则替换
     * @param key
     * @param obj
     */
    void withValue(String key, Object obj);
    
    /**
     * getCurrActor 获取当前正在处理的 actor
     */
    Actor getCurrActor();

    /**
     * getCurrSlot 获取当前正在处理的 slot
     * 在进入到链式执行链后，slot 决不会为 null，否则会在 selectSlot
     * 之后就提前终止执行，而不会进入到链式执行链中
     */
    Slot getCurrSlot();


    /**
     * getResult 获取在处理流中产生的结果
     * result 应该总由 context 进行维护，用户不应该直接创建 result 对象
     * @return
     */
    Result getResult();
    
    /**
     * setResult 设置处理流中的结果
     * @param result
     */
    void setResult(Result result);
    
    /**
     * next 继续处理下一个处理器函数
     * 其主要由 interceptor 来调用，用户一般不需要直接调用
     */
    void next();
}
