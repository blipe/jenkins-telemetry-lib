package io.jenkins.telemetry.api;

import java.io.Serializable;
import java.util.List;

public record SpanData(
    String traceId,
    String spanId,
    String parentSpanId,
    String name,
    OperationKind kind,
    long startTimeUnixNano,
    long endTimeUnixNano,
    SpanStatus status,
    String statusMessage,
    Attributes attributes,
    List<SpanEventData> events,
    List<SpanLinkData> links,
    Correlation correlation
) implements Serializable {
    public SpanData {
        if (traceId == null || !traceId.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("traceId must be 32 lowercase hexadecimal characters");
        }
        if (spanId == null || !spanId.matches("[0-9a-f]{16}")) {
            throw new IllegalArgumentException("spanId must be 16 lowercase hexadecimal characters");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Span name must not be blank");
        }
        parentSpanId = parentSpanId == null || parentSpanId.isBlank() ? null : parentSpanId;
        kind = kind == null ? OperationKind.CUSTOM : kind;
        status = status == null ? SpanStatus.UNSET : status;
        statusMessage = statusMessage == null ? "" : statusMessage;
        attributes = attributes == null ? Attributes.empty() : attributes;
        events = events == null ? List.of() : List.copyOf(events);
        links = links == null ? List.of() : List.copyOf(links);
        correlation = correlation == null ? Correlation.empty() : correlation;
    }

    /** Compatibility constructor for embedders that do not use span links. */
    public SpanData(
        String traceId,
        String spanId,
        String parentSpanId,
        String name,
        OperationKind kind,
        long startTimeUnixNano,
        long endTimeUnixNano,
        SpanStatus status,
        String statusMessage,
        Attributes attributes,
        List<SpanEventData> events,
        Correlation correlation
    ) {
        this(traceId, spanId, parentSpanId, name, kind, startTimeUnixNano, endTimeUnixNano,
            status, statusMessage, attributes, events, List.of(), correlation);
    }

    public long durationNanos() {
        return Math.max(0, endTimeUnixNano - startTimeUnixNano);
    }
}
