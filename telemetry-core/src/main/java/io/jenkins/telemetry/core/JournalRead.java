package io.jenkins.telemetry.core;

import io.jenkins.telemetry.api.JournalEntry;

import java.util.List;

record JournalRead(List<JournalEntry> entries, long nextSequence, long nextOffset) {
    JournalRead {
        entries = List.copyOf(entries);
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }
}
