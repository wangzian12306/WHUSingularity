package com.lubover.singularity.scaler.policy;

import com.lubover.singularity.scaler.config.ServiceConfig;
import com.lubover.singularity.scaler.metrics.MetricHistory;
import com.lubover.singularity.scaler.model.ResourceMetrics;
import com.lubover.singularity.scaler.model.ScaleAction;
import org.springframework.stereotype.Component;

@Component
public class PolicyEvaluator {

    public ScaleAction evaluate(ResourceMetrics current, MetricHistory history, String serviceName,
                                int currentInstances, int minInstances, int maxInstances,
                                ServiceConfig config) {
        // Scale up: immediate, any resource紧张
        if (current.getCpuUsage() >= config.getCpuScaleUpThreshold()
                || current.getMemoryUsage() >= config.getMemoryScaleUpThreshold()) {
            if (currentInstances < maxInstances) {
                return ScaleAction.SCALE_UP;
            }
        }

        // Scale down: must be low for consecutive periods
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
