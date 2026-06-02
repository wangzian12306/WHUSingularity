package com.lubover.singularity.order.entity;

import java.time.LocalDateTime;

/**
 * 订单实体类
 */
public class Order {

    /**
     * 订单ID（UUID）
     */
    private String orderId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * Slot ID（库存桶ID）
     */
    private String slotId;

    /**
     * 商品ID
     */
    private String productId;

    /**
     * 订单状态: CREATED-已创建, PAID-已支付, CANCELLED-已取消
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    public Order() {
    }

    public Order(String orderId, String userId, String slotId, String status) {
        this.orderId = orderId;
        this.userId = userId;
        this.slotId = slotId;
        this.status = status;
    }

    // Getters and Setters

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSlotId() {
        return slotId;
    }

    public void setSlotId(String slotId) {
        this.slotId = slotId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
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
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", userId='" + userId + '\'' +
                ", slotId='" + slotId + '\'' +
                ", productId='" + productId + '\'' +
                ", status='" + status + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
