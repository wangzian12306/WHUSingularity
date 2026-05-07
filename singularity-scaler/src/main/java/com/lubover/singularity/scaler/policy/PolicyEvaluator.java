package com.lubover.singularity.scaler.policy;

import com.lubover.singularity.scaler.config.ServiceConfig;
import com.lubover.singularity.scaler.metrics.MetricHistory;
import com.lubover.singularity.scaler.model.ResourceMetrics;
import com.lubover.singularity.scaler.model.ScaleAction;
import org.springframework.stereotype.Component;

@Component
public class PolicyEvaluator {

    /** 扩容：任一资源超阈值且未达副本上限（用于 JVM 或 Docker cgroup 指标）。 */
    public boolean shouldScaleUp(ResourceMetrics m, int currentInstances, int maxInstances, ServiceConfig config) {
        if (currentInstances >= maxInstances) {
            return false;
        }
        return m.getCpuUsage() >= config.getCpuScaleUpThreshold()
                || m.getMemoryUsage() >= config.getMemoryScaleUpThreshold();
    }

    /**
     * 缩容：仅依据历史窗口（与此前 evaluate 行为一致）；调用方需在 QPS 可靠且已有 JVM 资源样本时调用。
     */
    public ScaleAction evaluateScaleDown(MetricHistory history, String serviceName,
                                         int currentInstances, int minInstances, ServiceConfig config) {
        int consecutivePeriods = config.getScaleDownConsecutivePeriods() > 0
                ? config.getScaleDownConsecutivePeriods() : 3;

        boolean allLow = history.allRecentBelow(serviceName, consecutivePeriods, m ->
                m.getCpuUsage() <= config.getCpuScaleDownThreshold()
                        && m.getMemoryUsage() <= config.getMemoryScaleDownThreshold()
        );

        if (allLow && currentInstances > minInstances) {
            return ScaleAction.SCALE_DOWN;
        }

        return ScaleAction.NONE;
    }
}
