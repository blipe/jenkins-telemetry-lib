package io.jenkins.telemetry.api;

import java.io.Serializable;

/** Immutable origin of an artifact/release produced by a Jenkins run. */
public record BuildProvenance(
    String runId,
    String jobName,
    String buildNumber,
    String buildUrl,
    String traceId,
    String spanId,
    Attributes attributes
) implements Serializable {
    public BuildProvenance {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        if (jobName == null || jobName.isBlank()) {
            throw new IllegalArgumentException("jobName must not be blank");
        }
        if (traceId == null || !traceId.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("traceId must be 32 lowercase hexadecimal characters");
        }
        if (spanId == null || !spanId.matches("[0-9a-f]{16}")) {
            throw new IllegalArgumentException("spanId must be 16 lowercase hexadecimal characters");
        }
        attributes = attributes == null ? Attributes.empty() : attributes;
    }

    public SpanLinkData asSpanLink() {
        return new SpanLinkData(traceId, spanId, Attributes.builder()
            .put("delivery.link.type", "build")
            .put("delivery.build.run.id", runId)
            .put("delivery.build.job.name", jobName)
            .put("delivery.build.number", buildNumber)
            .put("delivery.build.url", buildUrl)
            .putAll(attributes)
            .build());
    }
}
