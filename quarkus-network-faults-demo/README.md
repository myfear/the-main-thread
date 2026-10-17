# Quarkus Network Faults quote lab

Java 25 example using Quarkus Network Faults to inject failures into a REST Client's outbound carrier connection. The tests exercise connection failures, timeouts, retries, and recovery through a shipping quote API.

## Requirements

- JDK 25 on `PATH`.
- Quarkus 4.0.0.Beta1 and Network Faults 0.1.0, pinned in `pom.xml`.
- No container runtime, carrier account, or manually started proxy.

The Maven wrapper pins Maven 3.9.16. The test resource starts a local Vert.x carrier server on an allocated loopback port and stops it after the suite. Native compilation is outside this companion's scope.

## Run the fault tests

From this module's root:

```bash
java -version
./mvnw test
```

The seven tests cover a healthy quote, refused connections and recovery, one hanging attempt, two transient HTTP errors, persistent HTTP errors, three hanging attempts, and a non-retryable HTTP error.

The API has two read-only paths:

- `GET /quotes?destination=Berlin` allows the initial carrier request and two retries.
- `GET /quotes/once?destination=Berlin` makes one carrier attempt.

Both return the carrier's EUR 9.90 quote with HTTP 200 when available, or HTTP 503 with `error=carrier_unavailable` and the fallback cause. The exception class in the error body is a lab diagnostic, not a suggested public API contract.

Each attempt has a 300 ms application timeout. The client connect and read timeouts are one second. Two 100 ms retry delays bring the three-hang case to roughly 1.1 seconds. The local timing tests allow scheduling slack and check the exception cause. Their two-second upper bound is a test assertion; the application does not implement a hard overall deadline. `@Retry(maxDuration=2000)` does not cancel an in-flight attempt at that boundary.

## Verify the packaged application

```bash
./mvnw verify -DskipITs=false
```

This runs seven fault tests, builds the fast JAR, then runs one `@QuarkusIntegrationTest`. That integration test calls the healthy carrier and checks that `target/quarkus-app/lib/main` contains no Network Faults JAR.

Network Faults 0.1.0 injects faults in `@QuarkusTest`, not in the packaged application. The packaged check supplies an ordinary carrier test server and never enables fault injection.

## Application startup outside tests

The test resource supplies `carrier.url`. Dev and production mode default to `http://127.0.0.1:9099`; they do not start a carrier server. To connect to your own HTTP carrier implementation with the same `/carrier/quotes` contract, set `CARRIER_URL` before starting Quarkus or the packaged JAR:

```bash
CARRIER_URL=http://127.0.0.1:9099 ./mvnw quarkus:dev
```

Without that service, requests return the unavailable response. Stop dev mode with Ctrl+C. All carrier and proxy resources started by the tests close automatically.

## Source map

- [Carrier client and HTTP exception mapping](src/main/java/com/themainthread/networkfaults/CarrierClient.java).
- [Single-attempt and retrying policies](src/main/java/com/themainthread/networkfaults/QuoteService.java).
- [Quote API response handling](src/main/java/com/themainthread/networkfaults/QuoteResource.java).
- [Local carrier lifecycle](src/test/java/com/themainthread/networkfaults/CarrierServer.java).
- [Fault scenarios and request counters](src/test/java/com/themainthread/networkfaults/QuoteResourceTest.java).
- [Packaged application check](src/test/java/com/themainthread/networkfaults/QuoteResourceIT.java).

## Scope

Request selection, synthetic statuses, and HTTP request counters require plaintext HTTP/1.x. `connectTimeout()` is Linux-only and is not used here. No circuit breaker, database, Kafka broker, or streaming response is included. Retries apply to an idempotent GET; adapting the policy to writes requires deduplication.
