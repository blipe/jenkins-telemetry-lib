package io.jenkins.telemetry.api;

import java.io.Serializable;
import java.util.List;

/**
 * Portable release/artifact provenance passed from a build pipeline to independent promotion and
 * deployment pipelines.
 */
public record DeliveryManifest(
    String schema,
    long createdTimeUnixNano,
    ReleaseIdentity release,
    List<ArtifactRef> artifacts,
    BuildProvenance build,
    Attributes attributes
) implements Serializable {
    public static final String SCHEMA = "jenkins-delivery-manifest/v1";

    public DeliveryManifest {
        schema = schema == null || schema.isBlank() ? SCHEMA : schema;
        if (!SCHEMA.equals(schema)) {
            throw new IllegalArgumentException("Unsupported delivery manifest schema: " + schema);
        }
        if (createdTimeUnixNano <= 0) {
            throw new IllegalArgumentException("createdTimeUnixNano must be positive");
        }
        if (release == null) {
            throw new IllegalArgumentException("release must not be null");
        }
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        if (artifacts.isEmpty()) {
            throw new IllegalArgumentException("At least one immutable artifact is required");
        }
        if (build == null) {
            throw new IllegalArgumentException("build provenance must not be null");
        }
        attributes = attributes == null ? Attributes.empty() : attributes;
    }
}
