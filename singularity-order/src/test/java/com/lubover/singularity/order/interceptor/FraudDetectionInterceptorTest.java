package com.lubover.singularity.order.interceptor;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FraudDetectionInterceptorTest {

    @Test
    void computeFeatureVector_shouldUseConfiguredWindowSecondsForTimeBucket() {
        double[] first = FraudDetectionInterceptor.computeFeatureVector(
                "actor-1", "slot-1", 9_000L, 2, 10);
        double[] second = FraudDetectionInterceptor.computeFeatureVector(
                "actor-1", "slot-1", 9_500L, 2, 10);

        assertTrue(Arrays.equals(first, second));
    }

    @Test
    void recordAndScore_shouldResetRequestAndSlotSwitchStateAfterWindowExpires() {
        FraudDetectionInterceptor.BehaviorWindow window =
                new FraudDetectionInterceptor.BehaviorWindow(0L, 5, 10);

        window.recordAndScore(1_000L, "slot-a", new double[0]);
        window.recordAndScore(2_000L, "slot-b", new double[0]);

        double riskScore = window.recordAndScore(6_001L, "slot-c", new double[0]);

        assertEquals(1, window.requestCount);
        assertEquals(0, window.slotSwitchCount);
        assertEquals("slot-c", window.lastSlotId);
        assertEquals(0.055, riskScore, 0.0001);
    }
}
