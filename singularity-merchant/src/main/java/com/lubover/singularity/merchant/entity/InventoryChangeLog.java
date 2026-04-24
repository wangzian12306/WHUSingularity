package com.lubover.singularity.merchant.entity;

import java.time.LocalDateTime;

public class InventoryChangeLog {

    private Long id;
    private Long productId;
    private Long merchantId;
    private Integer changeType;
    private Long changeQuantity;
    private Long beforeQuantity;
    private Long afterQuantity;
    private String orderId;
    private String remark;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public Integer getChangeType() {
        return changeType;
    }

    public void setChangeType(Integer changeType) {
        this.changeType = changeType;
    }

    public Long getChangeQuantity() {
        return changeQuantity;
    }

    public void setChangeQuantity(Long changeQuantity) {
        this.changeQuantity = changeQuantity;
    }

    public Long getBeforeQuantity() {
        return beforeQuantity;
    }

    public void setBeforeQuantity(Long beforeQuantity) {
        this.beforeQuantity = beforeQuantity;
    }

    public Long getAfterQuantity() {
        return afterQuantity;
    }

    public void setAfterQuantity(Long afterQuantity) {
        this.afterQuantity = afterQuantity;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
