package com.lubover.singularity.scaler.metrics;

import com.lubover.singularity.scaler.config.ScalerProperties;
import com.lubover.singularity.scaler.config.ServiceConfig;
import com.lubover.singularity.scaler.discovery.InstanceDiscovery;
import com.lubover.singularity.scaler.model.ResourceMetrics;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsScraper {

    private final InstanceDiscovery instanceDiscovery;
    private final PrometheusTextParser prometheusTextParser;
    private final ScalerProperties scalerProperties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final Map<String, MetricSnapshot> previousSnapshots = new ConcurrentHashMap<>();

    public ResourceMetrics scrape(String serviceName) {
        var instances = instanceDiscovery.getHealthyInstances(serviceName);
        if (instances.isEmpty()) {
            log.warn("No healthy instances found for {}", serviceName);
            return new ResourceMetrics(0.0, 0.0, 0.0);
        }

        double totalQps = 0.0;
        double totalCpu = 0.0;
        double totalMem = 0.0;
        int successful = 0;
        long now = System.currentTimeMillis();

        for (Instance instance : instances) {
            String url = String.format("http://%s:%d/actuator/prometheus", instance.getIp(), instance.getPort());
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    log.warn("Failed to scrape metrics from {}: status {}", url, response.statusCode());
                    continue;
                }

                var metrics = prometheusTextParser.parse(response.body());

                // QPS: calculate rate per instance
                double totalCount = prometheusTextParser.extractRate(metrics, "http_server_requests_seconds_count");
                String snapshotKey = serviceName + "@" + instance.getIp() + ":" + instance.getPort();
                MetricSnapshot prev = previousSnapshots.put(snapshotKey, new MetricSnapshot(now, totalCount));
                if (prev != null) {
                    double delta = totalCount - prev.getValue();
                    double seconds = (now - prev.getTimestamp()) / 1000.0;
                    if (seconds > 0) {
                        totalQps += delta / seconds;
                    }
                }

                // CPU usage
                double cpu = prometheusTextParser.extractByLabelFilter(metrics, "process_cpu_usage", null, null);
                totalCpu += cpu;

                // Memory usage: heap only
                double memUsed = prometheusTextParser.extractByLabelFilter(metrics, "jvm_memory_used_bytes", "area", "heap");
                double memMax = prometheusTextParser.extractByLabelFilter(metrics, "jvm_memory_max_bytes", "area", "heap");
                double memUsage = memMax > 0 ? memUsed / memMax : 0.0;
                totalMem += memUsage;

                successful++;
            } catch (Exception e) {
                log.warn("Failed to scrape metrics from {}: {}", url, e.getMessage());
            }
        }

        if (successful == 0) {
            return new ResourceMetrics(0.0, 0.0, 0.0);
        }

        double avgQps = totalQps / instances.size();
        double avgCpu = totalCpu / successful;
        double avgMem = totalMem / successful;

        log.info("Scraped for {}: avgQps={}, avgCpu={}, avgMem={} (successful={}/{})",
                serviceName, String.format("%.2f", avgQps), String.format("%.2f", avgCpu),
                String.format("%.2f", avgMem), successful, instances.size());

        return new ResourceMetrics(avgQps, avgCpu, avgMem);
    }

    public ServiceConfig getServiceConfig(String serviceName) {
        if (scalerProperties.getServices() == null) {
            return null;
        }
        return scalerProperties.getServices().stream()
                .filter(s -> s.getName().equals(serviceName))
                .findFirst()
                .orElse(null);
    }
}
