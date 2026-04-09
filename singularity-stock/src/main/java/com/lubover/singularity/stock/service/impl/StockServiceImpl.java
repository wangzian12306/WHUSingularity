package com.lubover.singularity.stock.service.impl;

import com.lubover.singularity.stock.entity.Stock;
import com.lubover.singularity.stock.entity.StockChangeLog;
import com.lubover.singularity.stock.mapper.StockChangeLogMapper;
import com.lubover.singularity.stock.mapper.StockMapper;
import com.lubover.singularity.stock.service.StockService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 库存服务实现类
 * 使用MQ异步消费库存事件，并在消费端同步完成落库与幂等处理
 */
@Service
public class StockServiceImpl implements StockService {

    private final StockMapper stockMapper;
    private final StockChangeLogMapper changeLogMapper;

    public StockServiceImpl(StockMapper stockMapper, StockChangeLogMapper changeLogMapper) {
        this.stockMapper = stockMapper;
        this.changeLogMapper = changeLogMapper;
    }

    @Override
    public Stock getStock(String productId) {
        return stockMapper.selectByProductId(productId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deductStock(String productId, Long quantity, String orderId, String messageId) {
        validateParams(productId, quantity, messageId);

        // 1. 防重检查：检查是否已处理过此消息
        StockChangeLog existLog = changeLogMapper.selectByMessageId(messageId);
        if (existLog != null) {
            // 消息已处理过，返回处理结果
            return existLog.getStatus() == 1;
        }

        // 2. 记录库存变更日志（削峰）
        StockChangeLog log = new StockChangeLog(messageId, productId, quantity, 1, orderId);
        try {
            changeLogMapper.insert(log);
        } catch (DuplicateKeyException e) {
            // 并发重复消息：按幂等逻辑返回既有处理结果
            StockChangeLog dup = changeLogMapper.selectByMessageId(messageId);
            return dup != null && dup.getStatus() == 1;
        }

        // 3. 执行扣库存操作（乐观锁冲突时最多重试3次）
        for (int i = 0; i < 3; i++) {
            Stock stock = stockMapper.selectByProductId(productId);
            if (stock == null || stock.getAvailableQuantity() < quantity) {
                // 库存不足
                changeLogMapper.updateStatus(log.getId(), 2, "库存不足");
                return false;
            }

            int result = stockMapper.updateAvailableQuantity(productId, quantity, stock.getVersion());
            if (result > 0) {
                // 更新成功，增加已占用库存
                int reservedUpdated = stockMapper.increaseReservedQuantity(productId, quantity);
                if (reservedUpdated != 1) {
                    changeLogMapper.updateStatus(log.getId(), 2, "增加预占库存失败，影响行数: " + reservedUpdated);
                    throw new IllegalStateException("增加预占库存失败，productId=" + productId + ", affectedRows=" + reservedUpdated);
                }
                // 记录日志为已处理
                changeLogMapper.updateStatus(log.getId(), 1, "成功");
                return true;
            }
        }

        // 版本不匹配，扣库存失败（库存被其他线程修改）
        changeLogMapper.updateStatus(log.getId(), 2, "版本冲突，重试后仍失败");
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean returnStock(String productId, Long quantity, String orderId, String messageId) {
        validateParams(productId, quantity, messageId);

        // 1. 防重检查
        StockChangeLog existLog = changeLogMapper.selectByMessageId(messageId);
        if (existLog != null) {
            return existLog.getStatus() == 1;
        }

        // 2. 记录库存变更日志（削峰）
        StockChangeLog log = new StockChangeLog(messageId, productId, quantity, 2, orderId);
        try {
            changeLogMapper.insert(log);
        } catch (DuplicateKeyException e) {
            StockChangeLog dup = changeLogMapper.selectByMessageId(messageId);
            return dup != null && dup.getStatus() == 1;
        }

        // 3. 执行还库存操作
        Stock stock = stockMapper.selectByProductId(productId);
        if (stock == null) {
            changeLogMapper.updateStatus(log.getId(), 2, "商品不存在");
            return false;
        }

        // 4. 原子还库存：减少占用并增加可用
        int updated = stockMapper.returnStockQuantity(productId, quantity);
        if (updated > 0) {
            changeLogMapper.updateStatus(log.getId(), 1, "成功");
            return true;
        }

        changeLogMapper.updateStatus(log.getId(), 2, "已占用库存不足，无法还库存");
        return false;
    }

    @Override
    public StockChangeLog getChangeLog(String messageId) {
        return changeLogMapper.selectByMessageId(messageId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initializeStock(String productId, Long totalQuantity) {
        Objects.requireNonNull(productId, "productId不能为空");
        Objects.requireNonNull(totalQuantity, "totalQuantity不能为空");
        if (productId.isBlank()) {
            throw new IllegalArgumentException("productId不能为空字符串");
        }
        if (totalQuantity <= 0) {
            throw new IllegalArgumentException("totalQuantity必须大于0");
        }

        Stock existing = stockMapper.selectByProductId(productId);
        if (existing != null) {
            throw new IllegalArgumentException("商品库存已存在: " + productId);
        }

        Stock stock = new Stock(productId, totalQuantity);
        stockMapper.insert(stock);
    }

    private void validateParams(String productId, Long quantity, String messageId) {
        Objects.requireNonNull(productId, "productId不能为空");
        Objects.requireNonNull(quantity, "quantity不能为空");
        Objects.requireNonNull(messageId, "messageId不能为空");

        if (productId.isBlank()) {
            throw new IllegalArgumentException("productId不能为空字符串");
        }
        if (messageId.isBlank()) {
            throw new IllegalArgumentException("messageId不能为空字符串");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity必须大于0");
        }
    }
}
