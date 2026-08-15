package io.jenkins.telemetry.jenkins;

import hudson.ExtensionPoint;
import io.jenkins.telemetry.api.TelemetryCollectorFactory;

public abstract class JenkinsTelemetryCollectorFactory implements TelemetryCollectorFactory, ExtensionPoint {
}
