# Virtual threads in Quarkus

Inspect Quarkus REST dispatch and run bounded concurrent subtasks with deadlines and cancellation. Uses JDK 25 and Quarkus 3.40.1.2.

Companion to [Virtual Threads in Quarkus: REST, Concurrency, and Practical Limits](https://www.the-main-thread.com/p/quarkus-virtual-threads-java-tutorial).

## Run

Set `JAVA_HOME` to JDK 25. The included Maven wrapper downloads Maven and dependencies on its first invocation. No database, broker, or container engine is needed.

```sh
./mvnw quarkus:dev
```

Call the endpoints from another terminal:

```sh
curl --fail-with-body -sS http://localhost:8080/api/demo/event-loop
curl --fail-with-body -sS http://localhost:8080/api/demo/worker
curl --fail-with-body -sS http://localhost:8080/api/demo/hello
curl --fail-with-body -sS http://localhost:8080/api/demo/query
curl --fail-with-body -sS 'http://localhost:8080/api/demo/parallel/10?delayMs=500&timeoutMs=2000'
```

The first two responses have `virtual: false`; the next two have `virtual: true`. Thread names distinguish the event loop from the worker executor. Names and numeric suffixes vary between runs. `/query` waits 500 ms without starting child threads.

The batch endpoint returns `completed`, `elapsedMs`, and `allVirtual`. Ten 500 ms sleeps overlap; elapsed time includes scheduling and collection overhead. This measures one batch inside one HTTP request. It is not an HTTP-throughput benchmark or a database simulation with connection limits.

## Batch limits

`count` is 1–32; `delayMs` is 0–2000 (default 500); `timeoutMs` is 1–5000 (default 2000). Invalid values return 400. At most four batches per application instance run concurrently; another batch receives 503 with `Retry-After: 1`.

An expired batch returns 504 and unfinished tasks are cancelled. Sleeping subtasks cooperate with interruption. The executor's close waits for termination, so this deadline is not a guarantee that arbitrary interruption-insensitive code will return within the same duration. A child failure maps to 500 after batch collection; this example does not implement fail-fast sibling cancellation.

The service creates at most 128 child virtual threads across admitted batches. That limit does not include request threads or other application threads. The batch limiter does not limit the `/query` endpoint. Raw child executors do not automatically establish Quarkus request or transaction context; these children only sleep and return a boolean.

## Build and verify

Stop dev mode, then run:

```sh
./mvnw verify -DskipITs=false
java -jar target/quarkus-app/quarkus-run.jar
```

Keep the entire `target/quarkus-app` directory when copying the application. Tests cover dispatch, parameter bounds, task completion, deadline cancellation, failure propagation, interrupt preservation, and overload. The HTTP suite also runs against the packaged JVM application.

## Optional JVM container

After packaging, with Podman available:

```sh
podman build -f src/main/docker/Dockerfile.jvm -t virtual-threads-demo .
podman run --rm -p 8080:8080 virtual-threads-demo
```

Kafka and native-image builds are outside this companion's verification scope. See the [Quarkus messaging guide](https://quarkus.io/guides/messaging-virtual-threads/) for broker-specific configuration and the [virtual-thread reference](https://quarkus.io/guides/virtual-threads/) for pinning tests, context, and metrics.
