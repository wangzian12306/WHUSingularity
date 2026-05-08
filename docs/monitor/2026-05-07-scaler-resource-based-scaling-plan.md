# singularity-scaler 资源利用率驱动伸缩 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 singularity-scaler 的伸缩决策从固定 QPS 硬编码阈值迁移到资源利用率（CPU / JVM 堆内存）百分比 + 集群多实例平均 + 滑动窗口平滑。

**Architecture:** 增强 MetricsScraper 以采集全部健康实例的 CPU/内存并取平均；新增 MetricHistory 为每个服务维护最近 N 个周期的平均指标环形队列；重写 PolicyEvaluator 以基于资源百分比决策（扩容即时触发，缩容需连续 3 个周期低负载）。四个服务独立监控、独立决策、独立伸缩。

**Tech Stack:** Java 21, Spring Boot 3.2.6, Spring Cloud Alibaba Nacos, Maven, Docker

---

## 文件结构映射

| 文件 | 操作 | 职责 |
|---|---|---|
| `singularity-scaler/src/main/java/.../model/ResourceMetrics.java` | 创建 | 单个周期内某服务的集群平均指标（QPS / CPU / 内存） |
| `singularity-scaler/src/main/java/.../metrics/MetricHistory.java` | 创建 | 每个服务独立的滑动窗口，存储最近 N 个周期的 ResourceMetrics |
| `singularity-scaler/src/main/java/.../metrics/PrometheusTextParser.java` | 修改 | 新增 `extractByLabelFilter`，支持按 label 过滤提取指标值 |
| `singularity-scaler/src/main/java/.../metrics/MetricsScraper.java` | 修改 | 遍历全部实例采集 CPU/内存/QPS，计算集群平均 |
| `singularity-scaler/src/main/java/.../config/ServiceConfig.java` | 修改 | 移除 QPS 阈值字段，新增 CPU/内存阈值 + 缩容连续周期配置 |
| `singularity-scaler/src/main/java/.../config/ScalerProperties.java` | 修改 | 新增 `historySize` |
| `singularity-scaler/src/main/java/.../policy/PolicyEvaluator.java` | 修改 | 重写为基于资源利用率百分比 + 历史窗口判断 |
| `singularity-scaler/src/main/java/.../model/ServiceState.java` | 修改 | 新增 `avgCpuUsage`、`avgMemoryUsage` |
| `singularity-scaler/src/main/java/.../orchestration/ScalingService.java` | 修改 | 注入 MetricHistory，更新 getServiceState 和 evaluateAndScale 流程 |
| `singularity-scaler/src/main/resources/application.yml` | 修改 | 替换 QPS 阈值为资源利用率阈值 |

---

## Task 1: ResourceMetrics 模型

**Files:**
- Create: `singularity-scaler/src/main/java/com/lubover/singularity/scaler/model/ResourceMetrics.java`

- [x] **Step 1: 创建 ResourceMetrics**

```java
package com.lubover.singularity.scaler.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResourceMetrics {
    private double qps;
    private double cpuUsage;      // 0.0 ~ 1.0
    private double memoryUsage;   // 0.0 ~ 1.0
}
```

- [x] **Step 2: Commit**

```bash
git add singularity-scaler/src/main/java/com/lubover/singularity/scaler/model/ResourceMetrics.java
git commit -m "feat(scaler): add ResourceMetrics model for CPU/memory/QPS"
```

---

## Task 2: PrometheusTextParser 增强

**Files:**
- Modify: `singularity-scaler/src/main/java/com/lubover/singularity/scaler/metrics/PrometheusTextParser.java`

- [x] **Step 1: 新增 extractByLabelFilter 方法**

在 `PrometheusTextParser.java` 中，现有 `extractRate` 方法下方新增：

```java
public double extractByLabelFilter(Map<String, Map<String, Double>> metrics, String metricName,
                                    String labelKey, String labelValue) {
    Map<String, Double> values = metrics.get(metricName);
    if (values == null || values.isEmpty()) {
        return 0.0;
    }
    if (labelKey == null) {
        return values.values().stream().mapToDouble(Double::doubleValue).sum();
    }
    double sum = 0.0;
    for (Map.Entry<String, Double> entry : values.entrySet()) {
        if (entry.getKey().contains(labelKey + "=" + labelValue)) {
            sum += entry.getValue();
        }
    }
    return sum;
}
```

