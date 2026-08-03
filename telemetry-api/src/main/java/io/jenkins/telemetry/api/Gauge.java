package io.jenkins.telemetry.api;

public interface Gauge {
    void set(double value);

    void set(double value, Attributes attributes);
}
