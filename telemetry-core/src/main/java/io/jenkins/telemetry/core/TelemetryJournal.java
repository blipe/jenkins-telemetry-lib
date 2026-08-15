package io.jenkins.telemetry.core;

import io.jenkins.telemetry.api.Signal;
import io.jenkins.telemetry.api.TelemetryEnvelope;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

interface TelemetryJournal extends Closeable {
    long append(TelemetryEnvelope envelope) throws IOException;
    JournalRead read(String collectorId, Signal signal, int maxRecords, int maxBytes) throws IOException;
    void acknowledge(String collectorId, Signal signal, long sequence, long offset) throws IOException;
    void configureCollectors(Map<Signal, Set<String>> collectorsBySignal) throws IOException;
    long latestSequence(Signal signal);
    long acknowledgedSequence(String collectorId, Signal signal);
    long sizeBytes();
    Map<Signal, Long> recordCounts();
    @Override void close() throws IOException;
}