- [x] **Step 2: Commit**

**Files:**
- Create: `singularity-scaler/src/main/java/com/lubover/singularity/scaler/metrics/MetricHistory.java`
- Modify: `singularity-scaler/src/main/java/com/lubover/singularity/scaler/config/ScalerProperties.java`

- [x] **Step 1: ScalerProperties 新增 historySize**

```java
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
    private int historySize = 10;
    private List<ServiceConfig> services;
}
```

- [x] **Step 2: 创建 MetricHistory**

```java
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
```

- [x] **Step 3: Commit**

**Files:**
- Modify: `singularity-scaler/src/main/java/com/lubover/singularity/scaler/metrics/MetricsScraper.java`

- [x] **Step 1: 重写 MetricsScraper**

完整替换文件内容：

```java
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
```

- [x] **Step 2: Commit**

**Files:**
- Modify: `singularity-scaler/src/main/java/com/lubover/singularity/scaler/config/ServiceConfig.java`

- [x] **Step 1: 替换 ServiceConfig**

完整替换文件内容：

```java
package com.lubover.singularity.scaler.config;

import lombok.Data;

import java.util.Map;

@Data
public class ServiceConfig {
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

    private String image;
    private Map<String, String> env;
}
```

- [x] **Step 2: Commit**

**Files:**
- Modify: `singularity-scaler/src/main/java/com/lubover/singularity/scaler/policy/PolicyEvaluator.java`

- [x] **Step 1: 重写 PolicyEvaluator**

完整替换文件内容：

```java
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
```

- [x] **Step 2: Commit**

**Files:**
- Modify: `singularity-scaler/src/main/java/com/lubover/singularity/scaler/model/ServiceState.java`

- [x] **Step 1: 修改 ServiceState**

完整替换文件内容：

```java
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
```

- [x] **Step 2: Commit**

**Files:**
- Modify: `singularity-scaler/src/main/java/com/lubover/singularity/scaler/orchestration/ScalingService.java`

- [x] **Step 1: 修改 ScalingService**

完整替换文件内容：

