# Build verification

Verified in the delivery environment with OpenJDK 21 while compiling all dependency-free production sources with `--release 17`.

```text
scripts/verify-all.sh
  SELFTEST PASSED
  PLUGIN SELFTEST PASSED
  Jenkins adapter and Test Harness sources compiled against structural API stubs
```

The executed suite covers:

- logs, metrics, and traces through the file collector;
- generic corporate HTTP JSON routing for all three signals;
- OTLP/HTTP JSON routing to `/v1/logs`, `/v1/metrics`, and `/v1/traces`;
- OTLP partial-success acknowledgement without duplicate replay;
- metric validation;
- counter, gauge, and histogram batch aggregation;
- trace/span exemplar correlation and metric cardinality exclusions;
- redaction in messages, attributes, span events, repository URLs, and resources;
- concurrent journal writes;
- checksummed segmented journals and acknowledged-segment compaction;
- corrupt/incomplete tail recovery;
- transient retry and per-collector cursors;
- automatic background export without Pipeline `flush()`;
- durable replay by a new engine over the same journal;
- deterministic cross-job delivery-manifest encoding and SHA-256 verification;
- imported release/artifact conflict rejection;
- build-to-deployment span links in both journal envelopes and OTLP trace payloads;
- runtime-manifest distinction between the producing build and deployment pipeline;
- Jenkins console partial-line reconstruction, completion flush, ANSI removal, and source tagging;
- Jenkins adapter compilation against structural Jenkins Pipeline API stubs.

The plugin module also contains Jenkins Test Harness integration tests:

```text
JenkinsTelemetryPipelineTest
    Executes the plugin global API in a Pipeline.
    Verifies logs, metrics, explicit operation traces, root trace, and automatic stage trace.
    Builds in one job, imports the manifest in another job, and verifies the deployment span link.

JenkinsTelemetryRestartTest
    Imports build provenance, then restarts Jenkins while an observed operation is suspended in `sleep`.
    Verifies imported manifest and span-link persistence plus abort completion after restart.
```

Maven and external dependency access were unavailable in this delivery environment. Therefore those real-Jenkins tests were not executed here and an HPI was not produced here. In a networked build environment run:

```bash
mvn clean verify
```

Expected HPI:

```text
telemetry-jenkins-plugin/target/jenkins-delivery-telemetry.hpi
```
