package com.lubover.singularity.product.controller;

import com.lubover.singularity.product.dto.ApiResponse;
import com.lubover.singularity.product.dto.CreateProductRequest;
import com.lubover.singularity.product.dto.PageResponse;
import com.lubover.singularity.product.dto.ProductView;
import com.lubover.singularity.product.dto.UpdateProductRequest;
import com.lubover.singularity.product.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductView>> create(@RequestBody CreateProductRequest request) {
        ProductView view = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(view));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductView> detail(@PathVariable("productId") String productId) {
        return ApiResponse.success(productService.getByProductId(productId));
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductView> update(@PathVariable("productId") String productId, @RequestBody UpdateProductRequest request) {
        return ApiResponse.success(productService.update(productId, request));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> delete(@PathVariable("productId") String productId) {
        productService.delete(productId);
        return ApiResponse.successMessage("deleted");
    }

    @GetMapping("/list")
    public ApiResponse<PageResponse<ProductView>> list(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return ApiResponse.success(productService.list(status, category, keyword, pageNo, pageSize));
    }

    @GetMapping("/public/list")
    public ApiResponse<PageResponse<ProductView>> publicList(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "100") int pageSize) {
        return ApiResponse.success(productService.list(1, category, keyword, pageNo, pageSize));
    }
}
