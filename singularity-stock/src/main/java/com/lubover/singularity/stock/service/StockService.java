package com.lubover.singularity.stock.service;

import com.lubover.singularity.stock.entity.Stock;
import com.lubover.singularity.stock.entity.StockChangeLog;

/**
 * 库存服务接口
 */
public interface StockService {

    /**
     * 查询商品库存
     */
    Stock getStock(String productId);

    /**
     * 扣库存（异步削峰）
     * @param productId 商品ID
     * @param quantity 扣减数量
     * @param orderId 订单ID
     * @param messageId MQ消息ID，用于防重
     * @return 是否成功
     */
    boolean deductStock(String productId, Long quantity, String orderId, String messageId);

    /**
     * 还库存
     */
    boolean returnStock(String productId, Long quantity, String orderId, String messageId);

    /**
     * 获取库存变更日志
     */
    StockChangeLog getChangeLog(String messageId);

    /**
     * 初始化商品库存
     */
    void initializeStock(String productId, Long totalQuantity);
}
