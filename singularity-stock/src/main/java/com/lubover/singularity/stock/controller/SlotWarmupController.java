package com.lubover.singularity.stock.controller;

import com.lubover.singularity.stock.dto.SlotPreheatRequest;
import com.lubover.singularity.stock.dto.SlotPreheatResponse;
import com.lubover.singularity.stock.service.SlotWarmupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/stock/slots")
public class SlotWarmupController {

    private final SlotWarmupService slotWarmupService;

    public SlotWarmupController(SlotWarmupService slotWarmupService) {
        this.slotWarmupService = slotWarmupService;
    }

    @PostMapping("/preheat")
    public ResponseEntity<?> preheat(@RequestBody SlotPreheatRequest request) {
        try {
            boolean overwrite = request.getOverwrite() != null && request.getOverwrite();
                SlotPreheatResponse response = slotWarmupService.warmupSlot(
                    request.getSlotId(),
                    request.getRedisKey(),
                    request.getQuantity(),
                    overwrite);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}
