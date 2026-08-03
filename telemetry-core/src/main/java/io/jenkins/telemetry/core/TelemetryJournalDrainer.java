package io.jenkins.telemetry.core;

import io.jenkins.telemetry.api.TelemetryCollectorFactory;
import io.jenkins.telemetry.api.TelemetryStatus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;

public final class TelemetryJournalDrainer {
    private TelemetryJournalDrainer() { }

    public static TelemetryStatus drain(Path journalDirectory, TelemetryConfiguration configuration,
        Collection<? extends TelemetryCollectorFactory> factories) throws IOException {
        Objects.requireNonNull(journalDirectory, "journalDirectory");
        Objects.requireNonNull(configuration, "configuration");
        try (TelemetryEngine engine = TelemetryEngine.builder(journalDirectory)
            .configuration(configuration.withAutoExport(false))
            .collectorFactories(factories)
            .build()) {
            engine.flush();
            return engine.status();
        }
    }
}
