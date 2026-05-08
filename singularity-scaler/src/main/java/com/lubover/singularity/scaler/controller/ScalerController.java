package com.lubover.singularity.scaler.controller;

import com.lubover.singularity.scaler.model.ScaleResult;
import com.lubover.singularity.scaler.model.ServiceState;
import com.lubover.singularity.scaler.orchestration.ScalingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scaler")
@RequiredArgsConstructor
public class ScalerController {

    private final ScalingService scalingService;

    @GetMapping("/status")
    public ResponseEntity<List<ServiceState>> status() {
        return ResponseEntity.ok(scalingService.getAllServiceStates());
    }

    @PostMapping("/scale")
    public ResponseEntity<ScaleResult> scale(@RequestBody ScaleRequest request) {
        ScaleResult result = scalingService.manualScale(request.getService(), request.getAction());
        return ResponseEntity.ok(result);
    }
}
