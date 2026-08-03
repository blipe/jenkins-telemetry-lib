package io.jenkins.telemetry.api;

public interface DeploymentHandle extends OperationHandle {
    String deploymentId();

    DeploymentSpec specification();

    void verificationStarted();

    void verificationSucceeded();

    void verificationFailed(Throwable failure);

    void rolledBack(ArtifactRef replacement);
}
