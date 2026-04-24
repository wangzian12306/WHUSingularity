package com.lubover.singularity.merchant.entity;

import java.time.LocalDateTime;

public class ProductInventory {

    private Long id;
    private Long productId;
    private Long totalQuantity;
    private Long availableQuantity;
    private Long lockedQuantity;
    private Long soldQuantity;
    private Long warningQuantity;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

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

    public Long getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Long totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public Long getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(Long availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public Long getLockedQuantity() {
        return lockedQuantity;
    }

    public void setLockedQuantity(Long lockedQuantity) {
        this.lockedQuantity = lockedQuantity;
    }

    public Long getSoldQuantity() {
        return soldQuantity;
    }

    public void setSoldQuantity(Long soldQuantity) {
        this.soldQuantity = soldQuantity;
    }

    public Long getWarningQuantity() {
        return warningQuantity;
    }

    public void setWarningQuantity(Long warningQuantity) {
        this.warningQuantity = warningQuantity;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
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
}
