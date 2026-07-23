# Flamme Release Gate

This demo puts one slow component behind a configurable Flamme boundary. The
application evaluates a release candidate through four components:

```text
POST /releases/evaluate
        |
        v
release-gateway -> candidate-validator -> risk-scorer -> release-decider
        ^                                                    |
        +---------------- CompletableFuture reply -----------+
```

With the default configuration, the whole pipeline runs in one JVM and Flamme
passes the payload through its in-process broker. Change one property on each
process and `risk-scorer` moves to a second copy of the same application. That
boundary then uses NATS and Protocol Buffers.

The demo also records the rough edges found during the experiment. In
particular, Flamme 1.0.0-SNAPSHOT still needs NATS during startup when every
component is local. See [FIELD-NOTES.md](FIELD-NOTES.md) for the commands,
output, and suggested changes.

## Versions

- Java 21
- Quarkus 3.34.1
- Flamme 1.0.0-SNAPSHOT at commit
  `8afdaf6e8b59bc3b443750cf099971593ddb66c9`
- NATS 2.14.1
- Podman 5.x

Flamme does not currently publish a release artifact. The `vendor/flamme`
directory contains the unchanged runtime and deployment sources from the pinned
commit. Only their Maven parent declarations differ so the modules can build in
this reactor. The exact provenance is in
[vendor/flamme/PIN.md](vendor/flamme/PIN.md).

## Project Layout

```text
flamme-release-gate/
├── app/                         Quarkus application and tests
│   └── src/main/proto/          Release payload schema
├── vendor/flamme/runtime/       Pinned Flamme runtime
├── vendor/flamme/deployment/    Pinned Flamme build-time module
├── FIELD-NOTES.md               Reproduced behavior and feedback
└── pom.xml                      Maven reactor
```

## Prerequisites

You need the following tools before you start:

- Java 21
- Podman with a working Podman machine on macOS or Windows
- `curl`
- Three terminals for the split-process experiment

The Maven wrapper downloads the required Maven version. The integration test
starts its own NATS Testcontainer through Podman.

## Build and Test

Run the build from this directory:

```bash
./mvnw -pl app -am test
```

The test suite checks the local Flamme pipeline, the REST validation boundary,
the deterministic risk calculation, and the forced component failure.

Package the runnable application:

```bash
./mvnw -pl app -am package
```

## Run Everything in One Process

Start NATS first:

```bash
podman run --rm --name flamme-release-gate-nats \
  -p 4222:4222 \
  -d nats:2.14.1-alpine
```

Flamme opens a NATS connection during startup even though this topology keeps
all four components local:

```bash
java -jar app/target/quarkus-app/quarkus-run.jar
```

Submit a release candidate:

```bash
curl -s \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "release-42",
    "changedFiles": 6,
    "criticalDependencies": 1,
    "forceRiskFailure": false,
    "analysisDelayMillis": 0
  }' \
  http://localhost:8080/releases/evaluate
```

Expected response:

```json
{
  "releaseId": "release-42",
  "riskScore": 27,
  "approved": true,
  "reason": "approved for release",
  "processedBy": "monolith",
  "decidedBy": "monolith"
}
```

`processedBy` and `decidedBy` make the topology visible without tracing tools.

## Move the Risk Scorer to Another Process

Stop the monolith, keep NATS running, and start the API process in the first
terminal:

```bash
java \
  -Drelease-gate.node-id=api \
  -Dflamme.services.risk-scorer.remote=true \
  -jar app/target/quarkus-app/quarkus-run.jar
```

This process keeps the validator and decider local. It publishes
`candidate-validated` to NATS because the next component is remote.

Start a second copy in another terminal:

```bash
java \
  -Drelease-gate.node-id=worker-a \
  -Dflamme.services.candidate-validator.remote=true \
  -Dflamme.services.release-decider.remote=true \
  -Dquarkus.http.port=8081 \
  -jar app/target/quarkus-app/quarkus-run.jar
```

This configuration leaves only `risk-scorer` local on the worker. Send the same
request to port 8080. The response now identifies both processes:

```json
{
  "releaseId": "release-42",
  "riskScore": 27,
  "approved": true,
  "reason": "approved for release",
  "processedBy": "worker-a",
  "decidedBy": "api"
}
```

The Java source and the packaged application stay unchanged. Only runtime
configuration decides which components subscribe locally and which subjects
cross NATS.

## Try the Failure Cases

Set `forceRiskFailure` to `true` in the request. The risk scorer throws, Flamme
logs `error invoking ...RiskScorer`, and the gateway returns HTTP 500 after the
configured three-second reply timeout. The current runtime does not send the
component exception back to the gateway.

You can also stop NATS while the split topology is running:

```bash
podman stop flamme-release-gate-nats
```

The local validator still runs. The remote stage never receives the event, and
the request ends with the same three-second timeout. The NATS client then logs a
connection-refused message roughly every two seconds while it tries to
reconnect.

## Replica Warning

Starting another worker does not create a competing-consumer group. For
example:

```bash
java \
  -Drelease-gate.node-id=worker-b \
  -Dflamme.services.candidate-validator.remote=true \
  -Dflamme.services.release-decider.remote=true \
  -Dquarkus.http.port=8082 \
  -jar app/target/quarkus-app/quarkus-run.jar
```

Both workers subscribe directly to `candidate-validated`, so both score the
same release. The API then runs the decider twice. Keep component handlers
idempotent until Flamme provides NATS queue-group support or another
load-balanced transport.

## Configuration

The defaults live in `app/src/main/resources/application.properties`:

```properties
flamme.nats.url=${NATS_URL:nats://localhost:4222}
flamme.nats.connection-name=release-gate
flamme.reply-timeout=3

release-gate.node-id=${RELEASE_GATE_NODE_ID:monolith}

quarkus.grpc.server.use-separate-server=false
quarkus.otel.sdk.disabled=true
```

`flamme.reply-timeout` uses seconds. Three seconds keeps the failure experiments
short; pick a value from the latency budget of the real operation. A long value
keeps HTTP requests and reply futures open during broker or worker failures.

The gRPC server shares the HTTP port so each process needs only one runtime
port. Flamme brings in OpenTelemetry, but this demo has no collector, so the SDK
is disabled to avoid failed export attempts. Enable it and configure an
exporter when you add traces.

## Clean Up

Stop the Java processes with `Ctrl+C`. If the NATS container is still running,
remove it with:

```bash
podman stop flamme-release-gate-nats
```

Read [FIELD-NOTES.md](FIELD-NOTES.md) before using this snapshot as the base for
a production system.
