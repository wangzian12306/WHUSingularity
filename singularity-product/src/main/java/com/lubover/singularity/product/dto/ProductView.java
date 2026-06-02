package com.lubover.singularity.product.dto;

import com.lubover.singularity.product.entity.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductView {

    private String productId;
    private String name;
    private String subtitle;
    private String mainImage;
    private String category;
    private String tags;
    private Integer status;
    private BigDecimal price;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static ProductView from(Product product) {
        ProductView view = new ProductView();
        view.setProductId(product.getProductId());
        view.setName(product.getName());
        view.setSubtitle(product.getSubtitle());
        view.setMainImage(product.getMainImage());
        view.setCategory(product.getCategory());
        view.setTags(product.getTags());
        view.setStatus(product.getStatus());
        view.setPrice(product.getPrice());
        view.setVersion(product.getVersion());
        view.setCreateTime(product.getCreateTime());
        view.setUpdateTime(product.getUpdateTime());
        return view;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getMainImage() {
        return mainImage;
    }

    public void setMainImage(String mainImage) {
        this.mainImage = mainImage;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
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
