package com.lubover.singularity.order.controller;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lubover.singularity.api.Actor;
import com.lubover.singularity.api.Result;
import com.lubover.singularity.order.entity.Order;
import com.lubover.singularity.order.mapper.OrderMapper;
import com.lubover.singularity.order.registry.SlotRegistry;
import com.lubover.singularity.order.service.OrderService;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final SlotRegistry slotRegistry;

    public OrderController(OrderService orderService, OrderMapper orderMapper, SlotRegistry slotRegistry) {
        this.orderService = orderService;
        this.orderMapper = orderMapper;
        this.slotRegistry = slotRegistry;
    }

    @PostMapping("/snag")
    public CompletableFuture<Map<String, Object>> snagOrder(@RequestBody Map<String, Object> request) {
        String userId = request.get("userId") == null ? null : String.valueOf(request.get("userId"));
        if (userId == null || userId.isBlank()) {
            return CompletableFuture.completedFuture(failure("userId is required"));
        }

        String productId = request.get("productId") == null ? null : String.valueOf(request.get("productId"));

        CompletableFuture<Result> future;
        if (productId != null && !productId.isBlank()) {
            future = orderService.snagOrderByProduct(new SimpleActor(userId), productId);
        } else {
            future = orderService.snagOrder(new SimpleActor(userId));
        }

        return future.thenApply(result -> {
            if (result.isSuccess()) {
                Map<String, Object> data = new HashMap<>();
                data.put("orderId", result.getMessage());
                return success(data);
            }
            return failure(result.getMessage());
        });
    }

    @GetMapping("/{orderId}")
    public Map<String, Object> getOrderById(@PathVariable("orderId") String orderId) {
        Order order = orderMapper.selectByOrderId(orderId);
        if (order == null) {
            return failure("order not found");
        }
        return success(order);
    }

    @PutMapping("/{orderId}/status")
    public Map<String, Object> updateOrderStatus(@PathVariable("orderId") String orderId,
            @RequestBody Map<String, Object> request) {
        String status = request.get("status") == null ? null : String.valueOf(request.get("status"));
        if (status == null || status.isBlank()) {
            return failure("status is required");
        }
        int rows = orderMapper.updateStatus(orderId, status);
        if (rows <= 0) {
            return failure("order not found");
        }
        Order order = orderMapper.selectByOrderId(orderId);
        return success(order);
    }

    @PostMapping("/{orderId}/pay")
    public Map<String, Object> payOrder(@PathVariable("orderId") String orderId,
            @RequestBody Map<String, Object> request) {
        String userId = request.get("userId") == null ? null : String.valueOf(request.get("userId"));
        if (userId == null || userId.isBlank()) {
            return failure("userId is required");
        }
        String userType = request.get("userType") == null ? null : String.valueOf(request.get("userType"));
        Result result = orderService.payOrder(orderId, userId, userType);
        if (result.isSuccess()) {
            return success(null);
        }
        return failure(result.getMessage());
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
        return success(data);
    }

    @GetMapping("/list-by-products")
    public Map<String, Object> listOrdersByProducts(
            @RequestParam(value = "productIds") List<String> productIds,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        if (size < 1) {
            size = 10;
        }
        if (page < 0) {
            page = 0;
        }

        String filterStatus = (status == null || status.isBlank()) ? null : status;

        int offset = page * size;
        List<Order> content = orderMapper.selectByProductIds(productIds, filterStatus, offset, size);
        long totalElements = orderMapper.countByProductIds(productIds, filterStatus);
        long totalPages = totalElements == 0 ? 0 : (totalElements + size - 1) / size;

        Map<String, Object> data = new HashMap<>();
        data.put("content", content);
        data.put("totalElements", totalElements);
        data.put("totalPages", totalPages);
        data.put("page", page);
        data.put("size", size);
        return success(data);
    }

    @PostMapping("/slot/register")
    public Map<String, Object> registerSlot(@RequestBody Map<String, Object> request) {
        String slotId = request.get("slotId") == null ? null : String.valueOf(request.get("slotId"));
        String redisKey = request.get("redisKey") == null ? null : String.valueOf(request.get("redisKey"));
        String productId = request.get("productId") == null ? null : String.valueOf(request.get("productId"));

        if (slotId == null || slotId.isBlank()) {
            return failure("slotId is required");
        }
        if (redisKey == null || redisKey.isBlank()) {
            return failure("redisKey is required");
        }
        if (productId == null || productId.isBlank()) {
            return failure("productId is required");
        }

        slotRegistry.addSlot(slotId, redisKey, productId);
        return success(Map.of("slotId", slotId, "redisKey", redisKey, "productId", productId));
    }

    private Map<String, Object> success(Object data) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("data", data);
        return resp;
    }

    private Map<String, Object> failure(String message) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", false);
        resp.put("message", message);
        return resp;
    }

    private static final class SimpleActor implements Actor {
        private final String id;

        private SimpleActor(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Map<String, ?> getMetadata() {
            return Collections.emptyMap();
        }
    }
}
