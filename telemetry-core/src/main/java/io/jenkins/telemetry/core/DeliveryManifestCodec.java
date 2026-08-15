package io.jenkins.telemetry.core;

import io.jenkins.telemetry.api.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic JSON codec and integrity verification for portable delivery manifests. */
public final class DeliveryManifestCodec {
    private DeliveryManifestCodec() {
    }

    public static String encode(DeliveryManifest manifest) {
        return MiniJson.write(toMap(manifest));
    }

    public static DeliveryManifest decode(String json) {
        return fromMap(MiniJson.parseObject(json));
    }

    public static DeliveryManifest decodeVerified(String json, String expectedSha256) {
        if (expectedSha256 == null || expectedSha256.isBlank()) {
            return decode(json);
        }
        String normalized = normalizeSha256(expectedSha256);
        String actual = sha256(json);
        if (!MessageDigest.isEqual(
            normalized.getBytes(StandardCharsets.US_ASCII),
            actual.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException(
                "Delivery manifest SHA-256 mismatch: expected " + normalized + ", actual " + actual);
        }
        return decode(json);
    }

    public static String sha256(String json) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public static Map<String, Object> toMap(DeliveryManifest manifest) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema", manifest.schema());
        result.put("createdTimeUnixNano", Long.toString(manifest.createdTimeUnixNano()));
        result.put("release", releaseToMap(manifest.release()));
        List<Map<String, Object>> artifacts = new ArrayList<>();
        manifest.artifacts().forEach(artifact -> artifacts.add(artifactToMap(artifact)));
        result.put("artifacts", artifacts);
        result.put("build", provenanceToMap(manifest.build()));
        result.put("attributes", manifest.attributes().asMap());
        return result;
    }

    @SuppressWarnings("unchecked")
    public static DeliveryManifest fromMap(Map<String, Object> source) {
        String schema = text(source.get("schema"));
        long created = TelemetryMapper.number(source.get("createdTimeUnixNano"), 0);
        ReleaseIdentity release = releaseFromMap(map(source.get("release")));
        List<ArtifactRef> artifacts = new ArrayList<>();
        Object rawArtifacts = source.get("artifacts");
        if (rawArtifacts instanceof Iterable<?> values) {
            for (Object value : values) artifacts.add(artifactFromMap(map(value)));
        }
        BuildProvenance provenance = provenanceFromMap(map(source.get("build")));
        return new DeliveryManifest(schema, created, release, artifacts, provenance,
            Attributes.copyOf(map(source.get("attributes"))));
    }

    private static Map<String, Object> releaseToMap(ReleaseIdentity release) {
        Map<String, Object> result = new LinkedHashMap<>();
        put(result, "serviceNamespace", release.serviceNamespace());
        put(result, "serviceName", release.serviceName());
        put(result, "serviceVersion", release.serviceVersion());
        put(result, "sourceRepository", release.sourceRepository());
        put(result, "sourceRevision", release.sourceRevision());
        put(result, "sourceRef", release.sourceRef());
        put(result, "buildVersion", release.buildVersion());
        result.put("attributes", release.attributes().asMap());
        return result;
    }

    private static ReleaseIdentity releaseFromMap(Map<String, Object> source) {
        return ReleaseIdentity.builder()
            .serviceNamespace(text(source.get("serviceNamespace")))
            .serviceName(required(source, "serviceName"))
            .serviceVersion(text(source.get("serviceVersion")))
            .sourceRepository(text(source.get("sourceRepository")))
            .sourceRevision(text(source.get("sourceRevision")))
            .sourceRef(text(source.get("sourceRef")))
            .buildVersion(text(source.get("buildVersion")))
            .attributes(map(source.get("attributes")))
            .build();
    }

    private static Map<String, Object> artifactToMap(ArtifactRef artifact) {
        Map<String, Object> result = new LinkedHashMap<>();
        put(result, "logicalName", artifact.logicalName());
        put(result, "type", artifact.type());
        put(result, "repository", artifact.repository());
        put(result, "name", artifact.name());
        put(result, "version", artifact.version());
        put(result, "digestAlgorithm", artifact.digestAlgorithm());
        put(result, "digest", artifact.digest());
        put(result, "uri", artifact.uri());
        result.put("attributes", artifact.attributes().asMap());
        return result;
    }

    private static ArtifactRef artifactFromMap(Map<String, Object> source) {
        return ArtifactRef.builder()
            .logicalName(text(source.get("logicalName")))
            .type(required(source, "type"))
            .repository(text(source.get("repository")))
            .name(text(source.get("name")))
            .version(text(source.get("version")))
            .digest(textOr(source.get("digestAlgorithm"), "sha256"), required(source, "digest"))
            .uri(text(source.get("uri")))
            .attributes(map(source.get("attributes")))
            .build();
    }

    private static Map<String, Object> provenanceToMap(BuildProvenance provenance) {
        Map<String, Object> result = new LinkedHashMap<>();
        put(result, "runId", provenance.runId());
        put(result, "jobName", provenance.jobName());
        put(result, "buildNumber", provenance.buildNumber());
        put(result, "buildUrl", provenance.buildUrl());
        put(result, "traceId", provenance.traceId());
        put(result, "spanId", provenance.spanId());
        result.put("attributes", provenance.attributes().asMap());
        return result;
    }

    private static BuildProvenance provenanceFromMap(Map<String, Object> source) {
        return new BuildProvenance(
            required(source, "runId"),
            required(source, "jobName"),
            text(source.get("buildNumber")),
            text(source.get("buildUrl")),
            required(source, "traceId"),
            required(source, "spanId"),
            Attributes.copyOf(map(source.get("attributes")))
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static String required(Map<String, Object> source, String key) {
        String value = text(source.get(key));
        if (value == null) throw new IllegalArgumentException("Missing delivery manifest field: " + key);
        return value;
    }

    private static String text(Object value) {
        if (value == null) return null;
        String result = String.valueOf(value);
        return result.isBlank() ? null : result;
    }

    private static String textOr(Object value, String fallback) {
        String result = text(value);
        return result == null ? fallback : result;
    }

    private static String normalizeSha256(String value) {
        String normalized = value.trim().toLowerCase();
        if (normalized.startsWith("sha256:")) normalized = normalized.substring("sha256:".length());
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Expected SHA-256 must be 64 hexadecimal characters");
        }
        return normalized;
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) target.put(key, value);
    }
}
