package com.lubover.singularity.scaler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "scaler")
public class ScalerProperties {
    private int intervalSeconds = 15;
    private int cooldownSeconds = 120;
    /** 与 deploy/docker-compose.backend.yml 顶层 name: 一致，用于 com.docker.compose.project 与 -p */
    private String composeProject = "singularity";
    /** 容器内路径，Scaler 与 compose 文件同挂 workspace 时为 /workspace/deploy/... */
    private String composeFile = "/workspace/deploy/docker-compose.backend.yml";
    /**
     * 两次成功 QPS 采样间隔超过该秒数则丢弃上一快照、本周期不提供 policy QPS，
     * 避免长时间空窗把增量摊成接近 0 误触缩容。
     */
    private int metricsMaxSnapshotGapSeconds = 120;
    /**
     * 连续多少次调度周期（可靠 QPS 且策略为缩容）才执行缩容，避免偶发低 QPS 或指标抖动误删副本。
     */
    private int scaleDownMinConsecutivePolls = 3;
    private List<ServiceConfig> services;
}
