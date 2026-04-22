package com.lubover.singularity.stock.controller;

import com.lubover.singularity.stock.entity.Stock;
import com.lubover.singularity.stock.entity.StockChangeLog;
import com.lubover.singularity.stock.mapper.StockChangeLogMapper;
import com.lubover.singularity.stock.mapper.StockMapper;
import com.lubover.singularity.stock.service.StockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService stockService;
    private final StockMapper stockMapper;
    private final StockChangeLogMapper stockChangeLogMapper;

    public StockController(
            StockService stockService,
            StockMapper stockMapper,
            StockChangeLogMapper stockChangeLogMapper
    ) {
        this.stockService = stockService;
        this.stockMapper = stockMapper;
        this.stockChangeLogMapper = stockChangeLogMapper;
    }

    @GetMapping("/{productId}")
    public Map<String, Object> getStock(@PathVariable("productId") String productId) {
        Stock stock = stockService.getStock(productId);
        if (stock == null) {
            return failure("stock not found");
        }
        return success(stock);
    }

    @GetMapping("/list")
    public Map<String, Object> listStock() {
        List<Stock> list = stockMapper.selectAll();
        return success(list);
    }

    @PostMapping("/init")
    public Map<String, Object> initStock(@RequestBody Map<String, Object> request) {
        String productId = request.get("productId") == null ? null : String.valueOf(request.get("productId"));
        Long totalQuantity = null;
        Object totalQuantityRaw = request.get("totalQuantity");
        if (totalQuantityRaw != null) {
            try {
                totalQuantity = Long.valueOf(String.valueOf(totalQuantityRaw));
            } catch (NumberFormatException ignored) {
                return failure("totalQuantity must be a number");
            }
        }

        if (productId == null || productId.isBlank()) {
            return failure("productId is required");
        }
        if (totalQuantity == null) {
            return failure("totalQuantity is required");
        }

        try {
            stockService.initializeStock(productId, totalQuantity);
            return success(Map.of("productId", productId, "totalQuantity", totalQuantity));
        } catch (IllegalArgumentException e) {
            return failure(e.getMessage());
        }
    }

    @GetMapping("/change-log")
    public Map<String, Object> getChangeLogs(
            @RequestParam(value = "productId", required = false) String productId,
            @RequestParam(value = "status", required = false) Integer status
    ) {
        List<StockChangeLog> logs = stockChangeLogMapper.selectList(productId, status);
        return success(logs);
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
}
