package io.jenkins.telemetry.api;

import java.util.Collection;
import java.util.Optional;

public interface ArtifactRegistry {
    ArtifactRef record(ArtifactRef artifact);

    Optional<ArtifactRef> find(String logicalName);

    ArtifactRef primary();

    Collection<ArtifactRef> all();
}
