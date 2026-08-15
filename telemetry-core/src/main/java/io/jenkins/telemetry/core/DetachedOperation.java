package io.jenkins.telemetry.core;

import io.jenkins.telemetry.api.Attributes;
import io.jenkins.telemetry.api.OperationKind;
import io.jenkins.telemetry.api.SpanEventData;
import io.jenkins.telemetry.api.SpanLinkData;

import java.io.Serializable;
import java.util.List;

public record DetachedOperation(
    String operationId,
    String traceId,
    String spanId,
    String parentSpanId,
    String name,
    OperationKind kind,
    long startTimeUnixNano,
    Attributes attributes,
    List<SpanEventData> events,
    List<SpanLinkData> links
) implements Serializable {
    public DetachedOperation {
        attributes = attributes == null ? Attributes.empty() : attributes;
        events = events == null ? List.of() : List.copyOf(events);
        links = links == null ? List.of() : List.copyOf(links);
    }

    /** Compatibility constructor for operations without links. */
    public DetachedOperation(
        String operationId,
        String traceId,
        String spanId,
        String parentSpanId,
        String name,
        OperationKind kind,
        long startTimeUnixNano,
        Attributes attributes,
        List<SpanEventData> events
    ) {
        this(operationId, traceId, spanId, parentSpanId, name, kind, startTimeUnixNano,
            attributes, events, List.of());
    }

    public DetachedOperation withEvent(SpanEventData event) {
        java.util.ArrayList<SpanEventData> updated = new java.util.ArrayList<>(events);
        updated.add(event);
        return new DetachedOperation(operationId, traceId, spanId, parentSpanId, name, kind,
            startTimeUnixNano, attributes, updated, links);
    }

    public DetachedOperation withAttribute(String key, Object value) {
        return new DetachedOperation(operationId, traceId, spanId, parentSpanId, name, kind,
            startTimeUnixNano, attributes.merge(Attributes.of(key, value)), events, links);
    }

    public DetachedOperation withLink(SpanLinkData link) {
        java.util.ArrayList<SpanLinkData> updated = new java.util.ArrayList<>(links);
        if (!updated.contains(link)) updated.add(link);
        return new DetachedOperation(operationId, traceId, spanId, parentSpanId, name, kind,
            startTimeUnixNano, attributes, events, updated);
    }
}
