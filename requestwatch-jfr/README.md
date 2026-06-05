# requestwatch-jfr

Small Quarkus service for a JFR tutorial about performance bugs that hide from logs and coarse metrics.

The sample gives you four useful shapes:

- `GET /requests/fast` - a boring control endpoint
- `GET /requests/blocking` - a request path that holds a global lock while it waits
- `GET /requests/blocking-fixed` - the same work with the slow part moved outside the lock
- `GET /requests/allocating` - a request that creates a pile of temporary `byte[]` buffers

The app also does one ugly thing at startup on purpose so a recording taken from JVM boot has something real to show.

## Prerequisites

- JDK 25
- Maven 3.9+
- Podman, if you want to follow the container section

## Run in dev mode

```bash
./mvnw quarkus:dev
```

If you want a recording from startup, launch dev mode with JFR enabled:

```bash
./mvnw quarkus:dev \
  -Djvm.args="-XX:StartFlightRecording=name=requestwatch,settings=profile,dumponexit=true,filename=target/requestwatch-dev.jfr"
```

Stop the app with `q` or `Ctrl+C` and the recording lands in `target/requestwatch-dev.jfr`.

## Trigger the interesting paths

Fast path:

```bash
curl -s http://localhost:8080/requests/fast
```

Blocking path with four concurrent requests:

```bash
for i in 1 2 3 4; do
  curl -s http://localhost:8080/requests/blocking &
done
wait
```

Fixed version of the same path:

```bash
for i in 1 2 3 4; do
  curl -s http://localhost:8080/requests/blocking-fixed &
done
wait
```

Allocation-heavy path:

```bash
curl -s http://localhost:8080/requests/allocating
```

The blocking responses include `elapsedMs`, so you can see the serialized path get much worse under concurrent traffic.

## Read the recording

Quick CLI checks:

```bash
jfr summary target/requestwatch-dev.jfr
jfr print --categories quarkus target/requestwatch-dev.jfr
jfr print --events jdk.ThreadSleep,jdk.JavaMonitorEnter target/requestwatch-dev.jfr
jfr print --events jdk.ObjectAllocationInNewTLAB,jdk.ObjectAllocationOutsideTLAB target/requestwatch-dev.jfr
```

If you prefer a UI, open the file in JDK Mission Control and add the Quarkus lane in the Threads view so request activity and startup events are easier to spot.

## Run the tests

```bash
./mvnw test
```

The test suite proves that the fixed endpoint avoids the serialized latency shape from the bad endpoint.

## Build and run with Podman

Build the image through Quarkus:

```bash
./mvnw install -Dquarkus.container-image.build=true
```

Run it with JFR enabled and write the recording to the host:

```bash
mkdir -p recordings

podman run --rm -p 8080:8080 \
  -v "$(pwd)/recordings:/recordings" \
  -e JAVA_OPTS_APPEND="-XX:StartFlightRecording=name=requestwatch,settings=profile,dumponexit=true,filename=/recordings/requestwatch-container.jfr" \
  requestwatch/requestwatch-jfr:1.0.0
```

That `JAVA_OPTS_APPEND` hook works because the generated JVM container uses Red Hat's `run-java.sh` entrypoint.

## Related guides

- [Writing JSON REST Services](https://quarkus.io/guides/rest-json)
- [Using JDK Flight Recorder](https://quarkus.io/guides/jfr)
- [Container Images](https://quarkus.io/guides/container-image)

The article draft for this sample lives in [article.md](article.md).
