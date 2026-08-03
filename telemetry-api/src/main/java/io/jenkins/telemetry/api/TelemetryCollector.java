package io.jenkins.telemetry.api;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface TelemetryCollector extends AutoCloseable {
    String id();

    CollectorCapabilities capabilities();

    default void start(CollectorContext context) throws Exception {
    }

    CompletionStage<ExportResult> export(ExportBatch batch);

    default CompletionStage<Void> flush() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    default void close() throws Exception {
    }
}
