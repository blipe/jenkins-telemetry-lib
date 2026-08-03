package io.jenkins.telemetry.api;

import java.io.Serializable;
import java.util.Map;

public record OperationSpec(
    String name,
    OperationKind kind,
    String parentTraceId,
    String parentSpanId,
    Attributes attributes
) implements Serializable {
    public OperationSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Operation name must not be blank");
        }
        kind = kind == null ? OperationKind.CUSTOM : kind;
        attributes = attributes == null ? Attributes.empty() : attributes;
    }

    public static Builder builder(String name, OperationKind kind) {
        return new Builder(name, kind);
    }

    public static final class Builder {
        private final String name;
        private final OperationKind kind;
        private String parentTraceId;
        private String parentSpanId;
        private final Attributes.Builder attributes = Attributes.builder();

        private Builder(String name, OperationKind kind) {
            this.name = name;
            this.kind = kind;
        }

        public Builder parent(String traceId, String spanId) {
            parentTraceId = traceId;
            parentSpanId = spanId;
            return this;
        }

        public Builder attribute(String key, Object value) { attributes.put(key, value); return this; }
        public Builder attributes(Map<String, ?> values) { attributes.putAll(values); return this; }

        public OperationSpec build() {
            return new OperationSpec(name, kind, parentTraceId, parentSpanId, attributes.build());
        }
    }
}
