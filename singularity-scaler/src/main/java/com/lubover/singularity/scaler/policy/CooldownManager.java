package com.lubover.singularity.scaler.policy;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class CooldownManager {

    private final ConcurrentHashMap<String, Long> lastActionTimes = new ConcurrentHashMap<>();

    public boolean isCooldownActive(String serviceName, int cooldownSeconds) {
        Long lastTime = lastActionTimes.get(serviceName);
        if (lastTime == null) {
            return false;
        }
        return System.currentTimeMillis() - lastTime < cooldownSeconds * 1000L;
    }

    public void recordAction(String serviceName) {
        lastActionTimes.put(serviceName, System.currentTimeMillis());
    }

    public Long getLastActionTime(String serviceName) {
        return lastActionTimes.get(serviceName);
    }
}
