package com.lubover.singularity.merchant.service.impl;

import com.lubover.singularity.merchant.auth.AuthRequestContext;
import com.lubover.singularity.merchant.client.OrderServiceClient;
import com.lubover.singularity.merchant.client.ProductServiceClient;
import com.lubover.singularity.merchant.client.StockServiceClient;
import com.lubover.singularity.merchant.dto.*;
import com.lubover.singularity.merchant.entity.MerchantProduct;
import com.lubover.singularity.merchant.exception.BusinessException;
import com.lubover.singularity.merchant.exception.ErrorCode;
import com.lubover.singularity.merchant.mapper.MerchantProductMapper;
import com.lubover.singularity.merchant.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductServiceClient productServiceClient;

    @Autowired
    private StockServiceClient stockServiceClient;

    @Autowired
    private OrderServiceClient orderServiceClient;

    @Autowired
    private MerchantProductMapper merchantProductMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private void fillStockInfo(ProductView productView) {
        try {
            Map<String, Object> stockResp = stockServiceClient.getStock(productView.getProductId());
            if (stockResp != null && Boolean.TRUE.equals(stockResp.get("success"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stockData = (Map<String, Object>) stockResp.get("data");
                if (stockData != null) {
                    if (stockData.get("totalQuantity") != null) {
                        productView.setTotalQuantity(Long.valueOf(String.valueOf(stockData.get("totalQuantity"))));
                    }
                    if (stockData.get("availableQuantity") != null) {
                        productView.setAvailableQuantity(Long.valueOf(String.valueOf(stockData.get("availableQuantity"))));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to get stock for product: " + productView.getProductId());
        }
    }

    private void initSlotAndRedis(String productId, Long totalQuantity) {
        String slotId = "slot-" + productId;
        String redisKey = "stock:" + slotId;

        try {
            redisTemplate.opsForValue().set(redisKey, String.valueOf(totalQuantity));
            System.out.println("Redis stock bucket initialized: " + redisKey + " = " + totalQuantity);
        } catch (Exception e) {
            System.err.println("Failed to init Redis stock bucket: " + redisKey + ", error: " + e.getMessage());
        }

        try {
            Map<String, Object> slotReq = new HashMap<>();
            slotReq.put("slotId", slotId);
            slotReq.put("redisKey", redisKey);
            slotReq.put("productId", productId);
            orderServiceClient.registerSlot(slotReq);
            System.out.println("Slot registered: slotId=" + slotId + ", productId=" + productId);
        } catch (Exception e) {
            System.err.println("Failed to register slot for product: " + productId + ", error: " + e.getMessage());
        }
    }

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

        Long totalQuantity = request.getTotalQuantity();

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

        if (totalQuantity != null && totalQuantity > 0) {
            try {
                Map<String, Object> initReq = new HashMap<>();
                initReq.put("productId", productView.getProductId());
                initReq.put("totalQuantity", totalQuantity);
                stockServiceClient.initStock(initReq);
                productView.setTotalQuantity(totalQuantity);
                productView.setAvailableQuantity(totalQuantity);
            } catch (Exception e) {
                System.err.println("Failed to init stock for product: " + productView.getProductId() + ", error: " + e.getMessage());
            }

            initSlotAndRedis(productView.getProductId(), totalQuantity);
        }

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

        Long totalQuantity = request.getTotalQuantity();

        ApiResponse<ProductView> response = productServiceClient.updateProduct(productId, request);
        if (!response.isSuccess()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    response.getError() != null ? response.getError().getMessage() : "Update product failed");
        }

        ProductView productView = response.getData();
        productView.setMerchantStatus(merchantProduct.getStatus());
        productView.setSortOrder(merchantProduct.getSortOrder());

        if (totalQuantity != null && totalQuantity > 0) {
            try {
                Map<String, Object> initReq = new HashMap<>();
                initReq.put("productId", productId);
                initReq.put("totalQuantity", totalQuantity);
                stockServiceClient.initStock(initReq);
                productView.setTotalQuantity(totalQuantity);
                productView.setAvailableQuantity(totalQuantity);
            } catch (Exception e) {
                System.err.println("Failed to update stock for product: " + productId + ", error: " + e.getMessage());
            }

            initSlotAndRedis(productId, totalQuantity);
        } else {
            fillStockInfo(productView);
        }

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
        fillStockInfo(productView);

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
                    fillStockInfo(productView);
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
