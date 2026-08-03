package io.jenkins.telemetry.api;

import java.io.Serializable;

public record LogData(
    long timeUnixNano,
    long observedTimeUnixNano,
    Severity severity,
    String body,
    Attributes attributes,
    Correlation correlation
) implements Serializable {
    public LogData {
        severity = severity == null ? Severity.INFO : severity;
        body = body == null ? "" : body;
        attributes = attributes == null ? Attributes.empty() : attributes;
        correlation = correlation == null ? Correlation.empty() : correlation;
    }
}
