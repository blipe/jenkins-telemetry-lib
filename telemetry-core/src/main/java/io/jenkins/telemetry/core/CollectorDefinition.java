package io.jenkins.telemetry.core;

import io.jenkins.telemetry.api.Signal;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public record CollectorDefinition(
    String id,
    String type,
    Set<Signal> signals,
    Map<String, String> configuration,
    boolean acknowledgePermanentFailures
) {
    public CollectorDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Collector id must not be blank");
        if (type == null || type.isBlank()) throw new IllegalArgumentException("Collector type must not be blank");
        signals = signals == null || signals.isEmpty()
            ? EnumSet.allOf(Signal.class) : EnumSet.copyOf(signals);
        configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
    }
}
