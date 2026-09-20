# MDC across asynchronous boundaries in Quarkus

Companion project for Keep Request IDs Across Async Boundaries in Quarkus

The demo distinguishes MicroProfile context propagation from Vert.x-backed MDC. In this application, `ThreadContext.withContextCapture()` alone does not restore MDC on an unmanaged executor. The working library bridge also returns the continuation to the saved Vert.x request context.

## Requirements

- Java 21; validated with Temurin 21.0.8
- Quarkus 3.39.4, pinned in `pom.xml`
- `curl`, `jq`, and Bash for manual checks
- No external services or containers

The project was scaffolded with Quarkus CLI 3.39.1. The generated Maven wrapper uses Maven 3.9.16.

## Run

From this directory:

```bash
quarkus dev
```

Then, in another terminal:

```bash
curl --fail --silent --show-error \
  -H 'X-Request-ID: bridge-123' \
  http://localhost:8080/mdc/bridged | jq

bash scripts/check-concurrent.sh
```

The library observation has `requestId: null`; the continuation has `requestId: "bridge-123"`. A thread number is not part of the API contract.

For another port, use `quarkus dev -Dquarkus.http.port=8086`, change the curl URL, and pass `http://localhost:8086` to the script.

## Endpoints

- `GET /mdc/common-pool`: raw supplier loses MDC; HTTP 200.
- `GET /mdc/managed`: directly submitted managed task preserves MDC; HTTP 200.
- `GET /mdc/mutiny`: synchronous source followed by `emitOn` on Quarkus's executor preserves MDC; HTTP 200.
- `GET /mdc/uncaptured`: raw library and continuation both report `null`; HTTP 200.
- `GET /mdc/captured`: MicroProfile capture alone still leaves both MDC observations `null` with this stack; HTTP 200.
- `GET /mdc/bridged`: library reports `null`; continuation on the saved Vert.x context recovers the request ID; HTTP 200.
- `GET /mdc/managed-failure`: deliberately throws a handled exception from a managed task; returns the correlated observation with HTTP 503.

Responses after the request filter include the selected `X-Request-ID`. This header comes from request metadata, so it remains correct even on deliberately broken propagation paths. Inspect the JSON observation or log message to test MDC.

The filter accepts one visible value matching `[A-Za-z0-9._:-]{1,128}` and otherwise generates a UUID. Upstream HTTP processing can normalize repeated fields before the filter sees them. IDs are untrusted correlation labels, not authentication or authorization data.

## Tests and packaging

Stop dev mode before the packaged test run:

```bash
./mvnw verify -DskipITs=false
java -jar target/quarkus-app/quarkus-run.jar
```

`MdcResourceTest` has 11 HTTP tests, including 140 requests in batches of 20 across all seven endpoints. `MdcResourceIT` inherits the same checks and runs them against the built JVM application. Both suites passed; see [VALIDATION.md](VALIDATION.md).

The standalone script checks 100 bridged requests with up to 20 concurrent clients. It exits nonzero on connection errors, HTTP errors, timeouts, malformed JSON, or mismatched IDs.

The article includes every application file and the standalone script. The companion repository adds the larger regression suite. Native execution was not tested.

Stop a running application with `Ctrl+C`. There are no external resources to clean up.

## Scope

The bridged callback runs on the request's event loop and must stay nonblocking. The saved context belongs to one request and must not be cached or retained for background jobs. The demo neither propagates headers to another service nor implements distributed tracing.

