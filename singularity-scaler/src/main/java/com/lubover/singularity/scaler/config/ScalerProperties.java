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
