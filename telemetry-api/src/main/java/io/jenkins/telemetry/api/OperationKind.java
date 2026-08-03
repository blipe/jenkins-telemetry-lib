package io.jenkins.telemetry.api;

public enum OperationKind {
    PIPELINE,
    STAGE,
    CHECKOUT,
    BUILD,
    TEST,
    PACKAGE,
    SECURITY_SCAN,
    QUALITY_GATE,
    PUBLISH,
    APPROVAL,
    DEPLOY,
    VERIFY,
    ROLLBACK,
    CUSTOM;

    public static OperationKind parse(String value) {
        return value == null ? CUSTOM : valueOf(value.trim().toUpperCase().replace('-', '_'));
    }
}
