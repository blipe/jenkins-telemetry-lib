package io.jenkins.telemetry.core;

import io.jenkins.telemetry.api.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class TelemetryEngine implements Telemetry {
    private final Clock clock;
    private final IdGenerator ids;
    private final Correlation baseCorrelation;
    private final Map<String, Object> baseResource;
    private final TelemetryConfiguration configuration;
    private final TelemetryJournal journal;
    private final TelemetryExporter exporter;
    private final Redactor redactor;
    private final DefaultArtifactRegistry artifacts = new DefaultArtifactRegistry();
    private final AtomicReference<ReleaseIdentity> release = new AtomicReference<>();
    private final AtomicReference<DeliveryManifest> importedDeliveryManifest = new AtomicReference<>();
    private final AtomicReference<List<SpanLinkData>> defaultSpanLinks =
        new AtomicReference<>(List.of());
    private final ThreadLocal<Deque<OperationState>> operations = ThreadLocal.withInitial(ArrayDeque::new);
    private final AtomicLong dropped = new AtomicLong();
    private volatile boolean closed;

    private TelemetryEngine(Builder builder) throws IOException {
        this.clock = builder.clock;
        this.ids = builder.ids;
        this.baseCorrelation = builder.correlation;
        this.baseResource = Map.copyOf(builder.resource);
        this.configuration = builder.configuration;
        this.journal = new FileTelemetryJournal(builder.journalDirectory,
            configuration.maxJournalBytes(), configuration.journalSegmentBytes(),
            configuration.forceJournalWrites());
        this.redactor = Redactor.withDefaults(
            configuration.redactionPatterns(), configuration.redactionReplacement());

        Map<String, TelemetryCollectorFactory> factories = new LinkedHashMap<>();
        ServiceLoader.load(TelemetryCollectorFactory.class).forEach(factory ->
            factories.put(factory.type(), factory));
        for (TelemetryCollectorFactory factory : builder.factories) {
            factories.put(factory.type(), factory);
        }

        List<CollectorBinding> bindings = new ArrayList<>();
        for (CollectorDefinition definition : configuration.collectors()) {
            TelemetryCollectorFactory factory = factories.get(definition.type());
            if (factory == null) {
                throw new IllegalArgumentException("No TelemetryCollectorFactory for type "
                    + definition.type() + ". Available: " + factories.keySet());
            }
            TelemetryCollector collector = factory.create(definition.id(), definition.configuration());
            try {
                collector.start(new CollectorContext(definition.id(),
                    builder.journalDirectory.resolve("collectors").resolve(definition.id()),
                    clock, definition.configuration()));
            } catch (Exception e) {
                throw new IOException("Cannot start telemetry collector " + definition.id(), e);
            }
            bindings.add(new CollectorBinding(definition, collector, definition.signals()));
        }
        this.exporter = new TelemetryExporter(journal, bindings,
            configuration.batchRecords(), configuration.batchBytes(), configuration.exportTimeout(),
            configuration.retryInitialDelay(), configuration.retryMaxDelay(), configuration.autoExport());
    }

    public static Builder builder(Path journalDirectory) {
        return new Builder(journalDirectory);
    }

    @Override
    public ReleaseIdentity release(ReleaseIdentity identity) {
        ensureOpen();
        Objects.requireNonNull(identity, "identity");
        ReleaseIdentity current = release.get();
        if (current != null && !current.serviceName().equals(identity.serviceName())) {
            throw new IllegalStateException("Release service cannot change from "
                + current.serviceName() + " to " + identity.serviceName());
        }
        release.set(identity);
        logs().info("Release identity established", Attributes.copyOf(identity.resourceAttributes()));
        metrics().counter("jenkins.telemetry.release.identities", "{identity}", "Release identities set").add(1);
        return identity;
    }

    @Override
    public ReleaseIdentity release() {
        return release.get();
    }

    /**
     * Restores durable Jenkins state after a controller restart without emitting duplicate telemetry.
     */
    public void restoreRelease(ReleaseIdentity identity) {
        ensureOpen();
        if (identity != null) {
            release.set(identity);
        }
    }

    /**
     * Restores an artifact registry entry after a controller restart without emitting duplicate telemetry.
     */
    public void restoreArtifact(ArtifactRef artifact) {
        ensureOpen();
        if (artifact != null) {
            artifacts.record(artifact);
        }
    }

    @Override
    public LogEmitter logs() {
        return (severity, message, attributes) ->
            recordLog(severity, message, attributes, currentCorrelation());
    }

    @Override
    public MetricEmitter metrics() {
        return new MetricEmitter() {
            @Override public Counter counter(String name) { return counter(name, "{count}", ""); }
            @Override public Counter counter(String name, String unit, String description) {
                return new Counter() {
                    @Override public void add(long delta) { add(delta, Attributes.empty()); }
                    @Override public void add(long delta, Attributes attributes) {
                        recordMetric(name, MetricKind.COUNTER, delta, unit, description, attributes,
                            currentCorrelation());
                    }
                };
            }
            @Override public Histogram histogram(String name) { return histogram(name, "", ""); }
            @Override public Histogram histogram(String name, String unit, String description) {
                return new Histogram() {
                    @Override public void record(double value) { record(value, Attributes.empty()); }
                    @Override public void record(double value, Attributes attributes) {
                        recordMetric(name, MetricKind.HISTOGRAM, value, unit, description, attributes,
                            currentCorrelation());
                    }
                };
            }
            @Override public Gauge gauge(String name) { return gauge(name, "", ""); }
            @Override public Gauge gauge(String name, String unit, String description) {
                return new Gauge() {
                    @Override public void set(double value) { set(value, Attributes.empty()); }
                    @Override public void set(double value, Attributes attributes) {
                        recordMetric(name, MetricKind.GAUGE, value, unit, description, attributes,
                            currentCorrelation());
                    }
                };
            }
        };
    }

    @Override
    public OperationHandle beginOperation(OperationSpec specification) {
        ensureOpen();
        Objects.requireNonNull(specification, "specification");
        Deque<OperationState> stack = operations.get();
        OperationState parent = stack.peek();
        String traceId = first(specification.parentTraceId(), parent == null ? null : parent.traceId());
        String parentSpanId = first(specification.parentSpanId(), parent == null ? null : parent.spanId());
        DetachedOperation detached = startDetached(specification, traceId, parentSpanId);
        OperationState state = new OperationState(this, detached);
        stack.push(state);
        recordLog(Severity.INFO, "Operation started: " + specification.name(),
            Attributes.builder()
                .put("event.name", "operation.started")
                .put("operation.kind", specification.kind().name())
                .build(),
            correlationFor(detached));
        return state;
    }

    public DetachedOperation startDetached(OperationSpec specification,
                                            String traceId, String parentSpanId) {
        ensureOpen();
        String resolvedTrace = traceId == null || traceId.isBlank() ? ids.traceId() : traceId;
        String spanId = ids.spanId();
        return new DetachedOperation(ids.operationId(), resolvedTrace, spanId, parentSpanId,
            specification.name(), specification.kind(), nowUnixNano(),
            specification.attributes(), List.of(), defaultSpanLinks.get());
    }

    public void completeDetached(DetachedOperation operation, SpanStatus status, String statusMessage) {
        completeDetached(operation, status, statusMessage, currentCorrelation().withTrace(
            operation.traceId(), operation.spanId(), operation.name()));
    }

    public void completeDetached(DetachedOperation operation, SpanStatus status, String statusMessage,
                                 Correlation correlation) {
        ensureOpen();
        long end = nowUnixNano();
        SpanData span = new SpanData(operation.traceId(), operation.spanId(),
            operation.parentSpanId(), operation.name(), operation.kind(),
            operation.startTimeUnixNano(), end, status, statusMessage,
            operation.attributes(), operation.events(), operation.links(), correlation);
        recordSpan(span);

        Attributes metricAttributes = Attributes.builder()
            .put("cicd.pipeline.task.type", operation.kind().name().toLowerCase())
            .put("cicd.pipeline.task.result", status == SpanStatus.OK ? "success" : "failure")
            .build();
        recordMetric("jenkins.pipeline.operation.duration", MetricKind.HISTOGRAM,
            span.durationNanos() / 1_000_000_000d, "s", "Pipeline operation duration",
            metricAttributes, correlation);
        recordMetric("jenkins.pipeline.operations", MetricKind.COUNTER, 1,
            "{operation}", "Pipeline operations", metricAttributes, correlation);

        recordLog(status == SpanStatus.OK ? Severity.INFO : Severity.ERROR,
            "Operation " + (status == SpanStatus.OK ? "succeeded: " : "failed: ") + operation.name(),
            Attributes.builder()
                .put("event.name", "operation.completed")
                .put("operation.kind", operation.kind().name())
                .put("operation.status", status.name())
                .put("status.message", statusMessage)
                .build(),
            correlation);
    }

    void operationEnded(OperationState state) {
        Deque<OperationState> stack = operations.get();
        if (!stack.isEmpty() && stack.peek() == state) {
            stack.pop();
        } else {
            stack.remove(state);
        }
        if (stack.isEmpty()) operations.remove();
    }

    @Override
    public ArtifactRegistry artifacts() {
        return artifacts;
    }

    @Override
    public ArtifactRef artifact(ArtifactRef artifact) {
        ensureOpen();
        artifacts.record(artifact);
        Attributes attributes = Attributes.copyOf(artifact.telemetryAttributes());
        logs().info("Artifact recorded: " + artifact.logicalName(), attributes);
        metrics().counter("jenkins.pipeline.artifacts", "{artifact}", "Artifacts produced")
            .add(1, Attributes.builder()
                .put("artifact.type", artifact.type())
                .put("artifact.logical_name", artifact.logicalName())
                .build());
        return artifact;
    }

    @Override
    public DeploymentHandle beginDeployment(DeploymentSpec specification) {
        ensureOpen();
        Objects.requireNonNull(specification, "specification");
        String deploymentId = specification.deploymentId();
        if (deploymentId == null || deploymentId.isBlank()) {
            deploymentId = ids.operationId();
        }
        Attributes.Builder attributes = Attributes.builder()
            .put("deployment.id", deploymentId)
            .put("deployment.environment.name", specification.environment())
            .put("deployment.target", specification.target())
            .put("k8s.cluster.name", specification.cluster())
            .put("k8s.namespace.name", specification.namespace())
            .put("cloud.region", specification.region())
            .putAll(specification.attributes());
        if (specification.artifact() != null) {
            attributes.putAll(specification.artifact().telemetryAttributes());
        }
        OperationHandle operation = beginOperation(OperationSpec.builder(
                "Deploy " + specification.target() + " to " + specification.environment(),
                OperationKind.DEPLOY)
            .attributes(attributes.build().asMap())
            .build());
        return new DefaultDeploymentHandle(this, specification, deploymentId, operation);
    }

    @Override
    public RuntimeManifest runtimeManifest(String deploymentEnvironment, String deploymentId) {
        ReleaseIdentity identity = release.get();
        ArtifactRef artifact = null;
        try {
            artifact = artifacts.primary();
        } catch (IllegalStateException ignored) {
        }
        DeliveryManifest imported = importedDeliveryManifest.get();
        BuildProvenance origin = imported == null ? null : imported.build();
        Attributes manifestAttributes = identity == null ? Attributes.empty() : identity.attributes();
        if (origin != null) {
            manifestAttributes = manifestAttributes.merge(Attributes.builder()
                .put("delivery.build.run.id", origin.runId())
                .put("delivery.build.job.name", origin.jobName())
                .put("delivery.build.number", origin.buildNumber())
                .put("delivery.build.url", origin.buildUrl())
                .put("delivery.deployment.pipeline.run.id", baseCorrelation.runId())
                .put("delivery.deployment.pipeline.url", baseCorrelation.buildUrl())
                .build());
        }
        return new RuntimeManifest(
            identity == null ? null : identity.serviceNamespace(),
            identity == null ? null : identity.serviceName(),
            identity == null ? null : identity.serviceVersion(),
            identity == null ? null : identity.sourceRevision(),
            origin == null ? baseCorrelation.runId() : origin.runId(),
            origin == null ? baseCorrelation.buildUrl() : origin.buildUrl(),
            artifact == null ? null : artifact.type(),
            artifact == null ? null : artifact.uri(),
            artifact == null ? null : artifact.immutableId(),
            deploymentId,
            deploymentEnvironment,
            manifestAttributes
        );
    }

    @Override
    public DeliveryManifest deliveryManifest(BuildProvenance provenance, Attributes attributes) {
        ensureOpen();
        ReleaseIdentity identity = release.get();
        if (identity == null) {
            throw new IllegalStateException("Release identity must be established before exporting a delivery manifest");
        }
        List<ArtifactRef> immutableArtifacts = List.copyOf(artifacts.all());
        if (immutableArtifacts.isEmpty()) {
            throw new IllegalStateException("At least one immutable artifact must be recorded before exporting a delivery manifest");
        }
        return new DeliveryManifest(DeliveryManifest.SCHEMA, nowUnixNano(), identity,
            immutableArtifacts, Objects.requireNonNull(provenance, "provenance"), attributes);
    }

    @Override
    public DeliveryManifest importDeliveryManifest(DeliveryManifest manifest) {
        ensureOpen();
        applyDeliveryManifest(Objects.requireNonNull(manifest, "manifest"), true);
        return manifest;
    }

    /** Restores imported cross-run lineage after a controller restart without duplicate records. */
    public void restoreDeliveryManifest(DeliveryManifest manifest) {
        ensureOpen();
        if (manifest != null) applyDeliveryManifest(manifest, false);
    }

    public DeliveryManifest importedDeliveryManifest() {
        return importedDeliveryManifest.get();
    }

    private void applyDeliveryManifest(DeliveryManifest manifest, boolean emit) {
        ReleaseIdentity current = release.get();
        if (current != null) {
            requireCompatible("serviceName", current.serviceName(), manifest.release().serviceName());
            requireCompatible("sourceRevision", current.sourceRevision(), manifest.release().sourceRevision());
            requireCompatible("serviceVersion", current.serviceVersion(), manifest.release().serviceVersion());
        }
        for (ArtifactRef artifact : manifest.artifacts()) {
            ArtifactRef existing = artifacts.find(artifact.logicalName()).orElse(null);
            if (existing != null && !existing.immutableId().equals(artifact.immutableId())) {
                throw new IllegalStateException("Artifact " + artifact.logicalName()
                    + " already refers to " + existing.immutableId()
                    + " and cannot be replaced by " + artifact.immutableId());
            }
        }
        release.set(manifest.release());
        manifest.artifacts().forEach(artifacts::record);
        importedDeliveryManifest.set(manifest);
        defaultSpanLinks.set(List.of(manifest.build().asSpanLink()));
        if (emit) {
            Correlation correlation = currentCorrelation();
            recordLog(Severity.INFO, "Delivery manifest imported",
                Attributes.builder()
                    .put("event.name", "delivery.manifest.imported")
                    .put("delivery.manifest.schema", manifest.schema())
                    .put("delivery.build.run.id", manifest.build().runId())
                    .put("delivery.build.job.name", manifest.build().jobName())
                    .put("delivery.artifact.count", manifest.artifacts().size())
                    .build(), correlation);
            recordMetric("jenkins.delivery.manifests.imported", MetricKind.COUNTER, 1,
                "{manifest}", "Imported delivery manifests", Attributes.empty(), correlation);
        }
    }

    private static void requireCompatible(String field, String existing, String imported) {
        if (existing != null && imported != null && !existing.equals(imported)) {
            throw new IllegalStateException("Imported delivery manifest " + field + " " + imported
                + " conflicts with existing value " + existing);
        }
    }

    @Override
    public void event(String name, Attributes attributes) {
        Deque<OperationState> stack = operations.get();
        OperationState current = stack.peek();
        if (current != null) {
            current.event(name, attributes);
        }
        logs().info(name, (attributes == null ? Attributes.empty() : attributes)
            .merge(Attributes.of("event.name", name)));
    }

    public void recordConsoleLog(String line, Attributes attributes, Correlation correlation) {
        recordLog(Severity.INFO, line, (attributes == null ? Attributes.empty() : attributes)
            .merge(Attributes.of("log.source", "jenkins.console")), correlation);
    }

    public void recordLog(Severity severity, String message, Attributes attributes, Correlation correlation) {
        ensureOpen();
        long now = nowUnixNano();
        String safeMessage = redactor.redact(message == null ? "" : message);
        Attributes safeAttributes = redactor.redact(attributes);
        append(TelemetryMapper.envelope(
            new LogData(now, now, severity, safeMessage, safeAttributes, correlation),
            redactor.redact(resourceAttributes())));
    }

    public void recordMetric(String name, MetricKind kind, double value, String unit,
                             String description, Attributes attributes, Correlation correlation) {
        ensureOpen();
        append(TelemetryMapper.envelope(
            new MetricData(nowUnixNano(), name, kind, value, unit, description,
                redactor.redact(attributes), correlation),
            redactor.redact(metricResourceAttributes())));
    }

    public void recordSpan(SpanData span) {
        ensureOpen();
        Attributes safe = redactor.redact(span.attributes());
        List<SpanEventData> safeEvents = span.events().stream()
            .map(event -> new SpanEventData(event.timeUnixNano(), redactor.redact(event.name()),
                redactor.redact(event.attributes())))
            .toList();
        List<SpanLinkData> safeLinks = span.links().stream()
            .map(link -> new SpanLinkData(link.traceId(), link.spanId(), redactor.redact(link.attributes())))
            .toList();
        SpanData sanitized = new SpanData(span.traceId(), span.spanId(), span.parentSpanId(),
            redactor.redact(span.name()), span.kind(), span.startTimeUnixNano(), span.endTimeUnixNano(),
            span.status(), redactor.redact(span.statusMessage()), safe, safeEvents, safeLinks,
            span.correlation());
        append(TelemetryMapper.envelope(sanitized, redactor.redact(resourceAttributes())));
    }

    @Override
    public TelemetryStatus status() {
        Map<Signal, Long> counts = journal.recordCounts();
        return new TelemetryStatus(journal.sizeBytes(),
            counts.getOrDefault(Signal.LOGS, 0L),
            counts.getOrDefault(Signal.METRICS, 0L),
            counts.getOrDefault(Signal.TRACES, 0L),
            dropped.get(), exporter.collectorLag());
    }

    @Override
    public void flush() {
        ensureOpen();
        exporter.flush();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        try {
            exporter.close();
        } finally {
            try {
                journal.close();
            } catch (IOException ignored) {
            }
            closed = true;
        }
    }

    public Correlation currentCorrelation() {
        ReleaseIdentity identity = release.get();
        Correlation correlation = identity == null ? baseCorrelation : baseCorrelation.withRelease(identity);
        OperationState state = operations.get().peek();
        return state == null ? correlation
            : correlation.withTrace(state.traceId(), state.spanId(), null);
    }

    public Correlation correlationFor(DetachedOperation operation) {
        ReleaseIdentity identity = release.get();
        Correlation correlation = identity == null ? baseCorrelation : baseCorrelation.withRelease(identity);
        return correlation.withTrace(operation.traceId(), operation.spanId(), operation.name());
    }

    public long nowUnixNano() {
        Instant instant = clock.instant();
        return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L), instant.getNano());
    }


    private Map<String, Object> metricResourceAttributes() {
        Map<String, Object> result = new LinkedHashMap<>(baseResource);
        result.put("telemetry.sdk.name", "jenkins-delivery-telemetry");
        result.put("telemetry.sdk.language", "java");
        result.put("telemetry.sdk.version", "1.0.0");
        ReleaseIdentity identity = release.get();
        if (identity != null) {
            put(result, "service.namespace", identity.serviceNamespace());
            put(result, "service.name", identity.serviceName());
            put(result, "service.version", identity.serviceVersion());
        }
        put(result, "cicd.pipeline.name", baseCorrelation.jobName());
        return result;
    }

    private Map<String, Object> resourceAttributes() {
        Map<String, Object> result = new LinkedHashMap<>(baseResource);
        result.put("telemetry.sdk.name", "jenkins-delivery-telemetry");
        result.put("telemetry.sdk.language", "java");
        result.put("telemetry.sdk.version", "1.0.0");
        ReleaseIdentity identity = release.get();
        if (identity != null) result.putAll(identity.resourceAttributes());
        put(result, "cicd.pipeline.run.id", baseCorrelation.runId());
        put(result, "cicd.pipeline.name", baseCorrelation.jobName());
        put(result, "cicd.pipeline.run.number", baseCorrelation.buildNumber());
        put(result, "cicd.pipeline.run.url.full", baseCorrelation.buildUrl());
        return result;
    }

    private void append(TelemetryEnvelope envelope) {
        try {
            journal.append(envelope);
            exporter.requestExport();
        } catch (JournalCapacityException e) {
            dropped.incrementAndGet();
        } catch (IOException e) {
            dropped.incrementAndGet();
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Telemetry engine is closed");
    }

    private static String first(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private static void put(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }

    public static final class Builder {
        private final Path journalDirectory;
        private Clock clock = Clock.systemUTC();
        private IdGenerator ids = new IdGenerator();
        private Correlation correlation = Correlation.empty();
        private final Map<String, Object> resource = new LinkedHashMap<>();
        private TelemetryConfiguration configuration = TelemetryConfiguration.journalOnly();
        private final List<TelemetryCollectorFactory> factories = new ArrayList<>();

        private Builder(Path journalDirectory) {
            this.journalDirectory = Objects.requireNonNull(journalDirectory, "journalDirectory");
        }

        public Builder clock(Clock value) { clock = Objects.requireNonNull(value); return this; }
        public Builder correlation(Correlation value) { correlation = Objects.requireNonNull(value); return this; }
        public Builder resource(String key, Object value) { if (value != null) resource.put(key, value); return this; }
        public Builder resource(Map<String, ?> values) { if (values != null) resource.putAll(values); return this; }
        public Builder configuration(TelemetryConfiguration value) {
            configuration = Objects.requireNonNull(value); return this;
        }
        public Builder collectorFactory(TelemetryCollectorFactory factory) {
            factories.add(Objects.requireNonNull(factory)); return this;
        }
        public Builder collectorFactories(Collection<? extends TelemetryCollectorFactory> values) {
            if (values != null) values.forEach(this::collectorFactory);
            return this;
        }

        public TelemetryEngine build() throws IOException {
            return new TelemetryEngine(this);
        }
    }
}
