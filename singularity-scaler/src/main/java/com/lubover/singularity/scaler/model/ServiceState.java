package com.lubover.singularity.scaler.model;

import lombok.Data;

@Data
public class ServiceState {
    private String serviceName;
    private int instanceCount;
    private double currentQps;
    private double avgCpuUsage;
    private double avgMemoryUsage;
    private boolean cooldownActive;
    private String lastAction;
    private long lastActionTime;
}
