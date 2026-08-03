package io.jenkins.telemetry.api;

public enum Severity {
    TRACE(1),
    DEBUG(5),
    INFO(9),
    WARN(13),
    ERROR(17),
    FATAL(21);

    private final int otlpNumber;

    Severity(int otlpNumber) {
        this.otlpNumber = otlpNumber;
    }

    public int otlpNumber() {
        return otlpNumber;
    }

    public static Severity parse(String value) {
        return value == null ? INFO : valueOf(value.trim().toUpperCase());
    }
}
