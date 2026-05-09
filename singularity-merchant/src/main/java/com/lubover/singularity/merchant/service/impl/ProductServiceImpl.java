package com.lubover.singularity.merchant.service.impl;

import com.lubover.singularity.merchant.auth.AuthRequestContext;
import com.lubover.singularity.merchant.client.ProductServiceClient;
import com.lubover.singularity.merchant.dto.*;
import com.lubover.singularity.merchant.entity.MerchantProduct;
import com.lubover.singularity.merchant.exception.BusinessException;
import com.lubover.singularity.merchant.exception.ErrorCode;
import com.lubover.singularity.merchant.mapper.MerchantProductMapper;
import com.lubover.singularity.merchant.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductServiceClient productServiceClient;

    @Autowired
    private MerchantProductMapper merchantProductMapper;

    @Override
    @Transactional
    public ProductView createProduct(CreateProductRequest request) {
        Long merchantId = AuthRequestContext.getMerchantId();
        if (merchantId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        if (request.getProductId() == null || request.getProductId().isEmpty()) {
            request.setProductId(UUID.randomUUID().toString());
        }

        ApiResponse<ProductView> response = productServiceClient.createProduct(request);
        if (!response.isSuccess()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    response.getError() != null ? response.getError().getMessage() : "Create product failed");
        }

        ProductView productView = response.getData();

        MerchantProduct merchantProduct = new MerchantProduct();
        merchantProduct.setMerchantId(merchantId);
        merchantProduct.setProductId(productView.getProductId());
        merchantProduct.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        merchantProduct.setSortOrder(0);
        merchantProductMapper.insert(merchantProduct);

        productView.setMerchantStatus(merchantProduct.getStatus());
        productView.setSortOrder(merchantProduct.getSortOrder());

        return productView;
    }

    @Override
    @Transactional
    public ProductView updateProduct(String productId, UpdateProductRequest request) {
        Long merchantId = AuthRequestContext.getMerchantId();
        if (merchantId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        MerchantProduct merchantProduct = merchantProductMapper.selectByMerchantIdAndProductId(merchantId, productId);
        if (merchantProduct == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        ApiResponse<ProductView> response = productServiceClient.updateProduct(productId, request);
        if (!response.isSuccess()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    response.getError() != null ? response.getError().getMessage() : "Update product failed");
        }

        ProductView productView = response.getData();
        productView.setMerchantStatus(merchantProduct.getStatus());
        productView.setSortOrder(merchantProduct.getSortOrder());

        return productView;
    }

    @Override
    @Transactional
    public void deleteProduct(String productId) {
        Long merchantId = AuthRequestContext.getMerchantId();
        if (merchantId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        MerchantProduct merchantProduct = merchantProductMapper.selectByMerchantIdAndProductId(merchantId, productId);
        if (merchantProduct == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        ApiResponse<Void> response = productServiceClient.deleteProduct(productId);
        if (!response.isSuccess()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    response.getError() != null ? response.getError().getMessage() : "Delete product failed");
        }

        merchantProductMapper.delete(merchantId, productId);
    }

    @Override
    public ProductView getProduct(String productId) {
        Long merchantId = AuthRequestContext.getMerchantId();
        if (merchantId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        MerchantProduct merchantProduct = merchantProductMapper.selectByMerchantIdAndProductId(merchantId, productId);
        if (merchantProduct == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        ApiResponse<ProductView> response = productServiceClient.getProduct(productId);
        if (!response.isSuccess()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    response.getError() != null ? response.getError().getMessage() : "Get product failed");
        }

        ProductView productView = response.getData();
        productView.setMerchantStatus(merchantProduct.getStatus());
        productView.setSortOrder(merchantProduct.getSortOrder());

        return productView;
    }

    @Override
    public List<ProductView> getCurrentMerchantProducts() {
        Long merchantId = AuthRequestContext.getMerchantId();
        if (merchantId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        List<MerchantProduct> merchantProducts = merchantProductMapper.selectByMerchantId(merchantId);
        List<ProductView> result = new ArrayList<>();

        for (MerchantProduct mp : merchantProducts) {
            try {
                ApiResponse<ProductView> response = productServiceClient.getProduct(mp.getProductId());
                if (response.isSuccess() && response.getData() != null) {
                    ProductView productView = response.getData();
                    productView.setMerchantStatus(mp.getStatus());
                    productView.setSortOrder(mp.getSortOrder());
                    result.add(productView);
                }
            } catch (Exception e) {
                System.err.println("Failed to get product: " + mp.getProductId());
            }
        }

        return result;
    }

    @Override
    @Transactional
    public void updateProductStatus(String productId, Integer status) {
        Long merchantId = AuthRequestContext.getMerchantId();
        if (merchantId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        MerchantProduct merchantProduct = merchantProductMapper.selectByMerchantIdAndProductId(merchantId, productId);
        if (merchantProduct == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        merchantProductMapper.updateStatus(merchantId, productId, status);

        UpdateProductRequest updateRequest = new UpdateProductRequest();
        updateRequest.setStatus(status);
        productServiceClient.updateProduct(productId, updateRequest);
    }
}
