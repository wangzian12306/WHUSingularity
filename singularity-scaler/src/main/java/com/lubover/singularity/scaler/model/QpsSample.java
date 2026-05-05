package com.lubover.singularity.scaler.model;

/**
 * {@code policyQps} 为 null 时不应根据 QPS 做扩缩（抓取失败、首帧基线、时间间隔过大等）。
 */
public record QpsSample(double displayQps, Double policyQps) {

    public boolean reliableForScaling() {
        return policyQps != null;
    }

    public static QpsSample unreliable(double displayFallback) {
        return new QpsSample(displayFallback, null);
    }

    public static QpsSample reliable(double qps) {
        return new QpsSample(qps, qps);
    }
}
