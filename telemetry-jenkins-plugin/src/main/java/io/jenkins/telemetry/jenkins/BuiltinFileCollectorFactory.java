package io.jenkins.telemetry.jenkins;

import hudson.Extension;
import io.jenkins.telemetry.api.TelemetryCollector;
import io.jenkins.telemetry.collector.file.FileTelemetryCollectorFactory;
import java.util.Map;

@Extension
public final class BuiltinFileCollectorFactory extends JenkinsTelemetryCollectorFactory {
    private final FileTelemetryCollectorFactory delegate = new FileTelemetryCollectorFactory();
    @Override public String type() { return delegate.type(); }
    @Override public TelemetryCollector create(String id, Map<String, String> configuration) { return delegate.create(id, configuration); }
}
