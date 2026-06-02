package com.lubover.singularity.stock.entity;

import java.time.LocalDateTime;

/**
 * 库存变更日志表
 * 记录所有库存变更事件，用于实现削峰填谷的MQ异步落库
 * 消费MQ消息后，先写入本表，后续业务处理时进行幂等性校验
 */
public class StockChangeLog {

    private Long id;
    
    /** 消息ID（用于防重） */
    private String messageId;
    
    /** 商品ID */
    private String productId;
    
    /** 变更数量 */
    private Long changeQuantity;
    
    /** 变更类型：1-扣库存, 2-还库存, 3-销售 */
    private Integer changeType;
    
    /** 关联的订单ID */
    private String orderId;
    
    /** 处理状态：0-待处理, 1-已处理, 2-处理失败 */
    private Integer status;
    
    /** 处理结果说明 */
    private String remark;
    
    /** 创建时间 */
    private LocalDateTime createTime;
    
    /** 更新时间 */
    private LocalDateTime updateTime;

    public StockChangeLog() {
    }

    public StockChangeLog(String messageId, String productId, Long changeQuantity, 
                          Integer changeType, String orderId) {
        this.messageId = messageId;
        this.productId = productId;
        this.changeQuantity = changeQuantity;
        this.changeType = changeType;
        this.orderId = orderId;
        this.status = 0;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public Long getChangeQuantity() {
        return changeQuantity;
    }

    public void setChangeQuantity(Long changeQuantity) {
        this.changeQuantity = changeQuantity;
    }

    public Integer getChangeType() {
        return changeType;
    }

    public void setChangeType(Integer changeType) {
        this.changeType = changeType;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "StockChangeLog{" +
                "id=" + id +
                ", messageId='" + messageId + '\'' +
                ", productId='" + productId + '\'' +
                ", changeQuantity=" + changeQuantity +
                ", changeType=" + changeType +
                ", orderId='" + orderId + '\'' +
                ", status=" + status +
                ", remark='" + remark + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
