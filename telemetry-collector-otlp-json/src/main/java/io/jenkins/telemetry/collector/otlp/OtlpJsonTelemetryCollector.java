package io.jenkins.telemetry.collector.otlp;

import io.jenkins.telemetry.api.*;
import io.jenkins.telemetry.core.ConfigurationResolver;
import io.jenkins.telemetry.core.HttpTransport;

import java.net.URI;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class OtlpJsonTelemetryCollector implements TelemetryCollector {
    private final String id;
    private final Map<String, String> configuration;
    private HttpTransport transport;

    public OtlpJsonTelemetryCollector(String id, Map<String, String> configuration) {
        this.id = id;
        this.configuration = Map.copyOf(configuration);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public CollectorCapabilities capabilities() {
        return new CollectorCapabilities(EnumSet.allOf(Signal.class), true, true, 512, 2 * 1024 * 1024);
    }

    @Override
    public void start(CollectorContext context) {
        transport = HttpTransport.fromConfiguration(configuration);
    }

    @Override
    public CompletionStage<ExportResult> export(ExportBatch batch) {
        try {
            HttpTransport.Response response = transport.post(endpoint(batch.signal()),
                "application/json", OtlpJsonEncoder.encode(batch));
            return CompletableFuture.completedFuture(result(response, batch.signal(), batch.entries().size()));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(ExportResult.retry(e.toString()));
        }
    }

    private URI endpoint(Signal signal) {
        String specific = configuration.get(signal.wireName() + "Endpoint");
        if (specific != null && !specific.isBlank()) {
            return URI.create(ConfigurationResolver.resolve(specific));
        }
        String base = configuration.get("endpoint");
        if (base == null || base.isBlank()) {
            throw new IllegalArgumentException("OTLP collector requires endpoint or per-signal endpoints");
        }
        base = ConfigurationResolver.resolve(base);
        String suffix = "/v1/" + signal.wireName();
        if (base.endsWith("/")) suffix = suffix.substring(1);
        return URI.create(base + suffix);
    }

    private static ExportResult result(HttpTransport.Response response, Signal signal, int count) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return successfulResult(response.bodyUtf8(), signal, count);
        }
        String diagnostic = "OTLP/HTTP " + status + ": " + truncate(response.bodyUtf8(), 1024);
        if (status == 408 || status == 425 || status == 429 || status >= 500) {
            return ExportResult.retry(diagnostic, retryAfterMillis(response));
        }
        return ExportResult.permanentFailure(count, diagnostic);
    }

    private static ExportResult successfulResult(String responseBody, Signal signal, int count) {
        if (responseBody == null || responseBody.isBlank()) return ExportResult.success(count);
        try {
            Object partial = io.jenkins.telemetry.core.MiniJson.parseObject(responseBody)
                .get("partialSuccess");
            if (!(partial instanceof Map<?, ?> values)) return ExportResult.success(count);

            String rejectedField = switch (signal) {
                case LOGS -> "rejectedLogRecords";
                case METRICS -> "rejectedDataPoints";
                case TRACES -> "rejectedSpans";
            };
            long rejected = unsignedLong(values.get(rejectedField));
            String message = values.get("errorMessage") == null
                ? ""
                : String.valueOf(values.get("errorMessage"));
            if (rejected <= 0) return ExportResult.success(count);

            long accepted = Math.max(0, (long) count - rejected);
            return ExportResult.partial(accepted, rejected,
                "OTLP partial success: " + rejectedField + "=" + rejected
                    + (message.isBlank() ? "" : ", " + truncate(message, 1024)));
        } catch (RuntimeException malformedResponse) {
            return ExportResult.partial(count, 0,
                "OTLP returned an unreadable success response: "
                    + truncate(malformedResponse.toString(), 1024));
        }
    }

    private static long unsignedLong(Object value) {
        if (value == null) return 0;
        if (value instanceof Number number) return Math.max(0, number.longValue());
        try {
            return Math.max(0, Long.parseLong(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static long retryAfterMillis(HttpTransport.Response response) {
        String value = response.firstHeader("Retry-After");
        if (value == null || value.isBlank()) return 0;
        try {
            return Math.max(0, Long.parseLong(value.trim()) * 1000L);
        } catch (NumberFormatException ignored) {
            try {
                long millis = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli() - System.currentTimeMillis();
                return Math.max(0, millis);
            } catch (DateTimeParseException ignoredDate) {
                return 0;
            }
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
