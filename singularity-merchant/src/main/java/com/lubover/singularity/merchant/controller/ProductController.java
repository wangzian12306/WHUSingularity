package com.lubover.singularity.merchant.controller;

import com.lubover.singularity.merchant.dto.*;
import com.lubover.singularity.merchant.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/product")
public class ProductController {

    @Autowired
    private ProductService productService;

    @PostMapping
    public ApiResponse<ProductView> createProduct(@RequestBody CreateProductRequest request) {
        ProductView product = productService.createProduct(request);
        return ApiResponse.success(product);
    }

    @GetMapping("/list")
    public ApiResponse<List<ProductView>> getProducts() {
        List<ProductView> products = productService.getCurrentMerchantProducts();
        return ApiResponse.success(products);
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductView> getProduct(@PathVariable("productId") String productId) {
        ProductView product = productService.getProduct(productId);
        return ApiResponse.success(product);
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductView> updateProduct(@PathVariable("productId") String productId,
                                                  @RequestBody UpdateProductRequest request) {
        ProductView product = productService.updateProduct(productId, request);
        return ApiResponse.success(product);
    }

    @PutMapping("/{productId}/status")
    public ApiResponse<Void> updateProductStatus(@PathVariable("productId") String productId,
                                                 @RequestBody Map<String, Integer> request) {
        Integer status = request.get("status");
        productService.updateProductStatus(productId, status);
        return ApiResponse.successMessage("Status updated");
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(@PathVariable("productId") String productId) {
        productService.deleteProduct(productId);
        return ApiResponse.successMessage("Product deleted");
    }
}
