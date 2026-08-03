package io.jenkins.telemetry.api;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public record RuntimeManifest(
    String serviceNamespace,
    String serviceName,
    String serviceVersion,
    String sourceRevision,
    String runId,
    String buildUrl,
    String artifactType,
    String artifactUri,
    String artifactDigest,
    String deploymentId,
    String deploymentEnvironment,
    Attributes attributes
) implements Serializable {
    public RuntimeManifest {
        attributes = attributes == null ? Attributes.empty() : attributes;
    }

    public Map<String, String> openTelemetryEnvironment() {
        Map<String, String> env = new LinkedHashMap<>();
        if (serviceName != null) {
            env.put("OTEL_SERVICE_NAME", serviceName);
        }
        Map<String, String> resource = new LinkedHashMap<>();
        put(resource, "service.namespace", serviceNamespace);
        put(resource, "service.version", serviceVersion);
        put(resource, "vcs.ref.head.revision", sourceRevision);
        put(resource, "cicd.pipeline.run.id", runId);
        put(resource, "cicd.pipeline.run.url.full", buildUrl);
        put(resource, "artifact.type", artifactType);
        put(resource, "artifact.uri", artifactUri);
        put(resource, "artifact.digest", artifactDigest);
        put(resource, "deployment.id", deploymentId);
        put(resource, "deployment.environment.name", deploymentEnvironment);
        attributes.asMap().forEach((key, value) -> put(resource, key, String.valueOf(value)));
        if (!resource.isEmpty()) {
            String joined = resource.entrySet().stream()
                .map(entry -> escape(entry.getKey()) + "=" + escape(entry.getValue()))
                .reduce((a, b) -> a + "," + b)
                .orElse("");
            env.put("OTEL_RESOURCE_ATTRIBUTES", joined);
        }
        return env;
    }

    private static void put(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace(",", "\\,").replace("=", "\\=");
    }
}
