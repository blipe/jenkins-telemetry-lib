package io.jenkins.telemetry.api;

import java.io.Serializable;
import java.util.regex.Pattern;

public record MetricData(
    long timeUnixNano,
    String name,
    MetricKind kind,
    double value,
    String unit,
    String description,
    Attributes attributes,
    Correlation correlation
) implements Serializable {
    private static final Pattern VALID_NAME =
        Pattern.compile("[A-Za-z][A-Za-z0-9_.\\-/]{0,254}");

    public MetricData {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Metric name must not be blank");
        }
        if (!VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                "Metric name must start with a letter and contain only letters, digits, '.', '-', '_', or '/': "
                    + name);
        }
        kind = kind == null ? MetricKind.GAUGE : kind;
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Metric value must be finite: " + value);
        }
        if (kind == MetricKind.COUNTER && value < 0) {
            throw new IllegalArgumentException("Monotonic counter delta must not be negative: " + value);
        }
        unit = unit == null ? "" : unit;
        description = description == null ? "" : description;
        attributes = attributes == null ? Attributes.empty() : attributes;
        correlation = correlation == null ? Correlation.empty() : correlation;
    }
}
