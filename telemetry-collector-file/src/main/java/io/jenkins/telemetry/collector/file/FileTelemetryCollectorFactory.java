package io.jenkins.telemetry.collector.file;

import io.jenkins.telemetry.api.TelemetryCollector;
import io.jenkins.telemetry.api.TelemetryCollectorFactory;

import java.util.Map;

public final class FileTelemetryCollectorFactory implements TelemetryCollectorFactory {
    @Override
    public String type() {
        return "file";
    }

    @Override
    public TelemetryCollector create(String id, Map<String, String> configuration) {
        return new FileTelemetryCollector(id, configuration);
    }
}
