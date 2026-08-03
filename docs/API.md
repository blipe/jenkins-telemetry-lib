# Jenkinsfile API

The plugin contributes `jenkinsTelemetry` directly. No `@Library` declaration and no top-level mutable API object are required.

## Release identity

```groovy
jenkinsTelemetry.release(
    serviceNamespace: 'commerce',
    serviceName: 'orders',
    serviceVersion: '1.4.7',
    sourceRepository: 'ssh://git.example.com/commerce/orders.git',
    sourceRevision: env.GIT_COMMIT,
    sourceRef: env.BRANCH_NAME,
    buildVersion: env.BUILD_NUMBER,
    attributes: [owner: 'checkout-platform']
)
```

When omitted, the plugin discovers available Jenkins and SCM values including job, run ID, build number, build URL, `GIT_URL`, `GIT_COMMIT`, and branch/tag environment values. Explicit values override discovery.

## Observe an operation

```groovy
jenkinsTelemetry.observe(
    name: 'Integration tests',
    kind: 'TEST',
    attributes: [database: 'postgres']
) { op ->
    op.log('Starting integration suite')
    sh './mvnw verify -Pintegration'
    op.count('tests.completed', 42, [suite: 'integration'])
    op.measure('tests.duration', 92.4, 's', [suite: 'integration'])
    op.event('test.report.created', [path: 'target/report.json'])
}
```

Supported operation kinds include:

```text
PIPELINE, STAGE, CHECKOUT, BUILD, TEST, PACKAGE, SECURITY_SCAN,
QUALITY_GATE, PUBLISH, APPROVAL, DEPLOY, VERIFY, ROLLBACK, CUSTOM
```

An exception marks the span as failed and is rethrown unchanged where possible.

Nested operations retain the explicit parent:

```groovy
jenkinsTelemetry.observe(name: 'Build', kind: 'BUILD') { build ->
    build.observe(name: 'Compile', kind: 'BUILD') {
        sh './mvnw compile'
    }
}
```

## Logs

```groovy
jenkinsTelemetry.log('Root-correlated message')
jenkinsTelemetry.log('WARN', 'Registry is slow', [elapsedMillis: 4200])

jenkinsTelemetry.observe(name: 'Publish', kind: 'PUBLISH') { op ->
    op.log('Publishing artifact', [repository: 'libs-release-local'])
    op.logs().error('Publish failed', [status: 503])
}
```

Available severities are `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, and `FATAL`.

## Metrics

Simplified API:

```groovy
jenkinsTelemetry.count('jenkins.cache.hits', 12, [cache: 'maven'])
jenkinsTelemetry.measure('jenkins.artifact.size', 1048576, 'By')

jenkinsTelemetry.observe(name: 'Tests', kind: 'TEST') { op ->
    op.count('tests.executed', 150, [suite: 'unit'])
    op.measure('tests.duration', 11.8, 's', [suite: 'unit'])
}
```

Full instrument API:

```groovy
jenkinsTelemetry.metrics()
    .counter('jenkins.cache.hits', '{hit}', 'Build cache hits')
    .add(12, [cache: 'maven'])

jenkinsTelemetry.metrics()
    .histogram('jenkins.artifact.size', 'By', 'Artifact size')
    .record(1048576)

jenkinsTelemetry.metrics()
    .gauge('jenkins.test.coverage', '1', 'Line coverage')
    .set(0.87)
```

Counters are monotonic deltas and must be non-negative. Gauges are recorded observations, not callbacks, so no live Jenkins object is retained across restart.

## Artifacts

### File

```groovy
jenkinsTelemetry.artifacts().file(
    logicalName: 'primary',
    name: 'orders.jar',
    version: env.BUILD_NUMBER,
    digest: sha256,
    uri: artifactUrl,
    attributes: [sizeBytes: bytes]
)
```

### Maven / Artifactory

```groovy
jenkinsTelemetry.artifacts().maven(
    logicalName: 'primary',
    repository: 'libs-release-local',
    groupId: 'com.acme.commerce',
    artifactId: 'orders',
    version: version,
    digest: sha256,
    uri: artifactUrl
)
```

### OCI

```groovy
jenkinsTelemetry.artifacts().oci(
    logicalName: 'primary',
    repository: 'registry.example.com',
    image: 'commerce/orders',
    version: env.GIT_COMMIT,
    digest: 'sha256:...',
    uri: 'registry.example.com/commerce/orders@sha256:...'
)
```

An immutable digest is required for meaningful deployment correlation.

## Deployment

```groovy
jenkinsTelemetry.deployment(
    deploymentId: deploymentId,
    environment: 'production',
    target: 'orders',
    cluster: 'corp-us-central',
    namespace: 'commerce-prod',
    artifactUri: imageDigestReference,
    artifactDigest: digest
) { op ->
    sh 'kubectl apply -f deployment.yaml'
    sh 'kubectl rollout status deployment/orders --timeout=10m'
    op.event('deployment.verified', [digest: digest])
}
```

The library observes deployment lifecycle; it does not implement deployment control flow.

## Cross-job delivery manifest

Build/publish job:

```groovy
Map delivery = jenkinsTelemetry.exportDeliveryManifest(
    file: 'delivery-manifest.json',
    attributes: [releaseChannel: 'production-candidate']
)
writeFile file: 'delivery-manifest.sha256',
    text: "${delivery.sha256}  delivery-manifest.json\n"
```

The export requires a release identity and at least one immutable artifact. It returns:

```text
delivery.values
delivery.json
delivery.sha256
```

Promotion/deployment job:

```groovy
String expectedSha256 = readFile('delivery-manifest.sha256').trim().tokenize()[0]
Map imported = jenkinsTelemetry.importDeliveryManifest(
    file: 'delivery-manifest.json',
    sha256: expectedSha256
)
```

`json:` may be supplied instead of `file:`. Import:

- verifies the optional SHA-256 before parsing;
- restores the producing release and every artifact;
- rejects service-name, service-version, source-revision, and artifact-digest conflicts;
- persists imported lineage across controller restart;
- links the deploy root and subsequent operation spans to the producing build root span.

The deploy span is linked, not parented, because the producing build trace normally completed before
promotion started.

## Runtime manifest

```groovy
Map manifest = jenkinsTelemetry.runtimeManifest(
    environment: 'production',
    deploymentId: deploymentId,
    file: 'release-manifest.json'
)

echo manifest.environment.OTEL_SERVICE_NAME
echo manifest.environment.OTEL_RESOURCE_ATTRIBUTES
```

The result contains:

```text
manifest.values
manifest.json
manifest.environment.OTEL_SERVICE_NAME
manifest.environment.OTEL_RESOURCE_ATTRIBUTES
```

Inject these values into the deployed process so runtime logs, metrics, and traces retain the same release and deployment identity.

## Status and diagnostics

```groovy
Map status = jenkinsTelemetry.status()
```

`status` exposes retained bytes, signal counts, dropped records, and per-collector lag.

```groovy
jenkinsTelemetry.flush()
```

`flush()` is diagnostic/test-only. Normal pipelines rely on asynchronous export and controller replay.
