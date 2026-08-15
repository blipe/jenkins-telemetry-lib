package io.jenkins.telemetry.collector.http;

import io.jenkins.telemetry.api.*;
import io.jenkins.telemetry.core.ConfigurationResolver;
import io.jenkins.telemetry.core.HttpTransport;
import io.jenkins.telemetry.core.MiniJson;
import io.jenkins.telemetry.core.TelemetryMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class HttpJsonTelemetryCollector implements TelemetryCollector {
    private final String id;
    private final Map<String, String> configuration;
    private HttpTransport transport;

    public HttpJsonTelemetryCollector(String id, Map<String, String> configuration) {
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
            URI endpoint = endpoint(batch.signal());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("schema", "jenkins-telemetry-batch/v1");
            body.put("collectorId", id);
            body.put("signal", batch.signal().name());
            List<Map<String, Object>> entries = new ArrayList<>();
            for (JournalEntry entry : batch.entries()) {
                entries.add(TelemetryMapper.envelopeToMap(entry.envelope()));
            }
            body.put("entries", entries);
            HttpTransport.Response response = transport.post(endpoint, "application/json",
                MiniJson.write(body).getBytes(StandardCharsets.UTF_8));
            return CompletableFuture.completedFuture(result(response, batch.entries().size()));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(ExportResult.retry(e.toString()));
        }
    }

    private URI endpoint(Signal signal) {
        String specific = configuration.get(signal.wireName() + "Endpoint");
        if (specific != null && !specific.isBlank()) {
            return URI.create(ConfigurationResolver.resolve(specific));
        }
        String base = ConfigurationResolver.resolve(required("endpoint"));
        String suffix = configuration.getOrDefault(signal.wireName() + "Path",
            "/" + signal.wireName());
        if (base.endsWith("/") && suffix.startsWith("/")) suffix = suffix.substring(1);
        if (!base.endsWith("/") && !suffix.startsWith("/")) suffix = "/" + suffix;
        return URI.create(base + suffix);
    }

    private String required(String key) {
        String value = configuration.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing HTTP JSON collector setting: " + key);
        }
        return value;
    }

    private static ExportResult result(HttpTransport.Response response, int count) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) return ExportResult.success(count);
        String diagnostic = "HTTP " + status + ": " + truncate(response.bodyUtf8(), 1024);
        if (status == 408 || status == 425 || status == 429 || status >= 500) {
            return ExportResult.retry(diagnostic);
        }
        return ExportResult.permanentFailure(count, diagnostic);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
