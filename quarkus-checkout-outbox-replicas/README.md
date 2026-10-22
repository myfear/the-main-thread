# Quarkus checkout outbox with multiple publishers

Companion for [Quarkus Outbox: How I Added a Second Publisher](https://www.the-main-thread.com/p/quarkus-outbox-multiple-publishers)

Two JVMs share PostgreSQL and Kafka. Checkout still commits an order and its event together. Publishers acquire rows with `FOR UPDATE SKIP LOCKED`, commit a lease, send to Kafka, and conditionally record completion using the claim token. Delivery remains at least once. The existing fulfillment consumer inserts at most one row per event UUID.

This extends the [original checkout outbox companion](https://github.com/myfear/the-main-thread/tree/main/quarkus-checkout-outbox). It preserves the checkout API, event format, dual-write comparison, V1 migration, and commit/post-ack crash points. V2 adds publication claims; the new `claim` crash point stops after acquisition commits.

## Prerequisites

- Java 25 and the supplied Maven Wrapper (Maven 3.9.16).
- Podman and a working `podman compose` provider. Verified with Podman 6.1.3 and podman-compose 1.5.0 on macOS ARM64.
- Bash and curl; jq for the manual inspection commands.
- Free loopback ports 8080, 8081, 15432, and 19092.

The application stays on Quarkus 3.39.4. Compose and isolated test Dev Services use PostgreSQL 18.4 and Kafka 4.2.0. The Quarkus BOM supplies Kafka client 4.2.1. No Python runtime is needed by the companion or its verification scripts.

On macOS, start your existing Podman machine if it is stopped:

```bash
podman machine start
```

On Linux, use your running Podman service/socket. Dev Services must be able to reach that engine.

## Build and verify

From this project directory:

```bash
./mvnw test
./mvnw package -DskipTests
./scripts/demo.sh
```

Tests use isolated PostgreSQL/Kafka Dev Services and disable scheduled polling. The process demo starts the dedicated Compose services, then launches the same packaged JAR on ports 8080 and 8081. Its `demo` profile disables Dev Services, enables loopback-only fault controls, and connects both JVMs to `jdbc:postgresql://localhost:15432/checkout` and `localhost:19092`.

A verified suite reports:

```text
Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
```

The process demo ends with:

```text
DEMO PASS normalEvents=6 claimCrashRecords=1 ackCrashRecords=2 ackCrashFulfillments=1 logs=target/demo/...
```

It stops only child JVMs it started. Container data remains available. Process logs and claim/completion JSON are saved under the printed `target/demo/` directory. If a previous interrupted run left pending work, the demo refuses to mix it into a new experiment; finish that work or use the scoped reset below.

If another application uses the default test ports, use:

```bash
./mvnw test -Dquarkus.http.test-port=8094 -Dquarkus.http.test-ssl-port=8095
```

## What the tests demonstrate

The deterministic baseline test holds two copies of the original publisher algorithm at a barrier after reading one unpublished row. It records two Kafka records with the same UUID and one fulfillment row. It passes by asserting that original limitation; there is no intentionally failing test.

After the change, concurrent acquisition returns disjoint batches. A separate test holds a row lock while another worker acquires a different available row. Active leases cannot be stolen; expired claims receive a different token. An old token updates zero rows after a reclaim. The send-boundary test verifies no JTA transaction is active and acquires the same row with `FOR UPDATE NOWAIT` before sending.

Crash tests retain order/outbox atomicity, stable event UUIDs, concurrent fulfillment deduplication, and propagation of unrelated foreign-key failures. Send-failure tests include a real Kafka client `RecordTooLargeException` and recovery after repairing the test payload and expiring its lease. An independent observer consumes Kafka records by event UUID and waits for the fulfillment group's committed offsets before counting fulfillment rows.

The two-process demo performs three scenarios:

1. Both workers publish from a common six-event backlog; all six complete with one acquisition and one Kafka record each.
2. A exits with status 1 after committing a claim. B's immediate attempt returns false. After database lease expiry, B acquires a new token, sends one record, and completes.
3. A exits with status 1 after Kafka acknowledgement. B later acquires a new token and republishes the same UUID. The observer finds two records, and fulfillment still has one row. Restarting A cannot reclaim the completed event.

## Run two replicas manually

Start the shared infrastructure:

```bash
podman compose up -d
```

In separate terminals, from this directory:

```bash
./scripts/start-replica.sh A 8080
```

```bash
./scripts/start-replica.sh B 8081
```

Both processes use the same built JAR. Each worker identity is its label plus a process-local UUID. Kafka advertises `localhost:19092` to host clients and `kafka:9094` to its internal listener. Do not replace the advertised host endpoint with the container-only hostname for these JVMs.

Create and inspect an order:

```bash
curl -i -H 'Content-Type: application/json' \
  -d '{"sku":"keyboard-1","quantity":1}' http://localhost:8080/orders
curl -s http://localhost:8081/outbox | jq
curl -s http://localhost:8081/fulfillments | jq
```

Expect HTTP 201 from checkout. Eventually its outbox row has a non-null `publishedAt`, cleared claim fields, and `attemptCount: 1`. `GET /orders/{id}` and `GET /fulfillments/order/{id}` retain the original behavior. `POST /orders/dual-write` remains the intentionally broken persist-then-send comparison.

## Select the crashing worker

Stop both manually launched JVMs with Ctrl+C and finish or reset any pending work. Restart both with their schedulers halted. This makes A's explicit publish request the only attempt before the crash:

```bash
SCHEDULER_START_MODE=halted LEASE_DURATION=30s ./scripts/start-replica.sh A 8080
```

```bash
SCHEDULER_START_MODE=halted LEASE_DURATION=30s ./scripts/start-replica.sh B 8081
```

In a third terminal:

```bash
order_id=$(curl -fsS -H 'Content-Type: application/json' -H 'X-Crash-After: claim' \
  -d '{"sku":"claim-crash","quantity":1}' http://localhost:8080/orders | jq -r .id)
event_id=$(curl -fsS http://localhost:8081/outbox | \
  jq -r --arg order "$order_id" '.[] | select(.aggregateid == $order) | .id')
curl -v -X POST "http://localhost:8080/demo/publish/$event_id"
curl -fsS http://localhost:8081/outbox | jq --arg id "$event_id" '.[] | select(.id == $id)'
curl -fsS -X POST "http://localhost:8081/demo/publish/$event_id"
```

A exits with status 1 and curl reports a closed connection. A publish request to B that reaches the database before `claimedUntil` returns `"published":false`. If you take longer to inspect the row, the first B request may already recover it and return `"published":true`. The row has `publishedAt: null`, an A owner, a claim token, a future deadline, and `attemptCount: 1`. After the database deadline, repeat the B publish request: it returns `"published":true`; `attemptCount` becomes 2 and the claim fields clear. B's `CLAIM` log contains a different token.

Restart A with its scheduler halted, then repeat the sequence with `X-Crash-After: kafka`. That crash occurs after broker acknowledgement. Recovery again needs lease expiry, but now Kafka has two records with the same UUID. The automated demo's independent observer proves the count. `GET /fulfillments` alone cannot distinguish one delivery from two.

Use `X-Crash-After: commit` to retain the original post-commit experiment. `POST /orders` leaves both rows durable; `/orders/dual-write` leaves only the order. Do not combine fault experiments or leave unrelated pending work: the one-shot crash switch is local to one JVM but applies to that entire JVM.

Fault controls default to disabled. Outside the explicit demo/test configuration, `/demo/*` returns 404 and crash headers return 400. Never enable these local teaching controls on an exposed deployment. To resume normal polling in the demo profile:

```bash
curl -fsS -X POST http://localhost:8081/demo/scheduler/resume
```

## Tune publication

Defaults: `checkout.outbox.batch-size=1`, `checkout.outbox.lease-duration=30s`, `checkout.outbox.poll-every=1s`. Application validation accepts 1–20 rows and leases from 1 second to 10 minutes, and rejects polling intervals below 250 milliseconds. Keep the verified one-second polling interval: support for subsecond intervals depends on the scheduler implementation, as described in the [Quarkus scheduler reference](https://quarkus.io/guides/scheduler-reference#intervals). An accepted batch/lease combination must also cover the complete sequential send budget. Kafka uses a 20-second delivery timeout, 10-second request timeout, and 5-second maximum blocking time.

The lease must cover the entire sequential batch plus time for database completion and scheduling delays. A short lease can let B send while A is still waiting. Completion tokens fence PostgreSQL updates; they cannot retract Kafka records. Failed sends retain their lease to avoid immediate retry loops. There is no renewal or automatic poison-event handling. The automated demo's eight-second lease is intentionally short for isolated local crash experiments.

Different events for one aggregate can be sent out of order across workers, even with a stable Kafka key. This example has one `OrderPlaced` per order. It provides neither strict aggregate sequencing nor an atomic PostgreSQL/Kafka commit.

## Cleanup and reset

Stop manually launched replicas with Ctrl+C. The demo script stops its own children. Then remove only this Compose project's containers and PostgreSQL volume:

```bash
podman compose down -v
```

This deletes this demo's orders, outbox, fulfillments, and broker data. It leaves unrelated Podman resources alone. Use `podman compose up -d` to start fresh. Keep both services together: retaining the database while replacing Kafka is not a durable production topology.
