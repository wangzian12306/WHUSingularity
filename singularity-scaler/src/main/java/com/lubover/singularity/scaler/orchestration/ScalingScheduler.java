package com.lubover.singularity.scaler.orchestration;

import com.lubover.singularity.scaler.config.ScalerProperties;
import com.lubover.singularity.scaler.config.ServiceConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class ScalingScheduler {

    private final ScalingService scalingService;
    private final ScalerProperties scalerProperties;

    @Scheduled(fixedRate = 15000)
    public void poll() {
        if (scalerProperties.getServices() == null || scalerProperties.getServices().isEmpty()) {
            log.debug("No services configured for scaling");
            return;
        }
        log.info("Scaling poll started for {} services", scalerProperties.getServices().size());
        for (ServiceConfig config : scalerProperties.getServices()) {
            try {
                scalingService.evaluateAndScale(config);
            } catch (Exception e) {
                log.error("Failed to evaluate/scale service {}", config.getName(), e);
            }
        }
        log.info("Scaling poll completed");
    }
}
