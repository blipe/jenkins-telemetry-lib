package io.jenkins.telemetry.api;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public record ArtifactRef(
    String logicalName,
    String type,
    String repository,
    String name,
    String version,
    String digestAlgorithm,
    String digest,
    String uri,
    Attributes attributes
) implements Serializable {
    public ArtifactRef {
        logicalName = logicalName == null || logicalName.isBlank() ? "primary" : logicalName;
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Artifact type must not be blank");
        }
        if (digest == null || digest.isBlank()) {
            throw new IllegalArgumentException("Artifact digest must not be blank");
        }
        digestAlgorithm = digestAlgorithm == null || digestAlgorithm.isBlank() ? "sha256" : digestAlgorithm;
        attributes = attributes == null ? Attributes.empty() : attributes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ArtifactRef file(String name, String sha256, String uri) {
        return builder().type("file").name(name).digest("sha256", sha256).uri(uri).build();
    }

    public static ArtifactRef maven(String repository, String groupId, String artifactId,
                                    String version, String sha256, String uri) {
        return builder()
            .type("maven")
            .repository(repository)
            .name(groupId + ":" + artifactId)
            .version(version)
            .digest("sha256", sha256)
            .uri(uri)
            .attribute("maven.group_id", groupId)
            .attribute("maven.artifact_id", artifactId)
            .build();
    }

    public static ArtifactRef oci(String repository, String image, String digest) {
        String normalized = digest.startsWith("sha256:") ? digest.substring("sha256:".length()) : digest;
        return builder()
            .type("oci")
            .repository(repository)
            .name(image)
            .digest("sha256", normalized)
            .uri(repository + "/" + image + "@sha256:" + normalized)
            .build();
    }

    public String immutableId() {
        return digestAlgorithm + ":" + digest;
    }

    public Map<String, Object> telemetryAttributes() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifact.logical_name", logicalName);
        result.put("artifact.type", type);
        if (repository != null) result.put("artifact.repository", repository);
        if (name != null) result.put("artifact.name", name);
        if (version != null) result.put("artifact.version", version);
        result.put("artifact.digest.algorithm", digestAlgorithm);
        result.put("artifact.digest", digest);
        if (uri != null) result.put("artifact.uri", uri);
        result.putAll(attributes.asMap());
        return result;
    }

    public static final class Builder {
        private String logicalName;
        private String type;
        private String repository;
        private String name;
        private String version;
        private String digestAlgorithm;
        private String digest;
        private String uri;
        private final Attributes.Builder attributes = Attributes.builder();

        public Builder logicalName(String value) { logicalName = value; return this; }
        public Builder type(String value) { type = value; return this; }
        public Builder repository(String value) { repository = value; return this; }
        public Builder name(String value) { name = value; return this; }
        public Builder version(String value) { version = value; return this; }
        public Builder digest(String algorithm, String value) { digestAlgorithm = algorithm; digest = value; return this; }
        public Builder uri(String value) { uri = value; return this; }
        public Builder attribute(String key, Object value) { attributes.put(key, value); return this; }
        public Builder attributes(Map<String, ?> values) { attributes.putAll(values); return this; }

        public ArtifactRef build() {
            return new ArtifactRef(logicalName, type, repository, name, version,
                digestAlgorithm, digest, uri, attributes.build());
        }
    }
}
