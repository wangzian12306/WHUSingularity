package com.lubover.singularity.product.observability;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ProductObservabilityService {

    private final AtomicLong productReadTotal = new AtomicLong();
    private final AtomicLong eventSendSuccessTotal = new AtomicLong();
    private final AtomicLong eventSendFailureTotal = new AtomicLong();
    private final AtomicLong eventConsumeSuccessTotal = new AtomicLong();
    private final AtomicLong eventConsumeFailureTotal = new AtomicLong();
    private final AtomicLong stockQuerySuccessTotal = new AtomicLong();
    private final AtomicLong stockQueryFailureTotal = new AtomicLong();
    private final ConcurrentHashMap<String, AtomicLong> readSourceCounters = new ConcurrentHashMap<>();

    public void recordProductRead(String source) {
        productReadTotal.incrementAndGet();
        readSourceCounters.computeIfAbsent(source == null ? "UNKNOWN" : source, key -> new AtomicLong())
                .incrementAndGet();
    }

    public void recordEventSend(boolean success) {
        if (success) {
            eventSendSuccessTotal.incrementAndGet();
        } else {
            eventSendFailureTotal.incrementAndGet();
        }
    }

    public void recordEventConsume(boolean success) {
        if (success) {
            eventConsumeSuccessTotal.incrementAndGet();
        } else {
            eventConsumeFailureTotal.incrementAndGet();
        }
    }

    public void recordStockQuery(boolean success) {
        if (success) {
            stockQuerySuccessTotal.incrementAndGet();
        } else {
            stockQueryFailureTotal.incrementAndGet();
        }
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("productReadTotal", productReadTotal.get());
        metrics.put("productReadBySource", readSourceSnapshot());
        metrics.put("eventSendSuccessTotal", eventSendSuccessTotal.get());
        metrics.put("eventSendFailureTotal", eventSendFailureTotal.get());
        metrics.put("eventConsumeSuccessTotal", eventConsumeSuccessTotal.get());
        metrics.put("eventConsumeFailureTotal", eventConsumeFailureTotal.get());
        metrics.put("stockQuerySuccessTotal", stockQuerySuccessTotal.get());
        metrics.put("stockQueryFailureTotal", stockQueryFailureTotal.get());
        return metrics;
    }

    private Map<String, Long> readSourceSnapshot() {
        Map<String, Long> values = new LinkedHashMap<>();
        readSourceCounters.forEach((source, count) -> values.put(source, count.get()));
        return values;
    }
}
