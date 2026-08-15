package io.jenkins.telemetry.api;

public interface Histogram {
    void record(double value);

    void record(double value, Attributes attributes);
}
