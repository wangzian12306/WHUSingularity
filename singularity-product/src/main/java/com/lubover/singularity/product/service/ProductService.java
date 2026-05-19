package com.lubover.singularity.product.service;

import com.lubover.singularity.product.dto.CreateProductRequest;
import com.lubover.singularity.product.dto.PageResponse;
import com.lubover.singularity.product.dto.ProductDetailView;
import com.lubover.singularity.product.dto.ProductView;
import com.lubover.singularity.product.dto.UpdateProductRequest;

public interface ProductService {

    ProductView create(CreateProductRequest request);

    ProductView getByProductId(String productId);

    ProductDetailView getDetailWithStock(String productId);

    ProductView update(String productId, UpdateProductRequest request);

    ProductView updateStatus(String productId, Integer status);

    void delete(String productId);

    PageResponse<ProductView> list(Integer status, String category, String keyword, int pageNo, int pageSize);
}
