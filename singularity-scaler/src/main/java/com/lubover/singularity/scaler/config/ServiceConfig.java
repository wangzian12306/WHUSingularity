package com.lubover.singularity.scaler.config;

import lombok.Data;

import java.util.Map;

@Data
public class ServiceConfig {
    /**
     * compose：由 docker compose --scale 伸缩（与单文件、统一服务发现一致）。
     * 为空或非 compose 时沿用 docker run 克隆实例（旧行为，用于未迁到 compose scale 的服务）。
     */
    private String scaleMode;
    /** Compose 服务名，如 singularity-order（与 docker-compose.backend.yml 中 service 键一致） */
    private String composeService;
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

    /**
     * 覆盖全局 {@link ScalerProperties#getDockerCpuScaleDownBlockThreshold()}；≤0 表示沿用全局。
     */
    private double dockerCpuScaleDownBlockThreshold = 0.0;

    private String image;
    private Map<String, String> env;
}
