package com.lubover.singularity.merchant.service.impl;

import com.lubover.singularity.merchant.auth.AuthRequestContext;
import com.lubover.singularity.merchant.entity.InventoryChangeLog;
import com.lubover.singularity.merchant.entity.Product;
import com.lubover.singularity.merchant.entity.ProductInventory;
import com.lubover.singularity.merchant.exception.BusinessException;
import com.lubover.singularity.merchant.exception.ErrorCode;
import com.lubover.singularity.merchant.mapper.InventoryChangeLogMapper;
import com.lubover.singularity.merchant.mapper.ProductInventoryMapper;
import com.lubover.singularity.merchant.mapper.ProductMapper;
import com.lubover.singularity.merchant.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryServiceImpl implements InventoryService {

    @Autowired
    private ProductInventoryMapper inventoryMapper;

    @Autowired
    private InventoryChangeLogMapper logMapper;

    @Autowired
    private ProductMapper productMapper;

    @Override
    @Transactional
    public ProductInventory createInventory(Long productId, Long initialQuantity) {
        ProductInventory inventory = new ProductInventory();
        inventory.setProductId(productId);
        inventory.setTotalQuantity(initialQuantity);
        inventory.setAvailableQuantity(initialQuantity);
        inventory.setLockedQuantity(0L);
        inventory.setSoldQuantity(0L);
        inventory.setWarningQuantity(10L);
        inventory.setVersion(0L);

        inventoryMapper.insert(inventory);
        return inventory;
    }

    @Override
    @Cacheable(value = "inventory", key = "#productId")
    public ProductInventory getInventoryByProductId(Long productId) {
        ProductInventory inventory = inventoryMapper.selectByProductId(productId);
        if (inventory == null) {
            throw new BusinessException(ErrorCode.INVENTORY_NOT_FOUND);
        }
        return inventory;
    }

    @Override
    public ProductInventory getInventoryById(Long id) {
        ProductInventory inventory = inventoryMapper.selectById(id);
        if (inventory == null) {
            throw new BusinessException(ErrorCode.INVENTORY_NOT_FOUND);
        }
        return inventory;
    }

    @Override
    @Transactional
    @CacheEvict(value = "inventory", key = "#productId")
    public void addInventory(Long productId, Long quantity, String remark) {
        ProductInventory inventory = getInventoryByProductId(productId);
        Long beforeQuantity = inventory.getAvailableQuantity();
        Long version = inventory.getVersion();

        int updated = inventoryMapper.incrementAvailable(productId, quantity, version);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INVENTORY_VERSION_MISMATCH);
        }

        Long merchantId = getMerchantIdByProductId(productId);
        Long operatorId = AuthRequestContext.getMerchantId();
        String operatorName = getShopNameByMerchantId(merchantId);

        recordChangeLog(productId, merchantId, 1, quantity, beforeQuantity, beforeQuantity + quantity,
                null, remark, operatorId, operatorName);
    }

    @Override
    @Transactional
    @CacheEvict(value = "inventory", key = "#productId")
    public void lockInventory(Long productId, Long quantity, String orderId) {
        ProductInventory inventory = getInventoryByProductId(productId);
        Long beforeQuantity = inventory.getAvailableQuantity();
        Long version = inventory.getVersion();

        int updated = inventoryMapper.lockInventory(productId, quantity, version);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INVENTORY_VERSION_MISMATCH);
        }

        Long merchantId = getMerchantIdByProductId(productId);
        Long operatorId = AuthRequestContext.getMerchantId();
        String operatorName = getShopNameByMerchantId(merchantId);

        recordChangeLog(productId, merchantId, 2, -quantity, beforeQuantity, beforeQuantity - quantity,
                orderId, "Lock inventory for order", operatorId, operatorName);
    }

    @Override
    @Transactional
    @CacheEvict(value = "inventory", key = "#productId")
    public void unlockInventory(Long productId, Long quantity) {
        ProductInventory inventory = getInventoryByProductId(productId);
        Long beforeQuantity = inventory.getAvailableQuantity();

        inventoryMapper.unlockInventory(productId, quantity);

        Long merchantId = getMerchantIdByProductId(productId);
        Long operatorId = AuthRequestContext.getMerchantId();
        String operatorName = getShopNameByMerchantId(merchantId);

        recordChangeLog(productId, merchantId, 3, quantity, beforeQuantity, beforeQuantity + quantity,
                null, "Unlock inventory", operatorId, operatorName);
    }

    @Override
    @Transactional
    @CacheEvict(value = "inventory", key = "#productId")
    public void confirmSale(Long productId, Long quantity) {
        inventoryMapper.confirmSale(productId, quantity);

        Long merchantId = getMerchantIdByProductId(productId);
        ProductInventory inventory = getInventoryByProductId(productId);

        recordChangeLog(productId, merchantId, 4, quantity, inventory.getAvailableQuantity(),
                inventory.getAvailableQuantity(), null, "Confirm sale", merchantId,
                getShopNameByMerchantId(merchantId));
    }

    @Override
    @Transactional
    @CacheEvict(value = "inventory", key = "#productId")
    public void adjustInventory(Long productId, Long quantity, String remark) {
        ProductInventory inventory = getInventoryByProductId(productId);
        Long beforeQuantity = inventory.getAvailableQuantity();

        inventory.setAvailableQuantity(beforeQuantity + quantity);
        inventory.setTotalQuantity(inventory.getTotalQuantity() + quantity);
        inventoryMapper.update(inventory);

        Long merchantId = getMerchantIdByProductId(productId);
        Long operatorId = AuthRequestContext.getMerchantId();
        String operatorName = getShopNameByMerchantId(merchantId);

        recordChangeLog(productId, merchantId, 5, quantity, beforeQuantity, beforeQuantity + quantity,
                null, remark, operatorId, operatorName);
    }

    @Override
    public List<InventoryChangeLog> getChangeLogsByProductId(Long productId) {
        return logMapper.selectByProductId(productId);
    }

    @Override
    public List<InventoryChangeLog> getChangeLogsByMerchantId(Long merchantId) {
        return logMapper.selectByMerchantId(merchantId);
    }

    private void recordChangeLog(Long productId, Long merchantId, Integer changeType,
                                Long changeQuantity, Long beforeQuantity, Long afterQuantity,
                                String orderId, String remark, Long operatorId, String operatorName) {
        InventoryChangeLog log = new InventoryChangeLog();
        log.setProductId(productId);
        log.setMerchantId(merchantId);
        log.setChangeType(changeType);
        log.setChangeQuantity(changeQuantity);
        log.setBeforeQuantity(beforeQuantity);
        log.setAfterQuantity(afterQuantity);
        log.setOrderId(orderId);
        log.setRemark(remark);
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        logMapper.insert(log);
    }

    private Long getMerchantIdByProductId(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return product.getMerchantId();
    }

    private String getShopNameByMerchantId(Long merchantId) {
        Product product = productMapper.selectByMerchantId(merchantId).stream().findFirst().orElse(null);
        if (product != null) {
            return "Shop " + merchantId;
        }
        return "System";
    }
}
