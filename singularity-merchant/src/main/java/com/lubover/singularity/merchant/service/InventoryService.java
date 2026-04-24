package com.lubover.singularity.merchant.service;

import com.lubover.singularity.merchant.entity.InventoryChangeLog;
import com.lubover.singularity.merchant.entity.ProductInventory;

import java.util.List;

public interface InventoryService {

    ProductInventory createInventory(Long productId, Long initialQuantity);

    ProductInventory getInventoryByProductId(Long productId);

    ProductInventory getInventoryById(Long id);

    void addInventory(Long productId, Long quantity, String remark);

    void lockInventory(Long productId, Long quantity, String orderId);

    void unlockInventory(Long productId, Long quantity);

    void confirmSale(Long productId, Long quantity);

    void adjustInventory(Long productId, Long quantity, String remark);

    List<InventoryChangeLog> getChangeLogsByProductId(Long productId);

    List<InventoryChangeLog> getChangeLogsByMerchantId(Long merchantId);
}
