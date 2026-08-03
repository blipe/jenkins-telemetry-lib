package io.jenkins.telemetry.api;

import java.io.Serializable;
import java.util.List;

public record ExportBatch(
    String collectorId,
    Signal signal,
    List<JournalEntry> entries
) implements Serializable {
    public ExportBatch {
        if (collectorId == null || collectorId.isBlank()) {
            throw new IllegalArgumentException("collectorId must not be blank");
        }
        if (signal == null) {
            throw new IllegalArgumentException("signal must not be null");
        }
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public long firstSequence() {
        return entries.isEmpty() ? 0 : entries.get(0).sequence();
    }

    public long lastSequence() {
        return entries.isEmpty() ? 0 : entries.get(entries.size() - 1).sequence();
    }
}
