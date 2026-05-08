package com.lubover.singularity.scaler.metrics;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PrometheusTextParser {

    private static final Pattern METRIC_LINE_PATTERN = Pattern.compile(
            "^(?!#\\s*)(\\w+)(\\{([^}]*)\\})?\\s+([\\d.eE+-]+)$"
    );
    private static final Pattern LABEL_PATTERN = Pattern.compile(
            "(\\w+)=\"([^\"]*)\""
    );

    public Map<String, Map<String, Double>> parse(String text) {
        Map<String, Map<String, Double>> result = new HashMap<>();
        for (String line : text.split("\\r?\\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Matcher m = METRIC_LINE_PATTERN.matcher(line);
            if (!m.matches()) {
                continue;
            }
            String name = m.group(1);
            double value = Double.parseDouble(m.group(4));
            Map<String, String> labels = new HashMap<>();
            String labelPart = m.group(3);
            if (labelPart != null) {
                Matcher lm = LABEL_PATTERN.matcher(labelPart);
                while (lm.find()) {
                    labels.put(lm.group(1), lm.group(2));
                }
            }
            String key = name + labels;
            result.computeIfAbsent(name, k -> new HashMap<>()).put(key, value);
        }
        return result;
    }

    public double extractRate(Map<String, Map<String, Double>> metrics, String metricName) {
        Map<String, Double> values = metrics.get(metricName);
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        return values.values().stream().mapToDouble(Double::doubleValue).sum();
    }

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
}
