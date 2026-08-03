package io.jenkins.telemetry.core;

import io.jenkins.telemetry.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class OperationState implements OperationHandle {
    private final TelemetryEngine engine;
    private final DetachedOperation operation;
    private final AtomicBoolean ended = new AtomicBoolean();
    private final List<SpanEventData> events = new ArrayList<>();
    private Attributes attributes;

    OperationState(TelemetryEngine engine, DetachedOperation operation) {
        this.engine = engine;
        this.operation = operation;
        this.attributes = operation.attributes();
    }

    @Override public String operationId() { return operation.operationId(); }
    @Override public String traceId() { return operation.traceId(); }
    @Override public String spanId() { return operation.spanId(); }

    @Override
    public synchronized void event(String name, Attributes attributes) {
        ensureOpen();
        events.add(new SpanEventData(engine.nowUnixNano(), name, attributes));
    }

    @Override
    public synchronized void attribute(String key, Object value) {
        ensureOpen();
        attributes = attributes.merge(Attributes.of(key, value));
    }

    @Override
    public void succeed() {
        end(SpanStatus.OK, "", null, null);
    }

    @Override
    public void fail(Throwable failure) {
        if (failure == null) {
            fail("java.lang.Throwable", "Unknown failure");
            return;
        }
        end(SpanStatus.ERROR, failure.getMessage(), failure.getClass().getName(), failure);
    }

    @Override
    public void fail(String errorType, String message) {
        end(SpanStatus.ERROR, message, errorType, null);
    }

    @Override
    public void cancel(String reason) {
        end(SpanStatus.ERROR, reason, "cancelled", null);
    }

    @Override
    public boolean ended() {
        return ended.get();
    }

    private void end(SpanStatus status, String message, String errorType, Throwable failure) {
        if (!ended.compareAndSet(false, true)) return;
        Attributes finalAttributes;
        List<SpanEventData> finalEvents;
        synchronized (this) {
            finalAttributes = attributes;
            finalEvents = new ArrayList<>(events);
        }
        if (errorType != null) {
            finalAttributes = finalAttributes.merge(Attributes.builder()
                .put("error.type", errorType)
                .put("error.message", message)
                .build());
        }
        if (failure != null) {
            finalEvents.add(new SpanEventData(engine.nowUnixNano(), "exception",
                Attributes.builder()
                    .put("exception.type", failure.getClass().getName())
                    .put("exception.message", failure.getMessage())
                    .put("exception.stacktrace", stackTrace(failure))
                    .build()));
        }
        DetachedOperation completed = new DetachedOperation(operation.operationId(),
            operation.traceId(), operation.spanId(), operation.parentSpanId(), operation.name(),
            operation.kind(), operation.startTimeUnixNano(), finalAttributes, finalEvents, operation.links());
        engine.completeDetached(completed, status, message);
        engine.operationEnded(this);
    }

    private void ensureOpen() {
        if (ended()) throw new IllegalStateException("Operation has already ended");
    }

    private static String stackTrace(Throwable failure) {
        java.io.StringWriter buffer = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(buffer));
        return buffer.toString();
    }
}
