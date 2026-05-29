package com.lubover.singularity.product.dto;

public class ProductDetailView {

    private ProductView product;
    private StockView stock;

    public ProductDetailView() {
    }

    public ProductDetailView(ProductView product, StockView stock) {
        this.product = product;
        this.stock = stock;
    }

    public ProductView getProduct() {
        return product;
    }

    public void setProduct(ProductView product) {
        this.product = product;
    }

    public StockView getStock() {
        return stock;
    }

    public void setStock(StockView stock) {
        this.stock = stock;
    }
}
