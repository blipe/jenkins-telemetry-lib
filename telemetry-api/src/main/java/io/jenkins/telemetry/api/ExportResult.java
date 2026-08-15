package io.jenkins.telemetry.api;

import java.io.Serializable;

/**
 * Result of exporting one ordered journal batch.
 *
 * <p>{@link ExportStatus#PARTIAL} is terminal for the submitted batch: the
 * collector accepted some records and rejected others, so replaying the full
 * batch would duplicate accepted telemetry. The journal therefore advances
 * while retaining the rejection count and diagnostic for health reporting.</p>
 */
public record ExportResult(
    ExportStatus status,
    long accepted,
    long rejected,
    String diagnostic,
    long retryAfterMillis
) implements Serializable {
    public ExportResult {
        status = status == null ? ExportStatus.RETRY : status;
        accepted = Math.max(0, accepted);
        rejected = Math.max(0, rejected);
        diagnostic = diagnostic == null ? "" : diagnostic;
        retryAfterMillis = Math.max(0, retryAfterMillis);
    }

    /** Compatibility constructor for collector implementations compiled from the v1 SPI. */
    public ExportResult(ExportStatus status, long accepted, long rejected, String diagnostic) {
        this(status, accepted, rejected, diagnostic, 0);
    }

    public static ExportResult success(long count) {
        return new ExportResult(ExportStatus.SUCCESS, count, 0, "", 0);
    }

    public static ExportResult partial(long accepted, long rejected, String diagnostic) {
        return new ExportResult(ExportStatus.PARTIAL, accepted, rejected, diagnostic, 0);
    }

    public static ExportResult retry(String diagnostic) {
        return retry(diagnostic, 0);
    }

    public static ExportResult retry(String diagnostic, long retryAfterMillis) {
        return new ExportResult(ExportStatus.RETRY, 0, 0, diagnostic, retryAfterMillis);
    }

    public static ExportResult permanentFailure(long rejected, String diagnostic) {
        return new ExportResult(ExportStatus.PERMANENT_FAILURE, 0, rejected, diagnostic, 0);
    }

    /**
     * Whether this exact batch must not be submitted again.
     */
    public boolean acknowledged() {
        return status == ExportStatus.SUCCESS || status == ExportStatus.PARTIAL;
    }

    public boolean retryable() {
        return status == ExportStatus.RETRY;
    }
}
