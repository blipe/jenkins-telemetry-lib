package io.jenkins.telemetry.core;

import io.jenkins.telemetry.api.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TelemetryMapper {
    private TelemetryMapper() {
    }

    public static TelemetryEnvelope envelope(LogData log, Map<String, Object> resource) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timeUnixNano", Long.toString(log.timeUnixNano()));
        payload.put("observedTimeUnixNano", Long.toString(log.observedTimeUnixNano()));
        payload.put("severityText", log.severity().name());
        payload.put("severityNumber", log.severity().otlpNumber());
        payload.put("body", log.body());
        payload.put("attributes", mergeAttributes(log.attributes(), log.correlation()));
        put(payload, "traceId", log.correlation().traceId());
        put(payload, "spanId", log.correlation().spanId());
        return new TelemetryEnvelope("jenkins-telemetry/v1", 0, Signal.LOGS,
            log.observedTimeUnixNano(), resource, payload);
    }

    public static TelemetryEnvelope envelope(MetricData metric, Map<String, Object> resource) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timeUnixNano", Long.toString(metric.timeUnixNano()));
        payload.put("name", metric.name());
        payload.put("kind", metric.kind().name());
        payload.put("value", metric.value());
        payload.put("unit", metric.unit());
        payload.put("description", metric.description());
        payload.put("attributes", metricAttributes(metric.attributes(), metric.correlation()));
        put(payload, "traceId", metric.correlation().traceId());
        put(payload, "spanId", metric.correlation().spanId());
        return new TelemetryEnvelope("jenkins-telemetry/v1", 0, Signal.METRICS,
            metric.timeUnixNano(), resource, payload);
    }

    public static TelemetryEnvelope envelope(SpanData span, Map<String, Object> resource) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("traceId", span.traceId());
        payload.put("spanId", span.spanId());
        put(payload, "parentSpanId", span.parentSpanId());
        payload.put("name", span.name());
        payload.put("operationKind", span.kind().name());
        payload.put("startTimeUnixNano", Long.toString(span.startTimeUnixNano()));
        payload.put("endTimeUnixNano", Long.toString(span.endTimeUnixNano()));
        payload.put("status", span.status().name());
        payload.put("statusMessage", span.statusMessage());
        payload.put("attributes", mergeAttributes(span.attributes(), span.correlation()));
        List<Map<String, Object>> events = new ArrayList<>();
        for (SpanEventData event : span.events()) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            mapped.put("timeUnixNano", Long.toString(event.timeUnixNano()));
            mapped.put("name", event.name());
            mapped.put("attributes", event.attributes().asMap());
            events.add(mapped);
        }
        payload.put("events", events);
        List<Map<String, Object>> links = new ArrayList<>();
        for (SpanLinkData link : span.links()) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            mapped.put("traceId", link.traceId());
            mapped.put("spanId", link.spanId());
            mapped.put("attributes", link.attributes().asMap());
            links.add(mapped);
        }
        payload.put("links", links);
        return new TelemetryEnvelope("jenkins-telemetry/v1", 0, Signal.TRACES,
            span.endTimeUnixNano(), resource, payload);
    }

    public static Map<String, Object> envelopeToMap(TelemetryEnvelope envelope) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema", envelope.schema());
        result.put("sequence", envelope.sequence());
        result.put("signal", envelope.signal().name());
        result.put("observedTimeUnixNano", Long.toString(envelope.observedTimeUnixNano()));
        result.put("resource", envelope.resource());
        result.put("payload", envelope.payload());
        return result;
    }

    @SuppressWarnings("unchecked")
    public static TelemetryEnvelope envelopeFromMap(Map<String, Object> map) {
        String schema = string(map.get("schema"), "jenkins-telemetry/v1");
        long sequence = number(map.get("sequence"), 0);
        Signal signal = Signal.parse(string(map.get("signal"), "LOGS"));
        long observed = number(map.get("observedTimeUnixNano"), 0);
        Map<String, Object> resource = map.get("resource") instanceof Map<?, ?> raw
            ? new LinkedHashMap<>((Map<String, Object>) raw) : Map.of();
        Map<String, Object> payload = map.get("payload") instanceof Map<?, ?> raw
            ? new LinkedHashMap<>((Map<String, Object>) raw) : Map.of();
        return new TelemetryEnvelope(schema, sequence, signal, observed, resource, payload);
    }

    public static String encode(TelemetryEnvelope envelope) {
        return MiniJson.write(envelopeToMap(envelope));
    }

    public static TelemetryEnvelope decode(String json) {
        return envelopeFromMap(MiniJson.parseObject(json));
    }

    public static Map<String, Object> metricAttributes(Attributes explicit, Correlation correlation) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (explicit != null) result.putAll(explicit.asMap());
        if (correlation != null) {
            put(result, "deployment.environment.name", correlation.deploymentEnvironment());
            put(result, "cicd.pipeline.task.name", correlation.operationName());
        }
        return result;
    }

    public static Map<String, Object> mergeAttributes(Attributes explicit, Correlation correlation) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (explicit != null) result.putAll(explicit.asMap());
        if (correlation != null) {
            put(result, "cicd.pipeline.run.id", correlation.runId());
            put(result, "cicd.pipeline.name", correlation.jobName());
            put(result, "cicd.pipeline.run.number", correlation.buildNumber());
            put(result, "cicd.pipeline.run.url.full", correlation.buildUrl());
            put(result, "service.namespace", correlation.serviceNamespace());
            put(result, "service.name", correlation.serviceName());
            put(result, "service.version", correlation.serviceVersion());
            put(result, "vcs.ref.head.revision", correlation.sourceRevision());
            put(result, "deployment.environment.name", correlation.deploymentEnvironment());
            put(result, "deployment.id", correlation.deploymentId());
            put(result, "cicd.pipeline.task.name", correlation.operationName());
        }
        return result;
    }

    public static long number(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String string) {
            try { return Long.parseLong(string); } catch (NumberFormatException ignored) { }
        }
        return fallback;
    }

    public static double decimal(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String string) {
            try { return Double.parseDouble(string); } catch (NumberFormatException ignored) { }
        }
        return fallback;
    }

    public static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static void put(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }
}
