package com.lubover.singularity.scaler.orchestration;

import com.lubover.singularity.scaler.config.ScalerProperties;
import com.lubover.singularity.scaler.config.ServiceConfig;
import com.lubover.singularity.scaler.discovery.InstanceDiscovery;
import com.lubover.singularity.scaler.docker.DockerCommandExecutor;
import com.lubover.singularity.scaler.docker.DockerContainerInspector;
import com.lubover.singularity.scaler.docker.PortAllocator;
import com.lubover.singularity.scaler.metrics.MetricsScraper;
import com.lubover.singularity.scaler.model.QpsSample;
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
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScalingService {

    private final InstanceDiscovery instanceDiscovery;
    private final MetricsScraper metricsScraper;
    private final DockerContainerInspector containerInspector;
    private final PortAllocator portAllocator;
    private final DockerCommandExecutor dockerCommandExecutor;
    private final PolicyEvaluator policyEvaluator;
    private final CooldownManager cooldownManager;
    private final ScalerProperties scalerProperties;

    /** 缩容去抖：key=serviceName，值为已连续命中「策略建议缩容」的次数 */
    private final Map<String, Integer> consecutiveScaleDownSignals = new ConcurrentHashMap<>();

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

        int currentInstances = dockerInstanceCount(config);
        if (currentInstances == 0) {
            currentInstances = instanceDiscovery.getHealthyInstances(serviceName).size();
        }

        QpsSample qpsSample = metricsScraper.sampleQps(serviceName);
        if (!qpsSample.reliableForScaling()) {
            consecutiveScaleDownSignals.remove(serviceName);
            log.info("Service {}: instances={}, qps=unreliable (display={}), skip scale decision",
                    serviceName, currentInstances, qpsSample.displayQps());
            return new ScaleResult(serviceName, ScaleAction.NONE, "metrics unavailable or baseline reset");
        }
        double qps = qpsSample.policyQps().doubleValue();
        log.info("Service {}: instances={}, qps={}", serviceName, currentInstances, qps);

        ScaleAction action = policyEvaluator.evaluate(
                qps,
                config.getQpsScaleUpThreshold(),
                config.getQpsScaleDownThreshold(),
                currentInstances,
                config.getMinInstances(),
                config.getMaxInstances()
        );

        if (action == ScaleAction.SCALE_UP) {
            consecutiveScaleDownSignals.remove(serviceName);
            if (isComposeScale(config)) {
                int next = currentInstances + 1;
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
            int newIndex = containerInspector.getMaxIndex(serviceName) + 1;
            int port = portAllocator.allocatePort(serviceName, config.getBasePort(), config.getPortStep());
            dockerCommandExecutor.startInstance(config, newIndex, port);
            cooldownManager.recordAction(serviceName);
            return new ScaleResult(serviceName, ScaleAction.SCALE_UP,
                    "scaled up to index " + newIndex + " on port " + port);
        }

        if (action == ScaleAction.SCALE_DOWN) {
            int need = Math.max(1, scalerProperties.getScaleDownMinConsecutivePolls());
            int confirmed = consecutiveScaleDownSignals.merge(serviceName, 1, Integer::sum);
            if (confirmed < need) {
                log.info("Service {}: scale-down debounce {}/{} (qps={}, instances={})",
                        serviceName, confirmed, need, qps, currentInstances);
                return new ScaleResult(serviceName, ScaleAction.NONE,
                        "scale-down pending confirmation " + confirmed + "/" + need);
            }
            consecutiveScaleDownSignals.remove(serviceName);
            if (isComposeScale(config)) {
                int next = currentInstances - 1;
                if (next < config.getMinInstances()) {
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
            int maxIndex = containerInspector.getMaxIndex(serviceName);
            if (maxIndex >= 0) {
                String containerName = serviceName + "-" + maxIndex;
                dockerCommandExecutor.removeInstance(containerName);
                cooldownManager.recordAction(serviceName);
                return new ScaleResult(serviceName, ScaleAction.SCALE_DOWN,
                        "removed container " + containerName);
            }
        }

        consecutiveScaleDownSignals.remove(serviceName);
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
            consecutiveScaleDownSignals.remove(serviceName);
            if (isComposeScale(config)) {
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
            consecutiveScaleDownSignals.remove(serviceName);
            if (isComposeScale(config)) {
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

    private boolean isComposeScale(ServiceConfig config) {
        return config.getScaleMode() != null && "compose".equalsIgnoreCase(config.getScaleMode().trim());
    }

    private String composeServiceName(ServiceConfig config) {
        if (config.getComposeService() != null && !config.getComposeService().isBlank()) {
            return config.getComposeService().trim();
        }
        return config.getName();
    }

    private int dockerInstanceCount(ServiceConfig config) {
        if (isComposeScale(config)) {
            return containerInspector.countComposeReplicas(
                    scalerProperties.getComposeProject(),
                    composeServiceName(config));
        }
        return containerInspector.getContainerNamesForService(config.getName()).size();
    }
}
