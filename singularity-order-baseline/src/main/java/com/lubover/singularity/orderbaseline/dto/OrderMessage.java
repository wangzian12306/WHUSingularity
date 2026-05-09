package com.lubover.singularity.orderbaseline.dto;

import java.time.LocalDateTime;

public class OrderMessage {

    private String orderId;
    private String productId;
    private String userId;
    private String slotId;
    private LocalDateTime createTime;

    public OrderMessage() {
    }

    public OrderMessage(String orderId, String userId, String productId, String slotId, LocalDateTime createTime) {
        this.orderId = orderId;
        this.userId = userId;
        this.productId = productId;
        this.slotId = slotId;
        this.createTime = createTime;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "OrderMessage{" +
                "orderId='" + orderId + '\'' +
                ", productId='" + productId + '\'' +
                ", userId='" + userId + '\'' +
                ", slotId='" + slotId + '\'' +
                ", createTime=" + createTime +
                '}';
    }
}
