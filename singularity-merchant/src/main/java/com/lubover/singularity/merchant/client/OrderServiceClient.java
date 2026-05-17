package com.lubover.singularity.merchant.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "singularity-order")
public interface OrderServiceClient {

    @PostMapping("/api/order/slot/register")
    Map<String, Object> registerSlot(@RequestBody Map<String, Object> request);
}
