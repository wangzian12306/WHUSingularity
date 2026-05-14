package com.lubover.singularity.merchant.client;

import com.lubover.singularity.merchant.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "singularity-product")
public interface ProductServiceClient {

    @PostMapping("/api/product")
    ApiResponse<ProductView> createProduct(@RequestBody CreateProductRequest request);

    @GetMapping("/api/product/{productId}")
    ApiResponse<ProductView> getProduct(@PathVariable("productId") String productId);

    @PutMapping("/api/product/{productId}")
    ApiResponse<ProductView> updateProduct(@PathVariable("productId") String productId,
                                            @RequestBody UpdateProductRequest request);

    @DeleteMapping("/api/product/{productId}")
    ApiResponse<Void> deleteProduct(@PathVariable("productId") String productId);

    @GetMapping("/api/product/list")
    ApiResponse<PageResponse<ProductView>> listProducts(@RequestParam(value = "status", required = false) Integer status,
                                                         @RequestParam(value = "category", required = false) String category,
                                                         @RequestParam(value = "keyword", required = false) String keyword,
                                                         @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                                         @RequestParam(value = "pageSize", defaultValue = "10") int pageSize);
}
