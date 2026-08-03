package io.jenkins.telemetry.collector.otlp;

import io.jenkins.telemetry.api.TelemetryCollector;
import io.jenkins.telemetry.api.TelemetryCollectorFactory;

import java.util.Map;

public final class OtlpJsonTelemetryCollectorFactory implements TelemetryCollectorFactory {
    @Override
    public String type() {
        return "otlp-http-json";
    }

    @Override
    public TelemetryCollector create(String id, Map<String, String> configuration) {
        return new OtlpJsonTelemetryCollector(id, configuration);
    }
}