```java
package com.lubover.singularity.scaler.orchestration;

import com.lubover.singularity.scaler.config.ScalerProperties;
import com.lubover.singularity.scaler.config.ServiceConfig;
import com.lubover.singularity.scaler.discovery.InstanceDiscovery;
import com.lubover.singularity.scaler.docker.DockerCommandExecutor;
import com.lubover.singularity.scaler.docker.DockerContainerInspector;
import com.lubover.singularity.scaler.docker.PortAllocator;
import com.lubover.singularity.scaler.metrics.MetricHistory;
import com.lubover.singularity.scaler.metrics.MetricsScraper;
import com.lubover.singularity.scaler.model.ResourceMetrics;
import com.lubover.singularity.scaler.model.ScaleAction;
import com.lubover.singularity.scaler.model.ScaleResult;
import com.lubover.singularity.scaler.model.ServiceState;
import com.lubover.singularity.scaler.policy.CooldownManager;
import com.lubover.singularity.scaler.policy.PolicyEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScalingService {

    private final InstanceDiscovery instanceDiscovery;
    private final MetricsScraper metricsScraper;
    private final MetricHistory metricHistory;
    private final DockerContainerInspector containerInspector;
    private final PortAllocator portAllocator;
    private final DockerCommandExecutor dockerCommandExecutor;
    private final PolicyEvaluator policyEvaluator;
    private final CooldownManager cooldownManager;
    private final ScalerProperties scalerProperties;

    public List<ServiceState> getAllServiceStates() {
        List<ServiceState> states = new ArrayList<>();
        if (scalerProperties.getServices() == null) {
            return states;
        }
        for (ServiceConfig config : scalerProperties.getServices()) {
            states.add(getServiceState(config));
        }
        return states;
    }

    public ServiceState getServiceState(ServiceConfig config) {
        ServiceState state = new ServiceState();
        state.setServiceName(config.getName());
        int nacosCount = instanceDiscovery.getHealthyInstances(config.getName()).size();
        int dockerCount = containerInspector.getContainerNamesForService(config.getName()).size();
        state.setInstanceCount(Math.max(nacosCount, dockerCount));

        ResourceMetrics metrics = metricsScraper.scrape(config.getName());
        state.setCurrentQps(metrics.getQps());
        state.setAvgCpuUsage(metrics.getCpuUsage());
        state.setAvgMemoryUsage(metrics.getMemoryUsage());

        state.setCooldownActive(cooldownManager.isCooldownActive(config.getName(), scalerProperties.getCooldownSeconds()));
        Long lastTime = cooldownManager.getLastActionTime(config.getName());
        state.setLastActionTime(lastTime != null ? lastTime : 0);
        return state;
    }

    public ScaleResult evaluateAndScale(ServiceConfig config) {
        String serviceName = config.getName();

        if (cooldownManager.isCooldownActive(serviceName, scalerProperties.getCooldownSeconds())) {
            return new ScaleResult(serviceName, ScaleAction.NONE, "cooldown active");
        }

        int currentInstances = containerInspector.getContainerNamesForService(serviceName).size();
        if (currentInstances == 0) {
            currentInstances = instanceDiscovery.getHealthyInstances(serviceName).size();
        }

        ResourceMetrics metrics = metricsScraper.scrape(serviceName);
        metricHistory.record(serviceName, metrics);

        log.info("Service {}: instances={}, cpu={}, memory={}",
                serviceName, currentInstances,
                String.format("%.2f", metrics.getCpuUsage()),
                String.format("%.2f", metrics.getMemoryUsage()));

        ScaleAction action = policyEvaluator.evaluate(
                metrics, metricHistory, serviceName,
                currentInstances, config.getMinInstances(), config.getMaxInstances(),
                config
        );

        if (action == ScaleAction.SCALE_UP) {
            int newIndex = containerInspector.getMaxIndex(serviceName) + 1;
            int port = portAllocator.allocatePort(serviceName, config.getBasePort(), config.getPortStep());
            dockerCommandExecutor.startInstance(config, newIndex, port);
            cooldownManager.recordAction(serviceName);
            return new ScaleResult(serviceName, ScaleAction.SCALE_UP,
                    "scaled up to index " + newIndex + " on port " + port);
        }

        if (action == ScaleAction.SCALE_DOWN) {
            int maxIndex = containerInspector.getMaxIndex(serviceName);
            if (maxIndex >= 0) {
                String containerName = serviceName + "-" + maxIndex;
                dockerCommandExecutor.removeInstance(containerName);
                cooldownManager.recordAction(serviceName);
                return new ScaleResult(serviceName, ScaleAction.SCALE_DOWN,
                        "removed container " + containerName);
            }
        }

        return new ScaleResult(serviceName, ScaleAction.NONE, "metric within threshold");
    }

    public ScaleResult manualScale(String serviceName, ScaleAction action) {
        ServiceConfig config = scalerProperties.getServices().stream()
                .filter(s -> s.getName().equals(serviceName))
                .findFirst()
                .orElse(null);
        if (config == null) {
            return new ScaleResult(serviceName, ScaleAction.NONE, "unknown service");
        }

        if (action == ScaleAction.SCALE_UP) {
            int newIndex = containerInspector.getMaxIndex(serviceName) + 1;
            if (newIndex >= config.getMaxInstances()) {
                return new ScaleResult(serviceName, ScaleAction.NONE, "max instances reached");
            }
            int port = portAllocator.allocatePort(serviceName, config.getBasePort(), config.getPortStep());
            dockerCommandExecutor.startInstance(config, newIndex, port);
            cooldownManager.recordAction(serviceName);
            return new ScaleResult(serviceName, ScaleAction.SCALE_UP,
                    "scaled up to index " + newIndex + " on port " + port);
        }

        if (action == ScaleAction.SCALE_DOWN) {
            int maxIndex = containerInspector.getMaxIndex(serviceName);
            if (maxIndex < 0) {
                return new ScaleResult(serviceName, ScaleAction.NONE, "no instances to remove");
            }
            int currentCount = containerInspector.getContainerNamesForService(serviceName).size();
            if (currentCount <= config.getMinInstances()) {
                return new ScaleResult(serviceName, ScaleAction.NONE, "min instances reached");
            }
            String containerName = serviceName + "-" + maxIndex;
            dockerCommandExecutor.removeInstance(containerName);
            cooldownManager.recordAction(serviceName);
            return new ScaleResult(serviceName, ScaleAction.SCALE_DOWN,
                    "removed container " + containerName);
        }

        return new ScaleResult(serviceName, ScaleAction.NONE, "no action");
    }
}
```

