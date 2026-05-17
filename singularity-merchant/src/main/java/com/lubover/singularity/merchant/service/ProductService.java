package com.lubover.singularity.merchant.service;

import com.lubover.singularity.merchant.dto.*;

import java.util.List;

public interface ProductService {

    ProductView createProduct(CreateProductRequest request);

    ProductView updateProduct(String productId, UpdateProductRequest request);

    void deleteProduct(String productId);

    ProductView getProduct(String productId);

    List<ProductView> getCurrentMerchantProducts();

    void updateProductStatus(String productId, Integer status);
}
