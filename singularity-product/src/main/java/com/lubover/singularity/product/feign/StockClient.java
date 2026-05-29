package com.lubover.singularity.product.feign;

import com.lubover.singularity.product.dto.ApiResponse;
import com.lubover.singularity.product.dto.StockView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "singularity-stock", path = "/api/stock")
public interface StockClient {

    @GetMapping("/{productId}")
    ApiResponse<StockView> getStock(@PathVariable("productId") String productId);
}
