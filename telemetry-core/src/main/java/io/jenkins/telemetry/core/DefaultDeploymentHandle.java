package io.jenkins.telemetry.core;

import io.jenkins.telemetry.api.*;

import java.util.concurrent.atomic.AtomicBoolean;

final class DefaultDeploymentHandle implements DeploymentHandle {
    private final TelemetryEngine engine;
    private final DeploymentSpec specification;
    private final String deploymentId;
    private final OperationHandle operation;
    private final AtomicBoolean verificationOpen = new AtomicBoolean();

    DefaultDeploymentHandle(TelemetryEngine engine, DeploymentSpec specification,
                            String deploymentId, OperationHandle operation) {
        this.engine = engine;
        this.specification = specification;
        this.deploymentId = deploymentId;
        this.operation = operation;
    }

    @Override public String deploymentId() { return deploymentId; }
    @Override public DeploymentSpec specification() { return specification; }
    @Override public String operationId() { return operation.operationId(); }
    @Override public String traceId() { return operation.traceId(); }
    @Override public String spanId() { return operation.spanId(); }
    @Override public void event(String name, Attributes attributes) { operation.event(name, attributes); }
    @Override public void attribute(String key, Object value) { operation.attribute(key, value); }
    @Override public void succeed() { operation.succeed(); }
    @Override public void fail(Throwable failure) { operation.fail(failure); }
    @Override public void fail(String errorType, String message) { operation.fail(errorType, message); }
    @Override public void cancel(String reason) { operation.cancel(reason); }
    @Override public boolean ended() { return operation.ended(); }

    @Override
    public void verificationStarted() {
        if (verificationOpen.compareAndSet(false, true)) {
            event("deployment.verification.started", Attributes.empty());
        }
    }

    @Override
    public void verificationSucceeded() {
        if (verificationOpen.compareAndSet(true, false)) {
            event("deployment.verification.succeeded", Attributes.empty());
        }
    }

    @Override
    public void verificationFailed(Throwable failure) {
        if (verificationOpen.compareAndSet(true, false)) {
            event("deployment.verification.failed", Attributes.builder()
                .put("error.type", failure == null ? null : failure.getClass().getName())
                .put("error.message", failure == null ? null : failure.getMessage())
                .build());
        }
    }

    @Override
    public void rolledBack(ArtifactRef replacement) {
        event("deployment.rolled_back", replacement == null
            ? Attributes.empty() : Attributes.copyOf(replacement.telemetryAttributes()));
    }
}
