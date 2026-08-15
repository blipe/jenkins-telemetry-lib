# Jenkins Delivery Telemetry

A Jenkins plugin and Java API for **logs, metrics, and traces** correlated with the exact build, source revision, immutable artifact, and deployment.

It does not define pipeline control flow. Jenkinsfiles keep ordinary `stage`, `sh`, `parallel`, Artifactory, Docker, Kubernetes, approval, retry, and rollback logic. The plugin observes that logic through a small API and automatic Jenkins lifecycle instrumentation.

## Production shape

Install one HPI. No separately configured Shared Library is required.

The plugin provides:

- the global Pipeline variable `jenkinsTelemetry`;
- structured logs and durable Jenkins console-log capture;
- counters, gauges, histogram measurements, and trace exemplars;
- root Pipeline spans, automatic stage spans, explicit operation/deployment spans;
- release, artifact, and runtime-manifest correlation;
- portable delivery manifests for independent build, promotion, and deployment jobs;
- trace links from deployment spans to the completed producing-build trace;
- a checksummed segmented journal under each build;
- asynchronous export that never blocks normal Pipeline steps;
- controller-level replay of journals left by completed builds or outages;
- independent collector cursors for logs, metrics, and traces;
- pluggable Jenkins collector extensions plus built-in file, HTTP JSON, and OTLP/HTTP JSON collectors;
- redaction before journaling;
- proxy, custom truststore, and client-keystore/mTLS configuration;
- `${credential:ID}`, `${env:NAME}`, and `${sys:property}` placeholders.

The implementation does not require the Jenkins OpenTelemetry plugin.

## Minimal Jenkinsfile

```groovy
pipeline {
    agent any

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    // Jenkins and Git fields are discovered where available.
                    jenkinsTelemetry.release(
                        serviceNamespace: 'commerce',
                        serviceName: 'orders'
                    )
                }
            }
        }

        stage('Build') {
            steps {
                script {
                    jenkinsTelemetry.observe(name: 'Maven verify', kind: 'BUILD') { op ->
                        op.log('Starting build')
                        sh './mvnw -B clean verify'
                        op.count('jenkins.builds.completed', 1)
                    }
                }
            }
        }
    }
}
```

Normal Jenkinsfiles do not call `flush()`. Every record is journaled first and background workers export it.

## Complete examples

- [`examples/plain-java/Jenkinsfile`](examples/plain-java/Jenkinsfile)
- [`examples/artifactory/Jenkinsfile`](examples/artifactory/Jenkinsfile)
- [`examples/docker-image/Jenkinsfile`](examples/docker-image/Jenkinsfile)
- [`examples/kubernetes/Jenkinsfile`](examples/kubernetes/Jenkinsfile)
- [`examples/promotion/build/Jenkinsfile`](examples/promotion/build/Jenkinsfile)
- [`examples/promotion/deploy/Jenkinsfile`](examples/promotion/deploy/Jenkinsfile)

The Kubernetes example verifies the digest from each running pod's actual `imageID`, not only the Deployment template.


## Separate build and deploy jobs

A producing pipeline exports a deterministic delivery manifest after recording immutable artifacts:

```groovy
def delivery = jenkinsTelemetry.exportDeliveryManifest(
    file: 'delivery-manifest.json'
)
writeFile file: 'delivery-manifest.sha256',
    text: "${delivery.sha256}  delivery-manifest.json\n"
archiveArtifacts artifacts: 'delivery-manifest.json,delivery-manifest.sha256', fingerprint: true
```

A promotion/deployment pipeline retrieves both files and imports them before deployment:

```groovy
String expected = readFile('delivery-manifest.sha256').trim().tokenize()[0]
def imported = jenkinsTelemetry.importDeliveryManifest(
    file: 'delivery-manifest.json',
    sha256: expected
)
```

Import restores the original release and artifacts, rejects conflicting service/revision/digest
values, and adds an OpenTelemetry span link from deployment operations to the completed producing
build trace. The deploy trace remains an independent trace, which is correct when the build trace
has already ended.

The SHA-256 must be transported through an independently trusted value or a repository with its own
integrity controls. A digest fetched from the same untrusted location as the manifest detects
accidental corruption but does not authenticate the producer.

## Build

The plugin targets Java 17 and Jenkins 2.516.3:

```bash
mvn clean verify
```

The HPI is produced at:

```text
telemetry-jenkins-plugin/target/jenkins-delivery-telemetry.hpi
```

A dependency-free verification suite is included for restricted environments:

```bash
scripts/verify-all.sh
```

## Install and configure

1. Install the generated HPI.
2. In **Manage Jenkins → System → Jenkins Delivery Telemetry**, optionally set the JSON configuration path and automatic-export switch.
3. Otherwise place configuration at:

```text
$JENKINS_HOME/jenkins-telemetry.json
```

The system property below takes precedence:

```text
-Djenkins.telemetry.config=/approved/path/jenkins-telemetry.json
```

With no configuration file, telemetry remains in the per-build journal and no outbound connection is attempted.

## OTLP collector

```json
{
  "journal": {
    "maxBytes": 1073741824,
    "segmentBytes": 8388608,
    "forceWrites": false
  },
  "export": {
    "enabled": true,
    "batchRecords": 512,
    "batchBytes": 2097152,
    "timeoutMillis": 30000,
    "retryInitialMillis": 1000,
    "retryMaxMillis": 60000
  },
  "collectors": [
    {
      "id": "corp-otlp",
      "type": "otlp-http-json",
      "signals": ["logs", "metrics", "traces"],
      "config": {
        "endpoint": "https://otel-gateway.example.com:4318",
        "gzip": "true",
        "header.Authorization": "Bearer ${credential:jenkins-telemetry-otlp-token}"
      }
    }
  ]
}
```

The OTLP collector sends to `/v1/logs`, `/v1/metrics`, and `/v1/traces`. It handles partial-success responses, retryable status codes, and `Retry-After`.

`${credential:...}` resolves a Jenkins Secret Text credential when the optional Credentials and Plain Credentials plugins are installed. The telemetry plugin has no hard dependency on either plugin.

## Corporate proxy and TLS

Any HTTP collector may use:

```json
{
  "proxyHost": "proxy.example.com",
  "proxyPort": "8080",
  "trustStorePath": "/etc/jenkins/telemetry-trust.p12",
  "trustStoreType": "PKCS12",
  "trustStorePassword": "${credential:telemetry-truststore-password}",
  "keyStorePath": "/etc/jenkins/telemetry-client.p12",
  "keyStoreType": "PKCS12",
  "keyStorePassword": "${credential:telemetry-client-keystore-password}",
  "keyPassword": "${credential:telemetry-client-key-password}"
}
```

Resolved secrets are retained only while constructing the HTTP client and are never written into telemetry records or journal metadata.

## Durable journal

Each build owns:

```text
$BUILD_ROOT/telemetry/journal/
    logs-00000000000000000001.seg
    metrics-00000000000000000001.seg
    traces-00000000000000000001.seg
    cursors.properties
    stats.properties
```

Records are length-independent, CRC-framed JSON lines. Startup truncates a corrupt or partially written final record. Each collector advances independently per signal. A segment is removed only after every active collector for that signal has acknowledged it.

Completed journals with remaining collector lag are registered in a controller-level pending index and retried by a background drainer after restart or destination recovery.

## Signal behavior

### Logs

- `jenkinsTelemetry.log(...)` and `op.log(...)` are recorded immediately.
- Jenkins' stored, annotation-stripped console log is captured incrementally when the API runs and completely when the build finishes.
- Console records retain sequence, build, release, and root trace correlation.

### Metrics

- Counters reject negative deltas.
- Metric names and finite numeric values are validated.
- OTLP batches aggregate counters and histograms by attribute set.
- Gauges export the latest value per attribute set in a batch.
- Build IDs, URLs, Git revisions, artifact digests, and deployment IDs are not injected into metric attributes.
- Trace/span exemplars preserve drill-through correlation without creating one metric series per build.

### Traces

- A Pipeline root span is automatic.
- Scripted and Declarative `stage` blocks are automatic.
- Semantic build, test, publish, deployment, verification, and rollback operations use `observe(...)`.
- Only serializable detached IDs and value objects cross Pipeline suspension points.

## Collector SPI

Collectors installed inside the HPI may use Java `ServiceLoader`. Independently installed Jenkins collector plugins should implement:

```java
public abstract class JenkinsTelemetryCollectorFactory
        implements TelemetryCollectorFactory, ExtensionPoint {
}
```

Each collector advertises supported signals and receives batches through the common `TelemetryCollector` API.

## Current boundaries

- Jenkins Test Harness tests are included for Pipeline execution and controller restart, but this delivery environment could not download Maven/Jenkins dependencies to execute them or produce an HPI.
- Console capture is durable rather than truly live; console lines are correlated to the Pipeline root, while explicit structured logs carry operation-level correlation.
- Automatic topology currently covers Pipeline root and stages. Individual Jenkins steps, queue wait, agent allocation, and parallel-branch spans remain candidates for later enrichment.
- Delivery manifests provide digest verification and provenance linking, but not cryptographic producer authentication; use an independently trusted digest or an authenticated artifact repository.
- The plugin emits telemetry but does not provide a dashboard; collectors/backends own visualization.

See [`BUILD-VERIFICATION.md`](BUILD-VERIFICATION.md) for the exact verification performed here.
