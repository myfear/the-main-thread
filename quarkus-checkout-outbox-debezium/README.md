# A Quarkus transactional outbox with Debezium

Commit a checkout order and its event together in PostgreSQL. An independent Debezium PostgreSQL connector in Kafka Connect publishes the event to Kafka, and the Quarkus fulfillment consumer inserts one row per event UUID.

Companion to [Replace the Quarkus Outbox Poller with Debezium](https://www.the-main-thread.com/p/quarkus-outbox-debezium).

This evolves the [original polling companion](https://github.com/myfear/the-main-thread/tree/main/quarkus-checkout-outbox). The multiple-publisher companion is an alternative evolution of that original. This variant has no Java producer, scheduled relay, claim, or lease.

## Prerequisites

Use Java 25, Podman with a running engine, a `podman compose` provider, Bash, curl, and jq. On macOS or Windows, start your existing Podman machine first. The project includes Maven Wrapper 3.9.16.

The application uses Quarkus 3.39.4. The shared environment pins:

- PostgreSQL: `docker.io/library/postgres:18.4-alpine3.24`.
- Kafka: `docker.io/apache/kafka:4.3.1`.
- Debezium Connect: `quay.io/debezium/connect:3.7.0.Final`, containing Kafka Connect 4.3.1.

The Compose stack uses persistent project-scoped volumes. Its host ports bind to loopback: PostgreSQL 25432, Kafka 29092, and Connect 28083. The demo application uses 18086. Change the corresponding variables in `.env` if those ports are occupied. Run commands from this project directory.

## Build the application

```bash
cp .env.example .env
./mvnw package
```

The example credentials are for this local environment. `.env` is ignored. The application tests use isolated PostgreSQL and Kafka Dev Services; they verify transaction boundaries, request validation, the deserializer, and consumer deduplication. They do not start Debezium.

## Start and initialize CDC

Start the shared infrastructure:

```bash
./scripts/infra-up.sh
```

In one terminal, start the packaged application. Flyway applies V1 and V2 before the outbox can be captured:

```bash
./scripts/run-app.sh
```

After the application starts, run these commands in another terminal:

```bash
./scripts/setup-cdc.sh
./scripts/register-connector.sh
```

The first script provisions the demo replication login and an insert-only publication containing only `public.outbox_event`. Its SQL runs outside Flyway and can be repeated. The second script renders the password from `.env`, validates the configuration with Connect, registers the connector, and waits for both its task and replication slot. It prints configuration errors without printing the credentials.

Wait for the connector and its one task to show `RUNNING` before creating orders. The connector uses `snapshot.mode=no_data`: it captures changes from its slot, and does not snapshot existing outbox rows. Orders created before a fresh slot exists need an explicit backfill or controlled cutover.

## Place and inspect an order

```bash
curl -fsS http://127.0.0.1:18086/orders \
  -H 'Content-Type: application/json' \
  -d '{"sku":"keyboard-cdc","quantity":1}' | jq .

curl -fsS http://127.0.0.1:18086/outbox | jq .
curl -fsS http://127.0.0.1:18086/fulfillments | jq .
./scripts/status.sh
```

A successful checkout returns `201 Created` and a `Location: /orders/{id}` header. Use its ID with `GET /orders/{id}` and `GET /fulfillments/order/{orderId}`. Fulfillment becomes visible asynchronously.

`GET /outbox` shows committed rows. Its response no longer contains `publishedAt`, and the outbox row remains after delivery. The API's `payload` property is the stored JSON string; the Kafka value is a JSON object with `eventId`, `orderId`, `sku`, `quantity`, and `placedAt`.

Inspect a raw record independently of the application:

```bash
podman compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 --topic order-placed --from-beginning \
  --max-messages 1 --property print.key=true --property print.headers=true
```

The key is the order ID as a string. The `id` header is the event UUID encoded as UTF-8, matching `eventId` in the value. The console command reads the first retained record; the automated suite checks records for each newly created UUID.

## Repeat the failure experiments

Stop any manually started application before running the suite. Leave the project's infrastructure available, or let the suite start it:

```bash
./scripts/verify-cdc.sh
```

The Java runner launches the packaged application and performs eight scenarios:

1. Check logical replication, the restricted CDC login, the publication, and compacted Connect internal topics with local replication factor 1.
2. Verify the raw Kafka topic, key, UUID header, JSON payload, and eventual fulfillment.
3. Halt checkout immediately after commit. Read the committed event from Kafka while the JVM is stopped, then restart the consumer and check fulfillment.
4. Stop only Connect, commit three orders, observe unchanged topic end offsets during a bounded three-second window, then restart Connect and check all three UUIDs.
5. Recreate only the Connect container, keeping Kafka-backed configuration and offsets, then process another order.
6. Publish a deliberate duplicate through a dedicated verification client, wait for the fulfillment group's committed offset, and check for one fulfillment row.
7. Roll back a database transaction containing both rows, then observe a later committed event without receiving the aborted event. The application tests separately exercise rollback through `CheckoutService`.
8. Start a second, temporary slot with `no_data`. Check that it captures a new event without backfilling an earlier outbox row. The temporary connector and slot are removed afterward.

The runner uses an independent Kafka consumer with explicit partition assignment and its own observation offsets. Only the duplicate scenario produces a record from Java; checkout and all normal/recovery events go through Debezium. Reports, raw event details, slot samples, and logs are saved under `target/cdc-verification/`. The runner stops the JVM it launched and leaves the main CDC stack available for inspection. A failed suite returns a nonzero exit code.

For the manual checkout crash, with the demo running:

```bash
curl -v http://127.0.0.1:18086/orders \
  -H 'Content-Type: application/json' -H 'X-Crash-After: commit' \
  -d '{"sku":"checkout-crash","quantity":1}'
```

The connection drops and the JVM exits with status 1 after commit. Connect can publish the saved event while checkout is stopped. Restart with `./scripts/run-app.sh` to resume fulfillment. Crash controls are disabled outside the demo and test profiles. The old `kafka` crash option and `/orders/dual-write` operation have been removed.

To stop and restart only Connect:

```bash
podman compose stop connect
# Place orders and inspect PostgreSQL while Connect is stopped.
podman compose start connect
./scripts/status.sh
```

To recreate the worker without deleting broker state:

```bash
podman compose up -d --no-deps --force-recreate connect
./scripts/status.sh
```

## Understand the boundaries

The database transaction atomically stores the order and event. The connector publishes after commit, and its source offsets are separate from the fulfillment consumer's offsets. A repeated Kafka record can reach the consumer, so the unique `fulfillment.event_id` constraint and targeted `ON CONFLICT (event_id) DO NOTHING` are retained. This demo does not claim end-to-end exactly-once processing or deduplicate an external warehouse call.

A retained logical slot can hold WAL during an outage. `max_slot_wal_keep_size=256MB` is a local resource decision, checked at PostgreSQL checkpoints, rather than a guarantee of recovery for an arbitrary outage or a strict instantaneous disk cap. If required WAL is removed, the slot needs operator recovery. WAL distance includes other database activity; it is not the number or exact byte size of undelivered outbox events.

The single broker and worker, replication factor 1, plaintext listeners, unauthenticated loopback Connect API, local passwords, and database-owner application login are demonstration choices. A deployment needs appropriate database permissions, secret injection, Kafka/Connect security, retention, monitoring, and infrastructure resilience. See the reader-facing [architecture](docs/architecture.md) and [controlled cutover](docs/cutover.md) notes.

## Stop and clean up

Stop the foreground application with Ctrl+C. Stop the stack while preserving its data:

```bash
podman compose down
```

For a deliberate reset of this project's database, replication slots, Kafka topics, and Connect offsets:

```bash
podman compose down -v
```

After a reset, follow the complete initialization order again. Removing a slot or offsets during an outage is not a recovery procedure.
