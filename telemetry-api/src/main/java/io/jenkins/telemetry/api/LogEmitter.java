package io.jenkins.telemetry.api;

public interface LogEmitter {
    void emit(Severity severity, String message, Attributes attributes);

    default void trace(String message) { emit(Severity.TRACE, message, Attributes.empty()); }
    default void debug(String message) { emit(Severity.DEBUG, message, Attributes.empty()); }
    default void info(String message) { emit(Severity.INFO, message, Attributes.empty()); }
    default void info(String message, Attributes attributes) { emit(Severity.INFO, message, attributes); }
    default void warn(String message) { emit(Severity.WARN, message, Attributes.empty()); }
    default void warn(String message, Attributes attributes) { emit(Severity.WARN, message, attributes); }
    default void error(String message) { emit(Severity.ERROR, message, Attributes.empty()); }
    default void error(String message, Attributes attributes) { emit(Severity.ERROR, message, attributes); }
}
