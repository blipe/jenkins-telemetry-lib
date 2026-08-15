# Architecture

```text
Jenkinsfile
    |
plugin-provided jenkinsTelemetry global variable
    |
telemetrySignal Pipeline step ---- automatic stage GraphListener
    |                                      |
TelemetryRunAction <-----------------------+
    |  serializable release/artifact/open-operation IDs
    |
TelemetryEngine
    |
redact -> append checksummed segment -> schedule export -> return
    |                                      |
    |                               background exporter
    |                                      |
    +-------------------------- collector-specific cursor
                                           |
                         file / HTTP JSON / OTLP / extension collector
```

## One-HPI boundary

The Maven reactor separates API, core, and built-in collectors for testability, but the user-facing product is one Jenkins HPI. The plugin contributes the global Pipeline API directly; there is no required Shared Library registration.

## CPS and restart safety

Pipeline closures never retain a live span, thread-local context, HTTP client, `Run`, `TaskListener`, or collector instance.

Across a suspension point the DSL retains only:

```text
operation ID
trace ID
span ID
serializable maps and values
```

`TelemetryRunAction` persists the release, artifacts, root operation, open detached operations, stage FlowNode mappings, and console cursor. A controller restart reconstructs the runtime engine from those values and the journal.

## Automatic tracing

`RunListener` creates and completes the Pipeline root span.

`GraphListener.Synchronous` observes `stage` start/end FlowNodes and creates child stage spans. Failures in graph enrichment are swallowed because telemetry must never alter Pipeline execution; root and explicit-operation telemetry remain available.

Semantic spans are explicit:

```text
BUILD, TEST, PACKAGE, SECURITY_SCAN, PUBLISH,
APPROVAL, DEPLOY, VERIFY, ROLLBACK, CUSTOM
```

## Journal format

Every signal uses ordered segments:

```text
logs-00000000000000000001.seg
metrics-00000000000000000001.seg
traces-00000000000000000001.seg
```

Each record is:

```text
8-hex-digit CRC32 + space + jenkins-telemetry/v1 JSON + newline
```

Properties:

- synchronized concurrent append;
- complete `FileChannel.write` loops;
- configurable segment and total retained-byte limits;
- optional `force()` on append;
- corrupt/incomplete tail truncation during recovery;
- monotonic per-signal sequence numbers;
- persistent signal totals independent of compaction.

## Collector cursors and compaction

Cursors are keyed by:

```text
collector ID + signal
```

One destination can accept logs while another remains behind on traces. A segment is deleted only after every currently configured collector for that signal has acknowledged a sequence at or beyond the segment's final record.

Removing a collector from configuration removes its cursor so it no longer prevents compaction.

## Asynchronous export and replay

The Pipeline path journals and schedules export; it does not perform blocking HTTP delivery.

The in-process exporter:

- batches by record and byte limits;
- applies bounded exponential backoff with jitter;
- honors collector `Retry-After` hints;
- advances a cursor only for terminal success/partial success or explicitly acknowledged permanent failure.

At build completion, a journal is placed in a persistent controller index. `TelemetrySpoolDrainer` reopens indexed journals after restart and retries destinations. A one-time bounded discovery finds journals created before the index existed.

## Console logs

Structured API logs are immediate and operation-correlated.

Jenkins console output is read from Jenkins' stored, annotation-stripped log and reconstructed as UTF-8 lines. The capture tracks byte offset, line sequence, partial final line, ANSI removal, and maximum line size. It runs incrementally when the API is invoked and captures the remainder at completion.

This design favors durable recovery over remote agent-side stream interception. Console lines use root-span correlation; explicit structured logs should be used when operation-level correlation matters.

## Metrics

Raw measurements are journaled so they survive restart. The OTLP encoder aggregates within each export batch and attribute set:

- counter: sum of deltas;
- gauge: latest value;
- histogram: count, sum, minimum, maximum, and a catch-all bucket.

Metric resources include stable service/job identity. Per-build, Git, artifact, and deployment identifiers remain on logs/traces and in exemplars rather than metric dimensions.

## Collector extension

Built-ins are Jenkins extensions:

```java
public abstract class JenkinsTelemetryCollectorFactory
        implements TelemetryCollectorFactory, ExtensionPoint {
}
```

An independently installed collector plugin can implement this type and is discovered through Jenkins' extension list. Plain Java embedders can still use `ServiceLoader<TelemetryCollectorFactory>`.

## Configuration and secrets

Configuration is loaded in this order:

1. `-Djenkins.telemetry.config=...`;
2. Jenkins global configuration path;
3. `$JENKINS_HOME/jenkins-telemetry.json`;
4. journal-only defaults.

Placeholder sources:

```text
${env:NAME}
${sys:property}
${credential:secret-text-id}
```

The credential integration is reflective and optional. HTTP transports support explicit proxy, custom truststore, and client keystore. Redaction runs before records enter the durable journal.


## Cross-run build-to-deployment lineage

A build pipeline exports `jenkins-delivery-manifest/v1` containing:

```text
release identity
all immutable artifacts
producing Jenkins run identity and URL
producing root trace ID and span ID
manifest attributes
```

The JSON encoding is deterministic and accompanied by a SHA-256. Import validates compatibility
before changing run state. The imported manifest is stored in `TelemetryRunAction`, so controller
restart reconstructs release/artifact state and default span links without duplicate telemetry.

Deployment and promotion traces are independent traces with an OpenTelemetry `SpanLink` to the
producing build root. This avoids an invalid parent-child relationship to a span that ended earlier
and allows one build to fan out into several environment-specific deployment traces.
