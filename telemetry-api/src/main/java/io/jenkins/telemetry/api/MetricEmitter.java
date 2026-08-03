package io.jenkins.telemetry.api;

public interface MetricEmitter {
    Counter counter(String name);

    Counter counter(String name, String unit, String description);

    Histogram histogram(String name);

    Histogram histogram(String name, String unit, String description);

    Gauge gauge(String name);

    Gauge gauge(String name, String unit, String description);
}
