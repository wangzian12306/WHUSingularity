package com.lubover.singularity.merchant.service.impl;

import com.lubover.singularity.merchant.auth.AuthRequestContext;
import com.lubover.singularity.merchant.dto.MerchantView;
import com.lubover.singularity.merchant.dto.ProductView;
import com.lubover.singularity.merchant.entity.Merchant;
import com.lubover.singularity.merchant.entity.Product;
import com.lubover.singularity.merchant.entity.ProductInventory;
import com.lubover.singularity.merchant.exception.BusinessException;
import com.lubover.singularity.merchant.exception.ErrorCode;
import com.lubover.singularity.merchant.mapper.ProductMapper;
import com.lubover.singularity.merchant.service.InventoryService;
import com.lubover.singularity.merchant.service.MerchantService;
import com.lubover.singularity.merchant.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private InventoryService inventoryService;

    @Override
    @Transactional
    public Product createProduct(Product product) {
        Long merchantId = AuthRequestContext.getMerchantId();
        if (merchantId == null) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }

        product.setMerchantId(merchantId);
        if (product.getStatus() == null) {
            product.setStatus(0);
        }
        if (product.getSortOrder() == null) {
            product.setSortOrder(0);
        }
        if (product.getIsHot() == null) {
            product.setIsHot(0);
        }
        if (product.getIsRecommend() == null) {
            product.setIsRecommend(0);
        }
        if (product.getSalesCount() == null) {
            product.setSalesCount(0L);
        }
        if (product.getViewCount() == null) {
            product.setViewCount(0L);
        }

        productMapper.insert(product);

        inventoryService.createInventory(product.getId(), 0L);

        return product;
    }

    @Override
    @Cacheable(value = "product", key = "#id")
    public Product getProductById(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }

    @Override
    public ProductView getProductViewById(Long id) {
        Product product = getProductById(id);
        return convertToView(product);
    }

    @Override
    public List<Product> getProductsByMerchantId(Long merchantId) {
        return productMapper.selectByMerchantId(merchantId);
    }

    @Override
    public List<Product> getProductsByMerchantIdWithStatus(Long merchantId, Integer status) {
        return productMapper.selectByMerchantIdWithStatus(merchantId, status);
    }

    @Override
    public List<ProductView> getCurrentMerchantProducts() {
        Long merchantId = AuthRequestContext.getMerchantId();
        if (merchantId == null) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }

        List<Product> products = getProductsByMerchantId(merchantId);
        return products.stream().map(this::convertToView).collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = "product", key = "#product.id")
    public Product updateProduct(Product product) {
        Product existing = getProductById(product.getId());
        
        Long merchantId = AuthRequestContext.getMerchantId();
        if (!existing.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_BELONG_TO_MERCHANT);
        }

        productMapper.update(product);
        return getProductById(product.getId());
    }

    @Override
    @Transactional
    @CacheEvict(value = "product", key = "#id")
    public void updateProductStatus(Long id, Integer status) {
        Product product = getProductById(id);
        
        Long merchantId = AuthRequestContext.getMerchantId();
        if (!product.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_BELONG_TO_MERCHANT);
        }

        productMapper.updateStatus(id, status);
    }

    @Override
    @Transactional
    public void incrementSalesCount(Long id, Long count) {
        productMapper.incrementSalesCount(id, count);
    }

    @Override
    @Transactional
    public void incrementViewCount(Long id, Long count) {
        productMapper.incrementViewCount(id, count);
    }

    @Override
    @Transactional
    @CacheEvict(value = "product", key = "#id")
    public void deleteProduct(Long id) {
        Product product = getProductById(id);
        
        Long merchantId = AuthRequestContext.getMerchantId();
        if (!product.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_BELONG_TO_MERCHANT);
        }

        productMapper.deleteById(id);
    }

    private ProductView convertToView(Product product) {
        ProductView view = new ProductView();
        view.setId(product.getId());
        view.setMerchantId(product.getMerchantId());
        view.setProductName(product.getProductName());
        view.setDescription(product.getDescription());
        view.setPrice(product.getPrice());
        view.setOriginalPrice(product.getOriginalPrice());
        view.setImageUrl(product.getImageUrl());
        view.setCategory(product.getCategory());
        view.setStatus(product.getStatus());
        view.setSortOrder(product.getSortOrder());
        view.setIsHot(product.getIsHot());
        view.setIsRecommend(product.getIsRecommend());
        view.setSalesCount(product.getSalesCount());
        view.setViewCount(product.getViewCount());
        view.setCreateTime(product.getCreateTime());

        try {
            MerchantView merchant = merchantService.getMerchantViewById(product.getMerchantId());
            view.setMerchantName(merchant.getShopName());
        } catch (Exception e) {
            view.setMerchantName("Unknown");
        }

        try {
            ProductInventory inventory = inventoryService.getInventoryByProductId(product.getId());
            if (inventory != null) {
                view.setAvailableQuantity(inventory.getAvailableQuantity());
            }
        } catch (Exception e) {
            view.setAvailableQuantity(0L);
        }

        return view;
    }
}
