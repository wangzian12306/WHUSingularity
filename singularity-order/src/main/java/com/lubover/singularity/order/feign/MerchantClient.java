package com.lubover.singularity.order.feign;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "singularity-merchant")
public interface MerchantClient {

    @PostMapping("/api/merchant/internal/add-balance")
    Map<String, Object> addBalanceByProduct(@RequestBody Map<String, Object> body);

    @PostMapping("/api/merchant/internal/deduct-balance")
    Map<String, Object> deductBalance(@RequestBody Map<String, Object> body);
}
