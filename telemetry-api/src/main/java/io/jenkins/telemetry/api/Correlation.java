package io.jenkins.telemetry.api;

import java.io.Serializable;

public record Correlation(
    String runId,
    String jobName,
    String buildNumber,
    String buildUrl,
    String serviceNamespace,
    String serviceName,
    String serviceVersion,
    String sourceRevision,
    String deploymentEnvironment,
    String deploymentId,
    String traceId,
    String spanId,
    String operationName
) implements Serializable {
    public Correlation {
        runId = normalize(runId);
        jobName = normalize(jobName);
        buildNumber = normalize(buildNumber);
        buildUrl = normalize(buildUrl);
        serviceNamespace = normalize(serviceNamespace);
        serviceName = normalize(serviceName);
        serviceVersion = normalize(serviceVersion);
        sourceRevision = normalize(sourceRevision);
        deploymentEnvironment = normalize(deploymentEnvironment);
        deploymentId = normalize(deploymentId);
        traceId = normalize(traceId);
        spanId = normalize(spanId);
        operationName = normalize(operationName);
    }

    public static Correlation empty() {
        return new Correlation(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public Correlation withTrace(String traceId, String spanId, String operationName) {
        return new Correlation(runId, jobName, buildNumber, buildUrl, serviceNamespace, serviceName,
            serviceVersion, sourceRevision, deploymentEnvironment, deploymentId,
            traceId, spanId, operationName);
    }

    public Correlation withRelease(ReleaseIdentity release) {
        if (release == null) {
            return this;
        }
        return new Correlation(runId, jobName, buildNumber, buildUrl,
            first(release.serviceNamespace(), serviceNamespace),
            first(release.serviceName(), serviceName),
            first(release.serviceVersion(), serviceVersion),
            first(release.sourceRevision(), sourceRevision),
            deploymentEnvironment, deploymentId, traceId, spanId, operationName);
    }

    public Correlation withDeployment(String environment, String id) {
        return new Correlation(runId, jobName, buildNumber, buildUrl, serviceNamespace, serviceName,
            serviceVersion, sourceRevision, first(environment, deploymentEnvironment),
            first(id, deploymentId), traceId, spanId, operationName);
    }

    private static String first(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
