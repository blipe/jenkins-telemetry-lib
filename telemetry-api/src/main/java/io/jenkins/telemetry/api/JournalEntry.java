package io.jenkins.telemetry.api;

import java.io.Serializable;

public record JournalEntry(
    long sequence,
    Signal signal,
    TelemetryEnvelope envelope,
    String encoded
) implements Serializable {
}
