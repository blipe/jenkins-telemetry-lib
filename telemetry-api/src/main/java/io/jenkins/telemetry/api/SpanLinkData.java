package io.jenkins.telemetry.api;

import java.io.Serializable;

/**
 * A causal relationship to a span in another trace. Delivery/promotion pipelines use links rather
 * than parent-child relationships because the build trace normally completed before deployment.
 */
public record SpanLinkData(
    String traceId,
    String spanId,
    Attributes attributes
) implements Serializable {
    public SpanLinkData {
        if (traceId == null || !traceId.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("traceId must be 32 lowercase hexadecimal characters");
        }
        if (spanId == null || !spanId.matches("[0-9a-f]{16}")) {
            throw new IllegalArgumentException("spanId must be 16 lowercase hexadecimal characters");
        }
        attributes = attributes == null ? Attributes.empty() : attributes;
    }
}
