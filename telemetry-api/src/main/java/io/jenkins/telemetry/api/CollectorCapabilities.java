package io.jenkins.telemetry.api;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.Set;

public record CollectorCapabilities(
    Set<Signal> signals,
    boolean metricExemplars,
    boolean compression,
    int preferredBatchRecords,
    int preferredBatchBytes
) implements Serializable {
    public CollectorCapabilities {
        signals = signals == null || signals.isEmpty()
            ? EnumSet.noneOf(Signal.class)
            : EnumSet.copyOf(signals);
        preferredBatchRecords = preferredBatchRecords <= 0 ? 256 : preferredBatchRecords;
        preferredBatchBytes = preferredBatchBytes <= 0 ? 1_048_576 : preferredBatchBytes;
    }

    public static CollectorCapabilities all() {
        return new CollectorCapabilities(EnumSet.allOf(Signal.class), true, true, 256, 1_048_576);
    }

    public boolean supports(Signal signal) {
        return signals.contains(signal);
    }
}
