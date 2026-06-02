package com.lubover.singularity.product.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/product")
public class ProductPingController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        Map<String, Object> data = new HashMap<>();
        data.put("service", "singularity-product");
        data.put("status", "ok");

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("data", data);
        return resp;
    }
}
