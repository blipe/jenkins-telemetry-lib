package io.jenkins.telemetry.collector.otlp;

import io.jenkins.telemetry.api.ExportBatch;
import io.jenkins.telemetry.api.JournalEntry;
import io.jenkins.telemetry.api.MetricKind;
import io.jenkins.telemetry.api.Signal;
import io.jenkins.telemetry.core.MiniJson;
import io.jenkins.telemetry.core.TelemetryMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OtlpJsonEncoder {
    private OtlpJsonEncoder() {
    }

    static byte[] encode(ExportBatch batch) {
        Map<String, Object> value = switch (batch.signal()) {
            case LOGS -> logs(batch.entries());
            case METRICS -> metrics(batch.entries());
            case TRACES -> traces(batch.entries());
        };
        return MiniJson.write(value).getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, Object> logs(List<JournalEntry> entries) {
        List<Map<String, Object>> groups = new ArrayList<>();
        for (ResourceGroup group : group(entries)) {
            List<Map<String, Object>> records = new ArrayList<>();
            for (JournalEntry entry : group.entries) {
                Map<String, Object> payload = entry.envelope().payload();
                Map<String, Object> record = new LinkedHashMap<>();
                record.put("timeUnixNano", string(payload.get("timeUnixNano"), "0"));
                record.put("observedTimeUnixNano", string(payload.get("observedTimeUnixNano"), "0"));
                record.put("severityNumber", number(payload.get("severityNumber"), 9));
                record.put("severityText", string(payload.get("severityText"), "INFO"));
                record.put("body", anyValue(payload.get("body")));
                record.put("attributes", attributes(asMap(payload.get("attributes"))));
                put(record, "traceId", payload.get("traceId"));
                put(record, "spanId", payload.get("spanId"));
                records.add(record);
            }
            Map<String, Object> scopeLogs = new LinkedHashMap<>();
            scopeLogs.put("scope", scope());
            scopeLogs.put("logRecords", records);
            groups.add(Map.of(
                "resource", resource(group.resource),
                "scopeLogs", List.of(scopeLogs)
            ));
        }
        return Map.of("resourceLogs", groups);
    }

    private static Map<String, Object> metrics(List<JournalEntry> entries) {
        List<Map<String, Object>> groups = new ArrayList<>();
        for (ResourceGroup group : group(entries)) {
            Map<String, MetricAggregate> aggregates = new LinkedHashMap<>();
            for (JournalEntry entry : group.entries) {
                Map<String, Object> payload = entry.envelope().payload();
                String name = string(payload.get("name"), "unnamed");
                String kind = string(payload.get("kind"), MetricKind.GAUGE.name());
                String unit = string(payload.get("unit"), "");
                String description = string(payload.get("description"), "");
                String key = kind + "\u0000" + name + "\u0000" + unit + "\u0000" + description;
                aggregates.computeIfAbsent(key, ignored ->
                    new MetricAggregate(name, kind, unit, description)).entries.add(entry);
            }
            List<Map<String, Object>> metrics = new ArrayList<>();
            for (MetricAggregate aggregate : aggregates.values()) {
                metrics.add(metric(aggregate));
            }
            Map<String, Object> scopeMetrics = new LinkedHashMap<>();
            scopeMetrics.put("scope", scope());
            scopeMetrics.put("metrics", metrics);
            groups.add(Map.of(
                "resource", resource(group.resource),
                "scopeMetrics", List.of(scopeMetrics)
            ));
        }
        return Map.of("resourceMetrics", groups);
    }

    private static Map<String, Object> metric(MetricAggregate aggregate) {
        MetricKind kind = MetricKind.valueOf(aggregate.kind);
        Map<String, MetricPoint> pointsByAttributes = new LinkedHashMap<>();
        for (JournalEntry entry : aggregate.entries) {
            Map<String, Object> payload = entry.envelope().payload();
            Map<String, Object> pointAttributes = new LinkedHashMap<>(asMap(payload.get("attributes")));
            String key = MiniJson.write(pointAttributes);
            MetricPoint point = pointsByAttributes.computeIfAbsent(key,
                ignored -> new MetricPoint(pointAttributes));
            point.accept(payload, kind);
        }

        List<Map<String, Object>> points = new ArrayList<>();
        for (MetricPoint point : pointsByAttributes.values()) {
            Map<String, Object> encoded = new LinkedHashMap<>();
            encoded.put("timeUnixNano", point.timeUnixNano);
            encoded.put("attributes", attributes(point.attributes));
            List<Map<String, Object>> exemplars = point.exemplars();
            if (!exemplars.isEmpty()) encoded.put("exemplars", exemplars);

            switch (kind) {
                case COUNTER -> encoded.put("asDouble", point.sum);
                case GAUGE -> encoded.put("asDouble", point.last);
                case HISTOGRAM -> {
                    encoded.put("count", Long.toString(point.count));
                    encoded.put("sum", point.sum);
                    encoded.put("min", point.min);
                    encoded.put("max", point.max);
                    encoded.put("bucketCounts", List.of(Long.toString(point.count)));
                    encoded.put("explicitBounds", List.of());
                }
            }
            points.add(encoded);
        }

        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("name", aggregate.name);
        if (!aggregate.description.isBlank()) metric.put("description", aggregate.description);
        if (!aggregate.unit.isBlank()) metric.put("unit", aggregate.unit);

        switch (kind) {
            case COUNTER -> metric.put("sum", Map.of(
                "dataPoints", points,
                "aggregationTemporality", 1,
                "isMonotonic", true
            ));
            case GAUGE -> metric.put("gauge", Map.of("dataPoints", points));
            case HISTOGRAM -> metric.put("histogram", Map.of(
                "dataPoints", points,
                "aggregationTemporality", 1
            ));
        }
        return metric;
    }

    private static Map<String, Object> traces(List<JournalEntry> entries) {
        List<Map<String, Object>> groups = new ArrayList<>();
        for (ResourceGroup group : group(entries)) {
            List<Map<String, Object>> spans = new ArrayList<>();
            for (JournalEntry entry : group.entries) {
                Map<String, Object> payload = entry.envelope().payload();
                Map<String, Object> span = new LinkedHashMap<>();
                span.put("traceId", string(payload.get("traceId"), ""));
                span.put("spanId", string(payload.get("spanId"), ""));
                put(span, "parentSpanId", payload.get("parentSpanId"));
                span.put("name", string(payload.get("name"), "unnamed"));
                span.put("kind", 1);
                span.put("startTimeUnixNano", string(payload.get("startTimeUnixNano"), "0"));
                span.put("endTimeUnixNano", string(payload.get("endTimeUnixNano"), "0"));
                Map<String, Object> spanAttributes = new LinkedHashMap<>(asMap(payload.get("attributes")));
                spanAttributes.putIfAbsent("cicd.pipeline.task.type",
                    string(payload.get("operationKind"), "custom").toLowerCase());
                span.put("attributes", attributes(spanAttributes));

                List<Map<String, Object>> events = new ArrayList<>();
                Object rawEvents = payload.get("events");
                if (rawEvents instanceof Iterable<?> iterable) {
                    for (Object raw : iterable) {
                        Map<String, Object> eventValue = asMap(raw);
                        Map<String, Object> event = new LinkedHashMap<>();
                        event.put("timeUnixNano", string(eventValue.get("timeUnixNano"), "0"));
                        event.put("name", string(eventValue.get("name"), "event"));
                        event.put("attributes", attributes(asMap(eventValue.get("attributes"))));
                        events.add(event);
                    }
                }
                if (!events.isEmpty()) span.put("events", events);

                List<Map<String, Object>> links = new ArrayList<>();
                Object rawLinks = payload.get("links");
                if (rawLinks instanceof Iterable<?> iterable) {
                    for (Object raw : iterable) {
                        Map<String, Object> linkValue = asMap(raw);
                        Map<String, Object> link = new LinkedHashMap<>();
                        link.put("traceId", string(linkValue.get("traceId"), ""));
                        link.put("spanId", string(linkValue.get("spanId"), ""));
                        link.put("attributes", attributes(asMap(linkValue.get("attributes"))));
                        links.add(link);
                    }
                }
                if (!links.isEmpty()) span.put("links", links);

                String status = string(payload.get("status"), "UNSET");
                Map<String, Object> statusValue = new LinkedHashMap<>();
                statusValue.put("code", switch (status) {
                    case "OK" -> 1;
                    case "ERROR" -> 2;
                    default -> 0;
                });
                String message = nullable(payload.get("statusMessage"));
                if (message != null) statusValue.put("message", message);
                span.put("status", statusValue);
                spans.add(span);
            }
            Map<String, Object> scopeSpans = new LinkedHashMap<>();
            scopeSpans.put("scope", scope());
            scopeSpans.put("spans", spans);
            groups.add(Map.of(
                "resource", resource(group.resource),
                "scopeSpans", List.of(scopeSpans)
            ));
        }
        return Map.of("resourceSpans", groups);
    }

    private static List<ResourceGroup> group(List<JournalEntry> entries) {
        Map<String, ResourceGroup> groups = new LinkedHashMap<>();
        for (JournalEntry entry : entries) {
            Map<String, Object> resource = entry.envelope().resource();
            String key = MiniJson.write(resource);
            groups.computeIfAbsent(key, ignored -> new ResourceGroup(resource)).entries.add(entry);
        }
        return new ArrayList<>(groups.values());
    }

    private static Map<String, Object> resource(Map<String, Object> attributes) {
        return Map.of("attributes", attributes(attributes));
    }

    private static Map<String, Object> scope() {
        return Map.of("name", "io.jenkins.telemetry", "version", "1.0.0");
    }

    private static List<Map<String, Object>> attributes(Map<String, Object> values) {
        List<Map<String, Object>> result = new ArrayList<>();
        values.forEach((key, value) ->
            result.add(Map.of("key", key, "value", anyValue(value))));
        return result;
    }

    private static Map<String, Object> anyValue(Object value) {
        if (value == null) return Map.of("stringValue", "");
        if (value instanceof Boolean bool) return Map.of("boolValue", bool);
        if (value instanceof Byte || value instanceof Short || value instanceof Integer ||
            value instanceof Long) {
            return Map.of("intValue", String.valueOf(value));
        }
        if (value instanceof Number number) return Map.of("doubleValue", number.doubleValue());
        if (value instanceof Map<?, ?> map) {
            List<Map<String, Object>> pairs = new ArrayList<>();
            map.forEach((key, item) ->
                pairs.add(Map.of("key", String.valueOf(key), "value", anyValue(item))));
            return Map.of("kvlistValue", Map.of("values", pairs));
        }
        if (value instanceof Iterable<?> iterable) {
            List<Map<String, Object>> values = new ArrayList<>();
            iterable.forEach(item -> values.add(anyValue(item)));
            return Map.of("arrayValue", Map.of("values", values));
        }
        if (value.getClass().isArray()) {
            List<Map<String, Object>> values = new ArrayList<>();
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) values.add(anyValue(java.lang.reflect.Array.get(value, i)));
            return Map.of("arrayValue", Map.of("values", values));
        }
        return Map.of("stringValue", String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String string(Object value, String fallback) {
        return TelemetryMapper.string(value, fallback);
    }

    private static Number number(Object value, long fallback) {
        return TelemetryMapper.number(value, fallback);
    }

    private static double decimal(Object value, double fallback) {
        return TelemetryMapper.decimal(value, fallback);
    }

    private static String nullable(Object value) {
        if (value == null) return null;
        String result = String.valueOf(value);
        return result.isBlank() ? null : result;
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        String string = nullable(value);
        if (string != null) target.put(key, string);
    }

    private static final class ResourceGroup {
        private final Map<String, Object> resource;
        private final List<JournalEntry> entries = new ArrayList<>();

        private ResourceGroup(Map<String, Object> resource) {
            this.resource = resource;
        }
    }

    private static final class MetricPoint {
        private final Map<String, Object> attributes;
        private long count;
        private double sum;
        private double last;
        private double min = Double.POSITIVE_INFINITY;
        private double max = Double.NEGATIVE_INFINITY;
        private String timeUnixNano = "0";
        private String traceId;
        private String spanId;

        private MetricPoint(Map<String, Object> attributes) {
            this.attributes = attributes;
        }

        private void accept(Map<String, Object> payload, MetricKind kind) {
            double value = decimal(payload.get("value"), 0);
            count++;
            sum += value;
            last = value;
            min = Math.min(min, value);
            max = Math.max(max, value);
            timeUnixNano = string(payload.get("timeUnixNano"), timeUnixNano);
            String candidateTrace = nullable(payload.get("traceId"));
            String candidateSpan = nullable(payload.get("spanId"));
            if (candidateTrace != null && candidateSpan != null) {
                traceId = candidateTrace;
                spanId = candidateSpan;
            }
            if (kind == MetricKind.GAUGE) {
                sum = value;
                count = 1;
                min = value;
                max = value;
            }
        }

        private List<Map<String, Object>> exemplars() {
            if (traceId == null || spanId == null) return List.of();
            Map<String, Object> exemplar = new LinkedHashMap<>();
            exemplar.put("timeUnixNano", timeUnixNano);
            exemplar.put("asDouble", last);
            exemplar.put("traceId", traceId);
            exemplar.put("spanId", spanId);
            return List.of(exemplar);
        }
    }

    private static final class MetricAggregate {
        private final String name;
        private final String kind;
        private final String unit;
        private final String description;
        private final List<JournalEntry> entries = new ArrayList<>();

        private MetricAggregate(String name, String kind, String unit, String description) {
            this.name = name;
            this.kind = kind;
            this.unit = unit;
            this.description = description;
        }
    }
}
