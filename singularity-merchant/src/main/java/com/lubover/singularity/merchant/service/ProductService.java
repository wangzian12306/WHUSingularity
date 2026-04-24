package com.lubover.singularity.merchant.service;

import com.lubover.singularity.merchant.dto.ProductView;
import com.lubover.singularity.merchant.entity.Product;

import java.util.List;

public interface ProductService {

    Product createProduct(Product product);

    Product getProductById(Long id);

    ProductView getProductViewById(Long id);

    List<Product> getProductsByMerchantId(Long merchantId);

    List<Product> getProductsByMerchantIdWithStatus(Long merchantId, Integer status);

    List<ProductView> getCurrentMerchantProducts();

    Product updateProduct(Product product);

    void updateProductStatus(Long id, Integer status);

    void incrementSalesCount(Long id, Long count);

    void incrementViewCount(Long id, Long count);

    void deleteProduct(Long id);
}
