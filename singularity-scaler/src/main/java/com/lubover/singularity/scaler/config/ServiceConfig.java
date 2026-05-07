package com.lubover.singularity.scaler.config;

import lombok.Data;

import java.util.Map;

@Data
public class ServiceConfig {
    private String name;
    private int basePort;
    private int portStep;
    private int minInstances;
    private int maxInstances;

    private double cpuScaleUpThreshold = 0.70;
    private double memoryScaleUpThreshold = 0.80;
    private double cpuScaleDownThreshold = 0.20;
    private double memoryScaleDownThreshold = 0.30;
    private int scaleDownConsecutivePeriods = 3;

    private String image;
    private Map<String, String> env;
}
