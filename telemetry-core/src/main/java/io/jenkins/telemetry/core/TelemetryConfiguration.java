package io.jenkins.telemetry.core;

import io.jenkins.telemetry.api.Signal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TelemetryConfiguration(
    long maxJournalBytes,
    long journalSegmentBytes,
    boolean forceJournalWrites,
    boolean autoExport,
    int batchRecords,
    int batchBytes,
    Duration exportTimeout,
    Duration retryInitialDelay,
    Duration retryMaxDelay,
    List<String> redactionPatterns,
    String redactionReplacement,
    List<CollectorDefinition> collectors
) {
    public TelemetryConfiguration {
        maxJournalBytes = maxJournalBytes <= 0 ? 512L * 1024 * 1024 : maxJournalBytes;
        journalSegmentBytes = journalSegmentBytes <= 0 ? 8L * 1024 * 1024 : journalSegmentBytes;
        if (journalSegmentBytes > maxJournalBytes) journalSegmentBytes = maxJournalBytes;
        batchRecords = batchRecords <= 0 ? 256 : batchRecords;
        batchBytes = batchBytes <= 0 ? 1_048_576 : batchBytes;
        exportTimeout = positive(exportTimeout, Duration.ofSeconds(30));
        retryInitialDelay = positive(retryInitialDelay, Duration.ofSeconds(1));
        retryMaxDelay = positive(retryMaxDelay, Duration.ofMinutes(1));
        if (retryMaxDelay.compareTo(retryInitialDelay) < 0) retryMaxDelay = retryInitialDelay;
        redactionPatterns = redactionPatterns == null ? List.of() : List.copyOf(redactionPatterns);
        redactionReplacement = redactionReplacement == null ? "$1***" : redactionReplacement;
        collectors = collectors == null ? List.of() : List.copyOf(collectors);
    }

    public TelemetryConfiguration(
        long maxJournalBytes,
        boolean forceJournalWrites,
        int batchRecords,
        int batchBytes,
        Duration exportTimeout,
        List<String> redactionPatterns,
        String redactionReplacement,
        List<CollectorDefinition> collectors
    ) {
        this(maxJournalBytes, 8L * 1024 * 1024, forceJournalWrites, false,
            batchRecords, batchBytes, exportTimeout, Duration.ofSeconds(1), Duration.ofMinutes(1),
            redactionPatterns, redactionReplacement, collectors);
    }

    public static TelemetryConfiguration journalOnly() {
        return new TelemetryConfiguration(512L * 1024 * 1024, 8L * 1024 * 1024,
            false, false, 256, 1_048_576, Duration.ofSeconds(30),
            Duration.ofSeconds(1), Duration.ofMinutes(1), List.of(), "$1***", List.of());
    }

    public TelemetryConfiguration withAutoExport(boolean value) {
        return new TelemetryConfiguration(maxJournalBytes, journalSegmentBytes, forceJournalWrites,
            value, batchRecords, batchBytes, exportTimeout, retryInitialDelay, retryMaxDelay,
            redactionPatterns, redactionReplacement, collectors);
    }

    public static TelemetryConfiguration fromJson(Path path) throws IOException {
        if (path == null || !Files.exists(path)) return journalOnly();
        return fromJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    public static TelemetryConfiguration fromJson(String json) {
        Map<String, Object> root = MiniJson.parseObject(json);
        Map<String, Object> journal = root.get("journal") instanceof Map<?, ?> value
            ? (Map<String, Object>) value : Map.of();
        Map<String, Object> export = root.get("export") instanceof Map<?, ?> value
            ? (Map<String, Object>) value : Map.of();
        Map<String, Object> redaction = root.get("redaction") instanceof Map<?, ?> value
            ? (Map<String, Object>) value : Map.of();

        List<String> patterns = new ArrayList<>();
        Object rawPatterns = redaction.get("patterns");
        if (rawPatterns instanceof Iterable<?> values) {
            values.forEach(value -> patterns.add(String.valueOf(value)));
        }

        List<CollectorDefinition> collectors = new ArrayList<>();
        Object rawCollectors = root.get("collectors");
        if (rawCollectors instanceof Iterable<?> values) {
            for (Object value : values) {
                if (!(value instanceof Map<?, ?> raw)) continue;
                Map<String, Object> item = (Map<String, Object>) raw;
                String id = requiredString(item, "id");
                String type = requiredString(item, "type");
                SetBuilder signals = new SetBuilder();
                Object rawSignals = item.get("signals");
                if (rawSignals instanceof Iterable<?> list) {
                    list.forEach(signal -> signals.add(String.valueOf(signal)));
                }
                Map<String, String> config = new LinkedHashMap<>();
                if (item.get("config") instanceof Map<?, ?> configMap) {
                    configMap.forEach((key, configValue) ->
                        config.put(String.valueOf(key), String.valueOf(configValue)));
                }
                boolean acknowledgePermanent = bool(item.get("acknowledgePermanentFailures"), false);
                collectors.add(new CollectorDefinition(id, type, signals.build(), config, acknowledgePermanent));
            }
        }

        return new TelemetryConfiguration(
            number(journal.get("maxBytes"), 512L * 1024 * 1024),
            number(journal.get("segmentBytes"), 8L * 1024 * 1024),
            bool(journal.get("forceWrites"), false),
            bool(export.get("enabled"), true),
            (int) number(export.get("batchRecords"), 256),
            (int) number(export.get("batchBytes"), 1_048_576),
            Duration.ofMillis(number(export.get("timeoutMillis"), 30_000)),
            Duration.ofMillis(number(export.get("retryInitialMillis"), 1_000)),
            Duration.ofMillis(number(export.get("retryMaxMillis"), 60_000)),
            patterns,
            String.valueOf(redaction.getOrDefault("replacement", "$1***")),
            collectors
        );
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }

    private static String requiredString(Map<String, Object> source, String key) {
        Object raw = source.get(key);
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new IllegalArgumentException("Missing collector field: " + key);
        }
        return String.valueOf(raw);
    }

    private static long number(Object value, long fallback) {
        return TelemetryMapper.number(value, fallback);
    }

    private static boolean bool(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static final class SetBuilder {
        private final EnumSet<Signal> values = EnumSet.noneOf(Signal.class);
        void add(String value) { values.add(Signal.parse(value)); }
        EnumSet<Signal> build() {
            return values.isEmpty() ? EnumSet.allOf(Signal.class) : EnumSet.copyOf(values);
        }
    }
}
