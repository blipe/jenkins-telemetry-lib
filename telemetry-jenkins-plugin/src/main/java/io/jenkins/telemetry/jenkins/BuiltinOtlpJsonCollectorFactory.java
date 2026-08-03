package io.jenkins.telemetry.jenkins;

import hudson.Extension;
import io.jenkins.telemetry.api.TelemetryCollector;
import io.jenkins.telemetry.collector.otlp.OtlpJsonTelemetryCollectorFactory;
import java.util.Map;

@Extension
public final class BuiltinOtlpJsonCollectorFactory extends JenkinsTelemetryCollectorFactory {
    private final OtlpJsonTelemetryCollectorFactory delegate = new OtlpJsonTelemetryCollectorFactory();
    @Override public String type() { return delegate.type(); }
    @Override public TelemetryCollector create(String id, Map<String, String> configuration) { return delegate.create(id, configuration); }
}
