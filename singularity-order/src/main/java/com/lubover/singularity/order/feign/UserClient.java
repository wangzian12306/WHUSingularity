package com.lubover.singularity.order.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.Map;

@FeignClient(name = "singularity-user")
public interface UserClient {

    @GetMapping("/api/user/{id}")
    Map<String, Object> getUserById(@PathVariable("id") Long id);

    @PostMapping("/api/user/{id}/deduct")
    Map<String, Object> deductBalance(@PathVariable("id") Long id,
                                      @RequestBody Map<String, BigDecimal> body);
}
