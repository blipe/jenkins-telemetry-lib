package io.jenkins.telemetry.api;

import java.util.Map;

public interface TelemetryCollectorFactory {
    String type();

    TelemetryCollector create(String id, Map<String, String> configuration);
}
