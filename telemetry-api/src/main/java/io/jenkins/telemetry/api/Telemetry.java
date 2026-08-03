package io.jenkins.telemetry.api;

public interface Telemetry extends AutoCloseable {
    ReleaseIdentity release(ReleaseIdentity identity);

    ReleaseIdentity release();

    LogEmitter logs();

    MetricEmitter metrics();

    OperationHandle beginOperation(OperationSpec specification);

    ArtifactRegistry artifacts();

    ArtifactRef artifact(ArtifactRef artifact);

    DeploymentHandle beginDeployment(DeploymentSpec specification);

    RuntimeManifest runtimeManifest(String deploymentEnvironment, String deploymentId);

    DeliveryManifest deliveryManifest(BuildProvenance provenance, Attributes attributes);

    DeliveryManifest importDeliveryManifest(DeliveryManifest manifest);

    void event(String name, Attributes attributes);

    TelemetryStatus status();

    void flush();

    @Override
    void close();
}
