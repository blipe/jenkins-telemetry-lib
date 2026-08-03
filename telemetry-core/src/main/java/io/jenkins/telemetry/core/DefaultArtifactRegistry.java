package io.jenkins.telemetry.core;

import io.jenkins.telemetry.api.ArtifactRef;
import io.jenkins.telemetry.api.ArtifactRegistry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class DefaultArtifactRegistry implements ArtifactRegistry {
    private final Map<String, ArtifactRef> artifacts = new LinkedHashMap<>();

    @Override
    public synchronized ArtifactRef record(ArtifactRef artifact) {
        if (artifact == null) throw new IllegalArgumentException("artifact must not be null");
        artifacts.put(artifact.logicalName(), artifact);
        return artifact;
    }

    @Override
    public synchronized Optional<ArtifactRef> find(String logicalName) {
        return Optional.ofNullable(artifacts.get(logicalName));
    }

    @Override
    public synchronized ArtifactRef primary() {
        ArtifactRef primary = artifacts.get("primary");
        if (primary != null) return primary;
        return artifacts.values().stream().findFirst()
            .orElseThrow(() -> new IllegalStateException("No artifact has been recorded"));
    }

    @Override
    public synchronized Collection<ArtifactRef> all() {
        return ListCopy.copy(artifacts.values());
    }

    private static final class ListCopy {
        static <T> Collection<T> copy(Collection<T> values) {
            return java.util.List.copyOf(values);
        }
    }
}
