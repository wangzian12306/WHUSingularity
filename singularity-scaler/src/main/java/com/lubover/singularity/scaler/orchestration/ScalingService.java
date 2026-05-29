package com.lubover.singularity.scaler.orchestration;

import com.lubover.singularity.scaler.config.ScalerProperties;
import com.lubover.singularity.scaler.config.ServiceConfig;
import com.lubover.singularity.scaler.discovery.InstanceDiscovery;
import com.lubover.singularity.scaler.docker.DockerCommandExecutor;
import com.lubover.singularity.scaler.docker.DockerContainerInspector;
import com.lubover.singularity.scaler.metrics.MetricHistory;
import com.lubover.singularity.scaler.metrics.MetricsScraper;
import com.lubover.singularity.scaler.model.QpsSample;
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
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScalingService {

    private final InstanceDiscovery instanceDiscovery;
    private final MetricsScraper metricsScraper;
    private final MetricHistory metricHistory;
    private final DockerContainerInspector containerInspector;
    private final DockerCommandExecutor dockerCommandExecutor;
    private final PolicyEvaluator policyEvaluator;
    private final CooldownManager cooldownManager;
    private final ScalerProperties scalerProperties;

    /** 缩容去抖：key=serviceName，值为已连续命中「策略建议缩容」的次数 */
    private final Map<String, Integer> consecutiveScaleDownSignals = new ConcurrentHashMap<>();

    /** 上一轮成功采样的 docker CPU（0~1），用于暴增检测；与 {@link ScalerProperties#getIntervalSeconds()} 节奏对齐 */
    private final Map<String, DockerCpuSnapshot> lastDockerCpuByService = new ConcurrentHashMap<>();

    private record DockerCpuSnapshot(double cpuUsage, long timeMillis) {
    }

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
        int dockerCount = dockerInstanceCount(config);
        state.setInstanceCount(Math.max(nacosCount, dockerCount));
        state.setCurrentQps(metricsScraper.getDisplayQps(config.getName()));
        metricHistory.latest(config.getName()).ifPresent(metrics -> {
            state.setAvgCpuUsage(metrics.getCpuUsage());
            state.setAvgMemoryUsage(metrics.getMemoryUsage());
        });
        state.setCooldownActive(cooldownManager.isCooldownActive(config.getName(), scalerProperties.getCooldownSeconds()));
        Long lastTime = cooldownManager.getLastActionTime(config.getName());
        state.setLastActionTime(lastTime != null ? lastTime : 0);
        return state;
    }

    public Map<String, Object> getPanelSnapshot() {
        List<ServiceState> services = getAllServiceStates();
        int totalInstances = services.stream().mapToInt(ServiceState::getInstanceCount).sum();
        double totalQps = services.stream().mapToDouble(ServiceState::getCurrentQps).sum();
        double avgCpu = services.isEmpty()
                ? 0.0
                : services.stream().mapToDouble(ServiceState::getAvgCpuUsage).average().orElse(0.0);
        double avgMemory = services.isEmpty()
                ? 0.0
                : services.stream().mapToDouble(ServiceState::getAvgMemoryUsage).average().orElse(0.0);
        long cooldownServices = services.stream().filter(ServiceState::isCooldownActive).count();
        return Map.of(
                "generatedAt", System.currentTimeMillis(),
                "intervalSeconds", scalerProperties.getIntervalSeconds(),
                "cooldownSeconds", scalerProperties.getCooldownSeconds(),
                "totalServices", services.size(),
                "totalInstances", totalInstances,
                "totalQps", totalQps,
                "avgCpuUsage", avgCpu,
                "avgMemoryUsage", avgMemory,
                "cooldownServices", cooldownServices,
                "services", services);
    }

    public ScaleResult evaluateAndScale(ServiceConfig config) {
        String serviceName = config.getName();

        if (cooldownManager.isCooldownActive(serviceName, scalerProperties.getCooldownSeconds())) {
            return new ScaleResult(serviceName, ScaleAction.NONE, "cooldown active");
        }

        int counted = dockerInstanceCount(config);
        if (counted == 0) {
            counted = instanceDiscovery.getHealthyInstances(serviceName).size();
        }
        final int currentInstances = counted;

        String composeProject = scalerProperties.getComposeProject();
        String composeSvc = composeServiceName(config);
        int graceSec = Math.max(0, scalerProperties.getDockerStatsGraceSecondsAfterStart());
        OptionalLong youngest = containerInspector.youngestReplicaUptimeSeconds(composeProject, composeSvc);
        final Optional<ResourceMetrics> dockerRm = sampleDockerIfPastGrace(
                serviceName, composeProject, composeSvc, graceSec, youngest);

        QpsSample qpsSample = metricsScraper.sampleQps(serviceName);
        Optional<ResourceMetrics> jvmRm = Optional.empty();
        try {
            if (qpsSample.reliableForScaling()) {
                double qps = qpsSample.policyQps().doubleValue();
                log.info("Service {}: instances={}, qps={}", serviceName, currentInstances, qps);
                jvmRm = metricsScraper.sampleResourceMetrics(serviceName, qps);
            } else {
                log.info("Service {}: instances={}, qps=unreliable (display={}); scale-up may use docker only",
                        serviceName, currentInstances, qpsSample.displayQps());
            }

            boolean upFromDockerSurge = currentInstances < config.getMaxInstances()
                    && dockerRm.filter(d -> isDockerCpuSurge(serviceName, d)).isPresent();
            boolean upFromDockerAbs = dockerRm
                    .filter(d -> policyEvaluator.shouldScaleUp(d, currentInstances, config.getMaxInstances(), config))
                    .isPresent();
            boolean upFromDocker = upFromDockerAbs || upFromDockerSurge;
            boolean upFromJvm = jvmRm
                    .filter(j -> policyEvaluator.shouldScaleUp(j, currentInstances, config.getMaxInstances(), config))
                    .isPresent();

            if (upFromDocker || upFromJvm) {
                consecutiveScaleDownSignals.remove(serviceName);
                int next = currentInstances + 1;
                if (next > config.getMaxInstances()) {
                    return new ScaleResult(serviceName, ScaleAction.NONE, "max instances reached");
                }
                dockerCommandExecutor.scaleComposeService(
                        scalerProperties.getComposeFile(),
                        scalerProperties.getComposeProject(),
                        composeSvc,
                        next);
                cooldownManager.recordAction(serviceName);
                if (jvmRm.isPresent()) {
                    metricHistory.record(serviceName, jvmRm.get());
                }
                String trigger = buildScaleUpTrigger(upFromDockerAbs, upFromDockerSurge, upFromJvm);
                return new ScaleResult(serviceName, ScaleAction.SCALE_UP,
                        "compose scale " + composeSvc + "=" + next + " (" + trigger + ")");
            }

            if (!qpsSample.reliableForScaling()) {
                consecutiveScaleDownSignals.remove(serviceName);
                return new ScaleResult(serviceName, ScaleAction.NONE,
                        "metrics unavailable or baseline reset; scale-down skipped");
            }

            if (jvmRm.isEmpty()) {
                consecutiveScaleDownSignals.remove(serviceName);
                return new ScaleResult(serviceName, ScaleAction.NONE, "resource metrics unavailable (prometheus scrape)");
            }

            ResourceMetrics currentJvm = jvmRm.get();
            ScaleAction action = policyEvaluator.evaluateScaleDown(
                    metricHistory, serviceName, currentInstances, config.getMinInstances(), config);
            metricHistory.record(serviceName, currentJvm);

            if (action == ScaleAction.SCALE_DOWN) {
                double qps = qpsSample.policyQps().doubleValue();
                double blockThreshold = dockerScaleDownBlockThreshold(config);
                if (blockThreshold > 0 && dockerRm.isPresent()
                        && dockerRm.get().getCpuUsage() >= blockThreshold) {
                    consecutiveScaleDownSignals.remove(serviceName);
                    log.info(
                            "Service {}: scale-down blocked, docker cpu={} >= block threshold {} (qps={}, instances={})",
                            serviceName, dockerRm.get().getCpuUsage(), blockThreshold, qps, currentInstances);
                    return new ScaleResult(serviceName, ScaleAction.NONE,
                            "docker cpu above scale-down block threshold " + blockThreshold);
                }
                int need = Math.max(1, scalerProperties.getScaleDownMinConsecutivePolls());
                int confirmed = consecutiveScaleDownSignals.merge(serviceName, 1, Integer::sum);
                if (confirmed < need) {
                    log.info("Service {}: scale-down debounce {}/{} (qps={}, instances={})",
                            serviceName, confirmed, need, qps, currentInstances);
                    return new ScaleResult(serviceName, ScaleAction.NONE,
                            "scale-down pending confirmation " + confirmed + "/" + need);
                }
                consecutiveScaleDownSignals.remove(serviceName);
                int next = currentInstances - 1;
                if (next < config.getMinInstances()) {
                    return new ScaleResult(serviceName, ScaleAction.NONE, "min instances reached");
                }
                dockerCommandExecutor.scaleComposeService(
                        scalerProperties.getComposeFile(),
                        scalerProperties.getComposeProject(),
                        composeSvc,
                        next);
                cooldownManager.recordAction(serviceName);
                return new ScaleResult(serviceName, ScaleAction.SCALE_DOWN,
                        "compose scale " + composeSvc + "=" + next);
            }

            consecutiveScaleDownSignals.remove(serviceName);
            return new ScaleResult(serviceName, ScaleAction.NONE, "metric within threshold");
        } finally {
            dockerRm.ifPresent(d -> lastDockerCpuByService.put(
                    serviceName, new DockerCpuSnapshot(d.getCpuUsage(), System.currentTimeMillis())));
        }
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
            consecutiveScaleDownSignals.remove(serviceName);
            int current = dockerInstanceCount(config);
            int next = current + 1;
            if (next > config.getMaxInstances()) {
                return new ScaleResult(serviceName, ScaleAction.NONE, "max instances reached");
            }
            dockerCommandExecutor.scaleComposeService(
                    scalerProperties.getComposeFile(),
                    scalerProperties.getComposeProject(),
                    composeServiceName(config),
                    next);
            cooldownManager.recordAction(serviceName);
            return new ScaleResult(serviceName, ScaleAction.SCALE_UP,
                    "compose scale " + composeServiceName(config) + "=" + next);
        }

        if (action == ScaleAction.SCALE_DOWN) {
            consecutiveScaleDownSignals.remove(serviceName);
            int current = dockerInstanceCount(config);
            int next = current - 1;
            if (current <= config.getMinInstances()) {
                return new ScaleResult(serviceName, ScaleAction.NONE, "min instances reached");
            }
            dockerCommandExecutor.scaleComposeService(
                    scalerProperties.getComposeFile(),
                    scalerProperties.getComposeProject(),
                    composeServiceName(config),
                    next);
            cooldownManager.recordAction(serviceName);
            return new ScaleResult(serviceName, ScaleAction.SCALE_DOWN,
                    "compose scale " + composeServiceName(config) + "=" + next);
        }

        return new ScaleResult(serviceName, ScaleAction.NONE, "no action");
    }

    private Optional<ResourceMetrics> sampleDockerIfPastGrace(
            String serviceName,
            String composeProject,
            String composeSvc,
            int graceSec,
            OptionalLong youngest) {
        if (youngest.isPresent() && youngest.getAsLong() >= graceSec) {
            Optional<ResourceMetrics> sampled =
                    containerInspector.sampleComposeServiceDockerStats(composeProject, composeSvc);
            sampled.ifPresent(d -> log.info("Service {}: docker stats cpu={} mem={} (0~1), youngest uptime={}s",
                    serviceName, d.getCpuUsage(), d.getMemoryUsage(), youngest.getAsLong()));
            return sampled;
        }
        if (youngest.isPresent()) {
            log.info("Service {}: skip docker stats for scale-up, youngest replica uptime {}s < grace {}s",
                    serviceName, youngest.getAsLong(), graceSec);
        }
        return Optional.empty();
    }

    /**
     * 与上一轮 scaler 采到的 docker CPU（0~1）比较：短时拉升或 idle→高负载则扩容（仍受副本上限与 cooldown 约束）。
     */
    private boolean isDockerCpuSurge(String serviceName, ResourceMetrics current) {
        DockerCpuSnapshot prev = lastDockerCpuByService.get(serviceName);
        long now = System.currentTimeMillis();
        if (prev == null) {
            return false;
        }
        double gapSec = (now - prev.timeMillis()) / 1000.0;
        int maxGap = Math.max(20, scalerProperties.getDockerCpuSurgeMaxPollGapSeconds());
        if (gapSec > maxGap) {
            return false;
        }
        double delta = current.getCpuUsage() - prev.cpuUsage();
        boolean bigDelta = delta >= scalerProperties.getDockerCpuSurgeDeltaThreshold();
        boolean idleToLoad = prev.cpuUsage() <= scalerProperties.getDockerCpuSurgeLowBaseline()
                && current.getCpuUsage() >= scalerProperties.getDockerCpuSurgeHighWater();
        if (bigDelta || idleToLoad) {
            log.warn(
                    "Service {}: docker CPU surge prevCpu={} currCpu={} delta={} gap={}s bigDelta={} idleToLoad={}",
                    serviceName, prev.cpuUsage(), current.getCpuUsage(), delta, gapSec, bigDelta, idleToLoad);
            return true;
        }
        return false;
    }

    private static String buildScaleUpTrigger(boolean dockerAbs, boolean dockerSurge, boolean jvm) {
        List<String> parts = new ArrayList<>(3);
        if (dockerAbs) {
            parts.add("docker");
        }
        if (dockerSurge) {
            parts.add("surge");
        }
        if (jvm) {
            parts.add("jvm");
        }
        return String.join("+", parts);
    }

    private String composeServiceName(ServiceConfig config) {
        if (config.getComposeService() != null && !config.getComposeService().isBlank()) {
            return config.getComposeService().trim();
        }
        return config.getName();
    }

    private int dockerInstanceCount(ServiceConfig config) {
        return containerInspector.countComposeReplicas(
                scalerProperties.getComposeProject(),
                composeServiceName(config));
    }

    /** 自动缩容保护：docker 聚合 CPU 仍高于该值时不缩容；0 表示关闭。 */
    private double dockerScaleDownBlockThreshold(ServiceConfig config) {
        if (config.getDockerCpuScaleDownBlockThreshold() > 0) {
            return config.getDockerCpuScaleDownBlockThreshold();
        }
        return scalerProperties.getDockerCpuScaleDownBlockThreshold();
    }
}
