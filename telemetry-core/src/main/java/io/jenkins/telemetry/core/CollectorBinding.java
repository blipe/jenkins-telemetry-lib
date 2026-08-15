package io.jenkins.telemetry.core;

import io.jenkins.telemetry.api.Signal;
import io.jenkins.telemetry.api.TelemetryCollector;

import java.util.Set;

record CollectorBinding(
    CollectorDefinition definition,
    TelemetryCollector collector,
    Set<Signal> signals
) {
}
