package io.jenkins.telemetry.collector.file;

import io.jenkins.telemetry.api.*;
import io.jenkins.telemetry.core.ConfigurationResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class FileTelemetryCollector implements TelemetryCollector {
    private final String id;
    private final Map<String, String> configuration;
    private Path directory;
    private boolean forceWrites;

    public FileTelemetryCollector(String id, Map<String, String> configuration) {
        this.id = id;
        this.configuration = Map.copyOf(configuration);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public CollectorCapabilities capabilities() {
        return new CollectorCapabilities(EnumSet.allOf(Signal.class), false, false, 2048, 8 * 1024 * 1024);
    }

    @Override
    public void start(CollectorContext context) throws IOException {
        String configured = configuration.get("directory");
        directory = configured == null || configured.isBlank()
            ? context.workDirectory().resolve("export")
            : Path.of(ConfigurationResolver.resolve(configured));
        forceWrites = Boolean.parseBoolean(configuration.getOrDefault("forceWrites", "false"));
        Files.createDirectories(directory);
    }

    @Override
    public CompletionStage<ExportResult> export(ExportBatch batch) {
        try {
            Path target = directory.resolve(batch.signal().wireName() + ".ndjson");
            StringBuilder output = new StringBuilder();
            for (JournalEntry entry : batch.entries()) {
                output.append(entry.encoded()).append('\n');
            }
            byte[] bytes = output.toString().getBytes(StandardCharsets.UTF_8);
            try (var channel = java.nio.channels.FileChannel.open(target,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                channel.write(java.nio.ByteBuffer.wrap(bytes));
                if (forceWrites) channel.force(false);
            }
            return CompletableFuture.completedFuture(ExportResult.success(batch.entries().size()));
        } catch (IOException e) {
            return CompletableFuture.completedFuture(ExportResult.retry(e.toString()));
        }
    }
}
