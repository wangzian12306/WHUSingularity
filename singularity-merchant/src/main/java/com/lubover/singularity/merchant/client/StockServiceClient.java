package com.lubover.singularity.merchant.client;

import com.lubover.singularity.merchant.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "singularity-stock")
public interface StockServiceClient {

    @PostMapping("/api/stock/init")
    Map<String, Object> initStock(@RequestBody Map<String, Object> request);

    @GetMapping("/api/stock/{productId}")
    Map<String, Object> getStock(@PathVariable("productId") String productId);
}
