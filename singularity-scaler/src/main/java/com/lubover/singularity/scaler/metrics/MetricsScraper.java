package com.lubover.singularity.scaler.metrics;

import com.lubover.singularity.scaler.config.ScalerProperties;
import com.lubover.singularity.scaler.config.ServiceConfig;
import com.lubover.singularity.scaler.discovery.InstanceDiscovery;
import com.lubover.singularity.scaler.model.QpsSample;
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
import java.util.List;
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

    /** 聚合后的计数快照，key 为 serviceName（多副本共用一条时间序列） */
    private final Map<String, MetricSnapshot> previousSnapshots = new ConcurrentHashMap<>();
    /** 上次算出的可靠 QPS，用于状态展示；不可信周期内继续显示该值 */
    private final Map<String, Double> lastReliableQpsByService = new ConcurrentHashMap<>();

    public ServiceConfig getServiceConfig(String serviceName) {
        if (scalerProperties.getServices() == null) {
            return null;
        }
        return scalerProperties.getServices().stream()
                .filter(s -> s.getName().equals(serviceName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 仅展示用：读上次调度周期算出的可靠 QPS。
     * 切勿在每次 HTTP status 查询里调用 {@link #sampleQps}，否则会插入额外时间点，把 Δ/Δt 压得极小并误触缩容。
     */
    public double getDisplayQps(String serviceName) {
        return lastReliableQpsByService.getOrDefault(serviceName, 0.0);
    }

    public QpsSample sampleQps(String serviceName) {
        List<Instance> instances = instanceDiscovery.getHealthyInstances(serviceName);
        if (instances.isEmpty()) {
            log.warn("No healthy instances found for {}", serviceName);
            return QpsSample.unreliable(lastReliableQpsByService.getOrDefault(serviceName, 0.0));
        }

        long now = System.currentTimeMillis();
        double totalCount = 0.0;
        int ok = 0;
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
                totalCount += prometheusTextParser.extractRate(metrics, "http_server_requests_seconds_count");
                ok++;
            } catch (Exception e) {
                log.warn("Failed to scrape metrics from {}: {}", url, e.getMessage());
            }
        }

        if (ok == 0) {
            log.warn("No successful prometheus scrape for {} ({} instances attempted)", serviceName, instances.size());
            return QpsSample.unreliable(lastReliableQpsByService.getOrDefault(serviceName, 0.0));
        }
        if (ok < instances.size()) {
            log.warn("Partial prometheus scrape for {} ({}/{} healthy instances ok); skip QPS snapshot to avoid false scale-down",
                    serviceName, ok, instances.size());
            return QpsSample.unreliable(lastReliableQpsByService.getOrDefault(serviceName, 0.0));
        }

        String key = serviceName + "@aggregate";
        MetricSnapshot prev = previousSnapshots.put(key, new MetricSnapshot(now, totalCount));
        if (prev == null) {
            log.info("QPS baseline set for {} (count={}, instances_ok={}/{})",
                    serviceName, totalCount, ok, instances.size());
            return QpsSample.unreliable(lastReliableQpsByService.getOrDefault(serviceName, 0.0));
        }

        double seconds = (now - prev.getTimestamp()) / 1000.0;
        if (seconds <= 0) {
            return QpsSample.unreliable(lastReliableQpsByService.getOrDefault(serviceName, 0.0));
        }

        int maxGap = scalerProperties.getMetricsMaxSnapshotGapSeconds();
        if (seconds > maxGap) {
            log.warn("QPS snapshot gap {}s > {}s for {}, resetting baseline (avoid false scale-down)",
                    seconds, maxGap, serviceName);
            previousSnapshots.put(key, new MetricSnapshot(now, totalCount));
            return QpsSample.unreliable(lastReliableQpsByService.getOrDefault(serviceName, 0.0));
        }

        double delta = totalCount - prev.getValue();
        if (delta < 0) {
            log.warn("Prometheus counter sum decreased for {} (redeploy?), resetting baseline", serviceName);
            previousSnapshots.put(key, new MetricSnapshot(now, totalCount));
            return QpsSample.unreliable(lastReliableQpsByService.getOrDefault(serviceName, 0.0));
        }

        double qps = delta / seconds;
        log.info("Scraped QPS for {}: {} (count={}, delta={}, seconds={}, scrape_ok={}/{})",
                serviceName, qps, totalCount, delta, seconds, ok, instances.size());
        lastReliableQpsByService.put(serviceName, qps);
        return QpsSample.reliable(qps);
    }

    /**
     * 从各实例 actuator/prometheus 汇总 CPU / 堆内存占比（0~1），供 {@link com.lubover.singularity.scaler.policy.PolicyEvaluator} 使用。
     * 与 {@link #sampleQps} 相同实例列表；任一副本拉取失败则返回 empty。
     */
    public java.util.Optional<ResourceMetrics> sampleResourceMetrics(String serviceName, double policyQps) {
        List<Instance> instances = instanceDiscovery.getHealthyInstances(serviceName);
        if (instances.isEmpty()) {
            return java.util.Optional.empty();
        }
        double cpuSum = 0.0;
        double memRatioSum = 0.0;
        int ok = 0;
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
                    log.warn("resource scrape: {} status {}", url, response.statusCode());
                    continue;
                }
                var metrics = prometheusTextParser.parse(response.body());
                double cpu = prometheusTextParser.extractRate(metrics, "process_cpu_usage");
                if (cpu <= 0) {
                    cpu = prometheusTextParser.extractRate(metrics, "system_cpu_usage");
                }
                double used = prometheusTextParser.extractRate(metrics, "jvm_memory_used_bytes");
                double max = prometheusTextParser.extractRate(metrics, "jvm_memory_max_bytes");
                double memRatio = max > 0 ? Math.min(1.0, used / max) : 0.0;
                cpuSum += Math.min(1.0, Math.max(0.0, cpu));
                memRatioSum += memRatio;
                ok++;
            } catch (Exception e) {
                log.warn("resource scrape failed {}: {}", url, e.getMessage());
            }
        }
        if (ok == 0 || ok < instances.size()) {
            log.warn("resource scrape incomplete for {} ({}/{} instances)", serviceName, ok, instances.size());
            return java.util.Optional.empty();
        }
        double n = ok;
        return java.util.Optional.of(new ResourceMetrics(
                policyQps,
                cpuSum / n,
                memRatioSum / n
        ));
    }
}