- [x] **Step 2: Commit**

**Files:**
- Modify: `singularity-scaler/src/main/resources/application.yml`

- [x] **Step 1: 替换 application.yml 中 scaler.services 配置**

找到 `scaler:` 块，将 `services:` 下每个服务的 `qps-scale-up-threshold` 和 `qps-scale-down-threshold` 替换为资源利用率阈值：

```yaml
scaler:
  interval-seconds: 15
  cooldown-seconds: 120
  history-size: 10
  services:
    - name: singularity-order
      base-port: 8081
      port-step: 2
      min-instances: 1
      max-instances: 5
      cpu-scale-up-threshold: 0.70
      memory-scale-up-threshold: 0.80
      cpu-scale-down-threshold: 0.20
      memory-scale-down-threshold: 0.30
      scale-down-consecutive-periods: 3
      image: maven:3.9.9-eclipse-temurin-21
      env:
        SPRING_CLOUD_NACOS_SERVER_ADDR: nacos:8848
        SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/singularity_order?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
        SPRING_DATASOURCE_USERNAME: root
        SPRING_DATASOURCE_PASSWORD: root
        SPRING_DATA_REDIS_HOST: redis
        SPRING_DATA_REDIS_PORT: "6379"
        ROCKETMQ_NAME_SERVER: rmq-namesrv:9876
        ROCKETMQ_PRODUCER_GROUP: order-producer-group
        ROCKETMQ_CONSUMER_GROUP: order-consumer-group
        SPRING_APPLICATION_JSON: '{"singularity":{"order":{"slots":[{"id":"bucket-1","redis-key":"stock:bucket-1","product-id":"PROD_001"},{"id":"bucket-2","redis-key":"stock:bucket-2","product-id":"PROD_002"}]}}}'
    - name: singularity-user
      base-port: 8090
      port-step: 1
      min-instances: 1
      max-instances: 5
      cpu-scale-up-threshold: 0.70
      memory-scale-up-threshold: 0.80
      cpu-scale-down-threshold: 0.20
      memory-scale-down-threshold: 0.30
      scale-down-consecutive-periods: 3
      image: maven:3.9.9-eclipse-temurin-21
      env:
        SPRING_CLOUD_NACOS_SERVER_ADDR: nacos:8848
        SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/singularity_user?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
        SPRING_DATASOURCE_USERNAME: root
        SPRING_DATASOURCE_PASSWORD: root
        SPRING_DATA_REDIS_HOST: redis
        SPRING_DATA_REDIS_PORT: "6379"
        JWT_SECRET: dev-secret-key
    - name: singularity-stock
      base-port: 8082
      port-step: 2
      min-instances: 1
      max-instances: 5
      cpu-scale-up-threshold: 0.70
      memory-scale-up-threshold: 0.80
      cpu-scale-down-threshold: 0.20
      memory-scale-down-threshold: 0.30
      scale-down-consecutive-periods: 3
      image: maven:3.9.9-eclipse-temurin-21
      env:
        SPRING_CLOUD_NACOS_SERVER_ADDR: nacos:8848
        SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/singularity_stock?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
        SPRING_DATASOURCE_USERNAME: root
        SPRING_DATASOURCE_PASSWORD: root
        SPRING_DATA_REDIS_HOST: redis
        SPRING_DATA_REDIS_PORT: "6379"
        ROCKETMQ_NAME_SERVER: rmq-namesrv:9876
        ROCKETMQ_CONSUMER_GROUP: stock-consumer-group
    - name: singularity-product
      base-port: 8087
      port-step: 1
      min-instances: 1
      max-instances: 5
      cpu-scale-up-threshold: 0.70
      memory-scale-up-threshold: 0.80
      cpu-scale-down-threshold: 0.20
      memory-scale-down-threshold: 0.30
      scale-down-consecutive-periods: 3
      image: maven:3.9.9-eclipse-temurin-21
      env:
        SPRING_CLOUD_NACOS_SERVER_ADDR: nacos:8848
        SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/singularity_product?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
        SPRING_DATASOURCE_USERNAME: root
        SPRING_DATASOURCE_PASSWORD: root
        SPRING_DATA_REDIS_HOST: redis
        SPRING_DATA_REDIS_PORT: "6379"
```

