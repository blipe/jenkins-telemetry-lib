package io.jenkins.telemetry.api;

public interface OperationHandle extends AutoCloseable {
    String operationId();

    String traceId();

    String spanId();

    void event(String name, Attributes attributes);

    void attribute(String key, Object value);

    void succeed();

    void fail(Throwable failure);

    void fail(String errorType, String message);

    void cancel(String reason);

    boolean ended();

    @Override
    default void close() {
        succeed();
    }
}
