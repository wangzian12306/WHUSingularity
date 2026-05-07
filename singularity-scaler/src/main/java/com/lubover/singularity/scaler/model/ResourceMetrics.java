package com.lubover.singularity.scaler.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResourceMetrics {
    private double qps;
    private double cpuUsage;      // 0.0 ~ 1.0
    private double memoryUsage;   // 0.0 ~ 1.0
}
