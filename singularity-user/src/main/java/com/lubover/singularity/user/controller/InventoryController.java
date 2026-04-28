package com.lubover.singularity.user.controller;

import com.lubover.singularity.user.auth.AuthRequestContext;
import com.lubover.singularity.user.dto.ApiResponse;
import com.lubover.singularity.user.entity.InventoryChangeLog;
import com.lubover.singularity.user.entity.ProductInventory;
import com.lubover.singularity.user.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    @GetMapping("/product/{productId}")
    public ApiResponse<ProductInventory> getInventory(@PathVariable Long productId) {
        ProductInventory inventory = inventoryService.getInventoryByProductId(productId);
        return ApiResponse.success(inventory);
    }

    @PostMapping("/product/{productId}/add")
    public ApiResponse<Void> addInventory(@PathVariable Long productId, @RequestBody Map<String, Object> request) {
        Long quantity = Long.valueOf(request.get("quantity").toString());
        String remark = request.get("remark") != null ? request.get("remark").toString() : "Manual add";
        inventoryService.addInventory(productId, quantity, remark);
        return ApiResponse.successMessage("Inventory added");
    }

    @PostMapping("/product/{productId}/adjust")
    public ApiResponse<Void> adjustInventory(@PathVariable Long productId, @RequestBody Map<String, Object> request) {
        Long quantity = Long.valueOf(request.get("quantity").toString());
        String remark = request.get("remark") != null ? request.get("remark").toString() : "Manual adjust";
        inventoryService.adjustInventory(productId, quantity, remark);
        return ApiResponse.successMessage("Inventory adjusted");
    }

    @GetMapping("/product/{productId}/logs")
    public ApiResponse<List<InventoryChangeLog>> getChangeLogs(@PathVariable Long productId) {
        List<InventoryChangeLog> logs = inventoryService.getChangeLogsByProductId(productId);
        return ApiResponse.success(logs);
    }

    @GetMapping("/merchant/logs")
    public ApiResponse<List<InventoryChangeLog>> getMerchantChangeLogs() {
        Long merchantId = AuthRequestContext.getCurrentUserId();
        List<InventoryChangeLog> logs = inventoryService.getChangeLogsByMerchantId(merchantId);
        return ApiResponse.success(logs);
    }
}