- [x] **Step 2: Commit**

```bash
mvn clean package -DskipTests
```

Expected: BUILD SUCCESS

- [x] **Step 1: 全量构建**

常见错误：
- `PrometheusTextParser` 中 `extractByLabelFilter` 方法签名不匹配 — 检查 `MetricsScraper` 中的调用是否正确。
- `ServiceConfig` 中旧字段 `qpsScaleUpThreshold` 被其他类引用 — 搜索并替换引用。

修复后重新运行 `mvn clean package -DskipTests`。

- [x] **Step 2: 如构建失败，修复编译错误**

```bash
git add -A
git commit -m "fix(scaler): resolve compilation errors after resource-based refactoring"
```

---

## Task 11: 集成测试

**前置条件**：Docker Compose 环境已启动（MySQL / Redis / Nacos / RocketMQ），至少一个业务服务（如 order）已启动。

- [x] **Step 3: Commit（如做过修复）**

```bash
java -jar singularity-scaler/target/singularity-scaler-1.0-SNAPSHOT.jar
# 或在 docker-compose 中启动 scaler 容器
```

- [x] **Step 1: 启动 scaler 服务**

```bash
curl -s http://localhost:9090/api/scaler/status | jq .
```

Expected: JSON 数组中包含 `avgCpuUsage` 和 `avgMemoryUsage`，值在 `0.0 ~ 1.0` 之间。

- [x] **Step 2: 验证 /status 返回资源利用率字段**

```bash
curl -X POST http://localhost:9090/api/scaler/scale \
  -H "Content-Type: application/json" \
  -d '{"service":"singularity-order","action":"SCALE_UP"}'
docker ps --format "{{.Names}}" | grep singularity-order
```

Expected: 返回 `singularity-order-1`，Nacos 控制台约 30s 后可见新实例。

- [x] **Step 3: 手动扩容验证**

```bash
curl -X POST http://localhost:9090/api/scaler/scale \
  -H "Content-Type: application/json" \
  -d '{"service":"singularity-order","action":"SCALE_DOWN"}'
docker ps --format "{{.Names}}" | grep singularity-order
```

Expected: 只剩 `singularity-order-0`。

- [x] **Step 4: 手动缩容验证**

使用 wrk 或连续发送请求给 order 服务：

```bash
# 持续对 order 服务施压（通过 gateway 或直接访问 order 实例）
while true; do curl -s http://localhost:8081/actuator/health > /dev/null; done &
```

观察 scaler 日志：
- 应输出 `Scraped for singularity-order: avgQps=..., avgCpu=..., avgMem=...`
- 当 `avgCpu >= 0.70` 或 `avgMem >= 0.80` 时，应在 15s 内触发 `SCALE_UP`。

停止压测后，观察 scaler 日志：
- 连续 3 个周期 CPU 和内存都低于缩容阈值后，触发 `SCALE_DOWN`。
- 冷却期 120s 内，即使再次压测也不会重复扩容。

- [x] **Step 5: 压测触发自动扩容**

---

## Self-Review Checklist

- [x] **Step 6: Commit（如测试中发现并修复了问题）**
- [x] 滑动窗口按服务聚合 — Task 3 MetricHistory
- [x] 多实例平均（全部健康实例采集） — Task 4 MetricsScraper
- [x] CPU / 内存百分比指标 — Task 2 PrometheusTextParser + Task 4 MetricsScraper
- [x] 扩容即时触发，缩容连续 3 周期 — Task 6 PolicyEvaluator
- [x] 四个服务独立监控、独立伸缩 — Task 4/8 中 serviceName 为 key 的隔离设计
- [x] /status 返回 avgCpuUsage / avgMemoryUsage — Task 7 ServiceState + Task 8 ScalingService

**2. Placeholder scan:**
- [x] 无 TBD / TODO
- [x] 每个步骤含完整代码
- [x] 每个步骤含精确文件路径

**3. Type consistency:**
- [x] `ResourceMetrics` 字段名（qps / cpuUsage / memoryUsage）在所有 task 中一致
- [x] `MetricHistory.allRecentBelow` 签名在所有调用处一致
- [x] `ServiceConfig` 新字段名（cpuScaleUpThreshold 等）在 PolicyEvaluator 和 application.yml 中一致
