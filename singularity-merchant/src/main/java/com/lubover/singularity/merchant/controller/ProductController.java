package com.lubover.singularity.merchant.controller;

import com.lubover.singularity.merchant.dto.ApiResponse;
import com.lubover.singularity.merchant.dto.ProductView;
import com.lubover.singularity.merchant.entity.Product;
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
    public ApiResponse<ProductView> createProduct(@RequestBody Product product) {
        Product created = productService.createProduct(product);
        ProductView view = productService.getProductViewById(created.getId());
        return ApiResponse.success(view);
    }

    @GetMapping("/list")
    public ApiResponse<List<ProductView>> getProducts() {
        List<ProductView> views = productService.getCurrentMerchantProducts();
        return ApiResponse.success(views);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductView> getProduct(@PathVariable Long id) {
        ProductView view = productService.getProductViewById(id);
        return ApiResponse.success(view);
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductView> updateProduct(@PathVariable Long id, @RequestBody Product product) {
        product.setId(id);
        Product updated = productService.updateProduct(product);
        ProductView view = productService.getProductViewById(updated.getId());
        return ApiResponse.success(view);
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateProductStatus(@PathVariable Long id, @RequestBody Map<String, Integer> request) {
        Integer status = request.get("status");
        productService.updateProductStatus(id, status);
        return ApiResponse.successMessage("Status updated");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ApiResponse.successMessage("Product deleted");
    }
}
