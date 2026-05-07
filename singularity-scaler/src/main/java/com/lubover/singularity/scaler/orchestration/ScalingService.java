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
