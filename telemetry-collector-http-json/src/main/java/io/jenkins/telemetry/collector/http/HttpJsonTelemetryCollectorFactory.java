package io.jenkins.telemetry.collector.http;

import io.jenkins.telemetry.api.TelemetryCollector;
import io.jenkins.telemetry.api.TelemetryCollectorFactory;

import java.util.Map;

public final class HttpJsonTelemetryCollectorFactory implements TelemetryCollectorFactory {
    @Override
    public String type() {
        return "http-json";
    }

    @Override
    public TelemetryCollector create(String id, Map<String, String> configuration) {
        return new HttpJsonTelemetryCollector(id, configuration);
    }
}
