package io.jenkins.telemetry.api;

import java.io.Serializable;

public record SpanEventData(
    long timeUnixNano,
    String name,
    Attributes attributes
) implements Serializable {
    public SpanEventData {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Event name must not be blank");
        }
        attributes = attributes == null ? Attributes.empty() : attributes;
    }
}
