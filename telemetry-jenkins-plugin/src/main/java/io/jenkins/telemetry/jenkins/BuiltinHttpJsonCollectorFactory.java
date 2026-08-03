package io.jenkins.telemetry.jenkins;

import hudson.Extension;
import io.jenkins.telemetry.api.TelemetryCollector;
import io.jenkins.telemetry.collector.http.HttpJsonTelemetryCollectorFactory;
import java.util.Map;

@Extension
public final class BuiltinHttpJsonCollectorFactory extends JenkinsTelemetryCollectorFactory {
    private final HttpJsonTelemetryCollectorFactory delegate = new HttpJsonTelemetryCollectorFactory();
    @Override public String type() { return delegate.type(); }
    @Override public TelemetryCollector create(String id, Map<String, String> configuration) { return delegate.create(id, configuration); }
}
