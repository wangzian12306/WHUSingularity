package com.lubover.singularity.scaler.metrics;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MetricSnapshot {
    private long timestamp;
    private double value;
}
