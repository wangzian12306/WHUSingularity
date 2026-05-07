package com.lubover.singularity.scaler.metrics;

import com.lubover.singularity.scaler.config.ScalerProperties;
import com.lubover.singularity.scaler.model.ResourceMetrics;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Component
public class MetricHistory {

    private final Map<String, ArrayDeque<ResourceMetrics>> histories = new ConcurrentHashMap<>();
    private final int maxSize;

    public MetricHistory(ScalerProperties scalerProperties) {
        this.maxSize = scalerProperties.getHistorySize() > 0 ? scalerProperties.getHistorySize() : 10;
    }

    public void record(String serviceName, ResourceMetrics metrics) {
        histories.computeIfAbsent(serviceName, k -> new ArrayDeque<>()).addLast(metrics);
        ArrayDeque<ResourceMetrics> deque = histories.get(serviceName);
        while (deque.size() > maxSize) {
            deque.pollFirst();
        }
    }

    public List<ResourceMetrics> recent(String serviceName, int n) {
        ArrayDeque<ResourceMetrics> deque = histories.get(serviceName);
        if (deque == null) {
            return Collections.emptyList();
        }
        List<ResourceMetrics> result = new ArrayList<>();
        Iterator<ResourceMetrics> it = deque.descendingIterator();
        int count = 0;
        while (it.hasNext() && count < n) {
            result.add(it.next());
            count++;
        }
        Collections.reverse(result);
        return result;
    }

    public boolean allRecentBelow(String serviceName, int n, Predicate<ResourceMetrics> predicate) {
        List<ResourceMetrics> recent = recent(serviceName, n);
        if (recent.size() < n) {
            return false;
        }
        return recent.stream().allMatch(predicate);
    }
}
