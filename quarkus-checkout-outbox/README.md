# Checkout outbox

Companion project for *Give Quarkus Checkout a Transactional Outbox*

Checkout writes a `purchase_orders` row and an `outbox_event` row in one PostgreSQL transaction, then a scheduler publishes `OrderPlaced` to Kafka. A consumer uses `ON CONFLICT (event_id) DO NOTHING` to insert at most one fulfillment row per event. Other constraint failures propagate. Scheduled polls do not overlap within one JVM. `POST /orders/dual-write` is the broken path: persist, commit, then send.

This example uses Quarkus 3.39.4 and Java 25. Dev Services starts PostgreSQL 18.4 and Apache Kafka 4.2.0.

## Requirements

- Java 25
- The Maven Wrapper in this directory (`./mvnw`)
- Podman, for Dev Services

## Run

```bash
export TESTCONTAINERS_REUSE_ENABLE=true
./mvnw quarkus:dev
```

Enable reuse before the first launch and keep the same database configuration on every restart. In a new shell, export the variable again. Without reuse, the disposable PostgreSQL database does not provide the restart demonstration. Record the PostgreSQL container ID in the startup log for cleanup. Tests explicitly disable database reuse and Kafka sharing is disabled.

Then:

```bash
curl -i -H 'Content-Type: application/json' \
  -d '{"sku":"keyboard-1","quantity":1}' \
  http://localhost:8080/orders
```

You should see `201 Created` and a JSON order. Within about a second, `GET /outbox` shows `publishedAt` set and `GET /fulfillments` shows one row for that event id.

## Crash windows

Run these requests one at a time, with no pending outbox backlog. The crash switch is application-wide and is intended only for this local demonstration.

`X-Crash-After: commit` calls `Runtime.halt(1)` after the database transaction commits. `X-Crash-After: kafka` halts after Kafka acknowledges the record and before `published_at` is stored.

Dual-write, event lost:

```bash
curl -i -H 'Content-Type: application/json' -H 'X-Crash-After: commit' \
  -d '{"sku":"lost-event-1","quantity":1}' \
  http://localhost:8080/orders/dual-write
```

Outbox, event recovered after restart:

```bash
curl -i -H 'Content-Type: application/json' -H 'X-Crash-After: commit' \
  -d '{"sku":"headset-1","quantity":1}' \
  http://localhost:8080/orders
```

Restart with `./mvnw quarkus:dev`, then inspect `GET /orders/{id}`, `GET /outbox`, and `GET /fulfillments`. Tests set `checkout.crash.halt=false` so Surefire catches `CrashWindowException` instead of dying.

## Tests

```bash
./mvnw test
```

Look for `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`. The test profile disables the scheduler and drives `OutboxPublisher` directly.

When another Quarkus test uses the default port, run:

```bash
./mvnw test -Dquarkus.http.test-port=8094 -Dquarkus.http.test-ssl-port=8095
```

The tests cover concurrent duplicates and foreign-key failures. The Kafka replay test waits for consumer group offsets to commit past the second delivery before asserting one fulfillment.

## Cleanup

Stop the application with `Ctrl+C`. The reused PostgreSQL container stays running. Remove only the PostgreSQL container whose ID you recorded at startup:

```bash
podman rm -f <postgres-container-id>
```

This removes the demo database and stored orders. Dev Services/Testcontainers cleans up the non-reused Kafka container. The next run starts with an empty database.

## Guides

- [Apache Kafka reference](https://quarkus.io/guides/kafka)
- [Dev Services for Kafka](https://quarkus.io/guides/kafka-dev-services)
- [Scheduling periodic tasks](https://quarkus.io/guides/scheduler)
- [Using Flyway](https://quarkus.io/guides/flyway)
- [Debezium outbox event router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html)
