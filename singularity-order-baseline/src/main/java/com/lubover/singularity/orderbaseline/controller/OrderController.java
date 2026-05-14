package com.lubover.singularity.orderbaseline.controller;

import com.lubover.singularity.orderbaseline.dto.ApiResponse;
import com.lubover.singularity.orderbaseline.dto.Result;
import com.lubover.singularity.orderbaseline.entity.Order;
import com.lubover.singularity.orderbaseline.mapper.OrderMapper;
import com.lubover.singularity.orderbaseline.service.OrderService;
import com.lubover.singularity.orderbaseline.service.RedisStockGate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final RedisStockGate redisStockGate;
    private final String stockKey;

    public OrderController(OrderService orderService,
            OrderMapper orderMapper,
            RedisStockGate redisStockGate,
            @Value("${baseline.benchmark.redis-stock-key:baseline:stock:1001}") String stockKey) {
        this.orderService = orderService;
        this.orderMapper = orderMapper;
        this.redisStockGate = redisStockGate;
        this.stockKey = stockKey;
    }

    @PostMapping("/snag")
    public Map<String, Object> snagOrder(@RequestBody Map<String, Object> request) {
        String userId = request.get("userId") == null ? null : String.valueOf(request.get("userId"));
        String productId = request.get("productId") == null ? null : String.valueOf(request.get("productId"));

        if (userId == null || userId.isBlank()) {
            return ApiResponse.failure("userId is required");
        }
        if (productId == null || productId.isBlank()) {
            return ApiResponse.failure("productId is required");
        }

        Result result = orderService.snagOrder(userId, productId);
        if (result.isSuccess()) {
            Map<String, Object> data = new HashMap<>();
            data.put("orderId", result.getMessage());
            return ApiResponse.success(data);
        }
        return ApiResponse.failure(result.getMessage());
    }

    @GetMapping("/{orderId}")
    public Map<String, Object> getOrderById(@PathVariable("orderId") String orderId) {
        Order order = orderMapper.selectByOrderId(orderId);
        if (order == null) {
            return ApiResponse.failure("order not found");
        }
        return ApiResponse.success(order);
    }

    @PutMapping("/{orderId}/status")
    public Map<String, Object> updateOrderStatus(@PathVariable("orderId") String orderId,
            @RequestBody Map<String, Object> request) {
        String status = request.get("status") == null ? null : String.valueOf(request.get("status"));
        if (status == null || status.isBlank()) {
            return ApiResponse.failure("status is required");
        }
        int rows = orderMapper.updateStatus(orderId, status);
        if (rows <= 0) {
            return ApiResponse.failure("order not found");
        }
        Order order = orderMapper.selectByOrderId(orderId);
        return ApiResponse.success(order);
    }

    @PostMapping("/{orderId}/pay")
    public Map<String, Object> payOrder(@PathVariable("orderId") String orderId,
            @RequestBody Map<String, Object> request) {
        String userId = request.get("userId") == null ? null : String.valueOf(request.get("userId"));
        if (userId == null || userId.isBlank()) {
            return ApiResponse.failure("userId is required");
        }
        Result result = orderService.payOrder(orderId, userId);
        if (result.isSuccess()) {
            return ApiResponse.success(null);
        }
        return ApiResponse.failure(result.getMessage());
    }

    @GetMapping("/list")
    public Map<String, Object> listOrders(
            @RequestParam(value = "actorId", required = false) String actorId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        if (size < 1) {
            size = 10;
        }
        if (page < 0) {
            page = 0;
        }

        String userId = (actorId == null || actorId.isBlank()) ? null : actorId;
        String filterStatus = (status == null || status.isBlank()) ? null : status;

        int offset = page * size;
        List<Order> content = orderMapper.selectList(userId, filterStatus, offset, size);
        long totalElements = orderMapper.countList(userId, filterStatus);
        long totalPages = totalElements == 0 ? 0 : (totalElements + size - 1) / size;

        Map<String, Object> data = new HashMap<>();
        data.put("content", content);
        data.put("totalElements", totalElements);
        data.put("totalPages", totalPages);
        data.put("page", page);
        data.put("size", size);
        return ApiResponse.success(data);
    }

    @GetMapping("/admin/stock")
    public Map<String, Object> getStock() {
        String value = redisStockGate.getStock(stockKey);
        Map<String, Object> data = new HashMap<>();
        data.put("key", stockKey);
        data.put("stock", Long.parseLong(value));
        return ApiResponse.success(data);
    }

    @PostMapping("/admin/stock/reset")
    public Map<String, Object> resetStock(@RequestBody Map<String, Object> request) {
        Object qty = request.get("quantity");
        if (qty == null) {
            return ApiResponse.failure("quantity is required");
        }
        long quantity;
        try {
            quantity = Long.parseLong(String.valueOf(qty));
        } catch (NumberFormatException e) {
            return ApiResponse.failure("invalid quantity");
        }
        if (quantity < 0) {
            return ApiResponse.failure("quantity must be >= 0");
        }

        redisStockGate.setStock(stockKey, quantity);
        Map<String, Object> data = new HashMap<>();
        data.put("key", stockKey);
        data.put("stock", quantity);
        return ApiResponse.success(data);
    }
}
