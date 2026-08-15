package io.jenkins.telemetry.api;

import java.io.Serializable;
import java.util.Map;

public record TelemetryEnvelope(
    String schema,
    long sequence,
    Signal signal,
    long observedTimeUnixNano,
    Map<String, Object> resource,
    Map<String, Object> payload
) implements Serializable {
    public TelemetryEnvelope {
        schema = schema == null ? "jenkins-telemetry/v1" : schema;
        signal = signal == null ? Signal.LOGS : signal;
        resource = resource == null ? Map.of() : Map.copyOf(resource);
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public TelemetryEnvelope withSequence(long value) {
        return new TelemetryEnvelope(schema, value, signal, observedTimeUnixNano, resource, payload);
    }
}
