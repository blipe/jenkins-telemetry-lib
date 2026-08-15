package io.jenkins.telemetry.api;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public record ReleaseIdentity(
    String serviceNamespace,
    String serviceName,
    String serviceVersion,
    String sourceRepository,
    String sourceRevision,
    String sourceRef,
    String buildVersion,
    Attributes attributes
) implements Serializable {
    public ReleaseIdentity {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName must not be blank");
        }
        attributes = attributes == null ? Attributes.empty() : attributes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> resourceAttributes() {
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, "service.namespace", serviceNamespace);
        put(values, "service.name", serviceName);
        put(values, "service.version", serviceVersion);
        put(values, "vcs.repository.url.full", sourceRepository);
        put(values, "vcs.ref.head.revision", sourceRevision);
        put(values, "vcs.ref.head.name", sourceRef);
        put(values, "cicd.pipeline.run.version", buildVersion);
        values.putAll(attributes.asMap());
        return values;
    }

    private static void put(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    public static final class Builder {
        private String serviceNamespace;
        private String serviceName;
        private String serviceVersion;
        private String sourceRepository;
        private String sourceRevision;
        private String sourceRef;
        private String buildVersion;
        private final Attributes.Builder attributes = Attributes.builder();

        public Builder serviceNamespace(String value) { serviceNamespace = value; return this; }
        public Builder serviceName(String value) { serviceName = value; return this; }
        public Builder serviceVersion(String value) { serviceVersion = value; return this; }
        public Builder sourceRepository(String value) { sourceRepository = value; return this; }
        public Builder sourceRevision(String value) { sourceRevision = value; return this; }
        public Builder sourceRef(String value) { sourceRef = value; return this; }
        public Builder buildVersion(String value) { buildVersion = value; return this; }
        public Builder attribute(String key, Object value) { attributes.put(key, value); return this; }
        public Builder attributes(Map<String, ?> values) { attributes.putAll(values); return this; }

        public ReleaseIdentity build() {
            return new ReleaseIdentity(serviceNamespace, serviceName, serviceVersion, sourceRepository,
                sourceRevision, sourceRef, buildVersion, attributes.build());
        }
    }
}
