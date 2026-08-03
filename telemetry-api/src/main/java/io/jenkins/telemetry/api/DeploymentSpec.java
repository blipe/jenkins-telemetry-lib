package io.jenkins.telemetry.api;

import java.io.Serializable;
import java.util.Map;

public record DeploymentSpec(
    String deploymentId,
    String environment,
    String target,
    String cluster,
    String namespace,
    String region,
    ArtifactRef artifact,
    Attributes attributes
) implements Serializable {
    public DeploymentSpec {
        if (environment == null || environment.isBlank()) {
            throw new IllegalArgumentException("Deployment environment must not be blank");
        }
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Deployment target must not be blank");
        }
        attributes = attributes == null ? Attributes.empty() : attributes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String deploymentId;
        private String environment;
        private String target;
        private String cluster;
        private String namespace;
        private String region;
        private ArtifactRef artifact;
        private final Attributes.Builder attributes = Attributes.builder();

        public Builder deploymentId(String value) { deploymentId = value; return this; }
        public Builder environment(String value) { environment = value; return this; }
        public Builder target(String value) { target = value; return this; }
        public Builder cluster(String value) { cluster = value; return this; }
        public Builder namespace(String value) { namespace = value; return this; }
        public Builder region(String value) { region = value; return this; }
        public Builder artifact(ArtifactRef value) { artifact = value; return this; }
        public Builder attribute(String key, Object value) { attributes.put(key, value); return this; }
        public Builder attributes(Map<String, ?> values) { attributes.putAll(values); return this; }

        public DeploymentSpec build() {
            return new DeploymentSpec(deploymentId, environment, target, cluster, namespace,
                region, artifact, attributes.build());
        }
    }
}
