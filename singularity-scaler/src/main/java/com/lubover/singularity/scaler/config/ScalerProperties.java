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
    /** {@link com.lubover.singularity.scaler.metrics.MetricHistory} 每个服务保留的样本数 */
    private int historySize = 10;
    /**
     * 连续多少次调度周期（可靠 QPS 且策略为缩容）才执行缩容，避免偶发低 QPS 或指标抖动误删副本。
     */
    private int scaleDownMinConsecutivePolls = 3;
    /**
     * 各副本自 Docker State.StartedAt 起至少经过该秒数后，才用 docker stats 参与扩容判断；
     * 避免启动、类加载、健康检查刚通过时 CPU 尖峰误触扩。JVM/Prometheus 路径不受此限制。
     */
    private int dockerStatsGraceSecondsAfterStart = 90;
    /**
     * 与上一轮 docker CPU（归一化 0~1，多核封顶 1）相比，Δ 超过该值视为短时暴增，可与绝对阈值无关也触发扩容。
     * 调度约 15s 一轮时，0.38 约等于「15s 内再抬 38 个点」（如 2%→40%）。
     */
    private double dockerCpuSurgeDeltaThreshold = 0.38;
    /**
     * 上一轮 CPU 低于此值视为「平常空闲」，若当前不低于 {@link #dockerCpuSurgeHighWater} 也视为暴增（兜住 1%→50%+）。
     */
    private double dockerCpuSurgeLowBaseline = 0.18;
    /** 与 {@link #dockerCpuSurgeLowBaseline} 配对：从 idle 抬升到至少该占比即 surge。 */
    private double dockerCpuSurgeHighWater = 0.45;
    /**
     * 上一轮 docker 快照早于该秒数则不做暴增比较（避免基线过旧或宕机恢复后误判）。
     * 宜略大于 2～3 倍 {@link #intervalSeconds}。
     */
    private int dockerCpuSurgeMaxPollGapSeconds = 50;
    /**
     * 自动缩容前：若已采到 docker 聚合 CPU（0~1）不低于该值，则禁止缩容（与 JVM 低占用解耦，避免压测中误删副本）。
     * 设为 0 表示关闭此保护。
     */
    private double dockerCpuScaleDownBlockThreshold = 0.35;
    private List<ServiceConfig> services;
}
