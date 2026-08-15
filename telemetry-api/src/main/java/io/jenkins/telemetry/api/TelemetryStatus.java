package io.jenkins.telemetry.api;

import java.io.Serializable;
import java.util.Map;

public record TelemetryStatus(
    long journalBytes,
    long logRecords,
    long metricRecords,
    long traceRecords,
    long droppedRecords,
    Map<String, Long> collectorLag
) implements Serializable {
    public TelemetryStatus {
        collectorLag = collectorLag == null ? Map.of() : Map.copyOf(collectorLag);
    }
}
