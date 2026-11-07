# Live order context with Quarkus and Kafka Streams

Build a local order view from checkout, inventory, and shipping events. Query materialized Kafka Streams state through `GET /orders/{orderId}/context`; observe missing facts, source-revision conflicts, and independent heartbeat evidence.

Companion to [Quarkus and Kafka Streams: Build a Live Order Context API](https://www.the-main-thread.com/p/quarkus-streamhouse-order-context).

## Requirements

- Java **25**; the build targets Java 25.
- Podman with a running machine on macOS or Windows.
- Bash, curl, and jq for the demonstrations. The Maven Wrapper is included.

The project pins Quarkus **3.40.1.1**. Its BOM supplies Kafka Streams/test-utils **4.2.1**. Dev Services supplies Kafka automatically; the verified default broker image was `apache/kafka-native:4.2.0`. No PostgreSQL, Python, LLM, cloud account, or earlier application is required.

## Run

Run all commands from this directory. On macOS, start the Podman machine if needed:

```bash
podman machine start
```

If the Java container client cannot discover Podman, expose its compatible socket in this terminal:

```bash
export DOCKER_HOST="unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

`DOCKER_HOST` is the client API variable; Podman remains the container engine. On Linux, use the socket configured for your Podman service instead. See the [Quarkus Podman guide](https://quarkus.io/guides/podman/) for platform-specific setup.

Start the application:

```bash
./mvnw quarkus:dev
```

Dev Services creates `order-placed`, `inventory-status`, `shipping-status`, and `source-heartbeats`, each with one partition. The broker is isolated from other demos. Wait until this returns HTTP 200:

```bash
curl -i http://localhost:8080/q/health/ready
```

In another terminal, run the experiments once against a fresh broker:

```bash
./scripts/demo.sh
./scripts/demo-stale-source.sh
```

The first script shows checkout-only state, a complete order with shipping revision 2, suppressed old and duplicate updates, and a conflict on a separate order. It uses order IDs 1042 and 1043. The second sends one heartbeat for each source, then continues checkout/inventory heartbeats every 10 seconds while shipping stops. After the default 30-second threshold, shipping becomes `STALE`; the complete business view remains available. Both scripts assert their results and have bounded waits.

If you use another HTTP port, set `BASE_URL`, for example:

```bash
BASE_URL=http://localhost:8094 ./scripts/demo.sh
```

`ORDER_ID` changes the sample order ID; the first script also uses the following ID as a processing barrier. Its fixed event IDs are fixtures for repeatable retries. Use a fresh broker for a full rerun: experiment A deliberately expects a previously unseen order.

The scripts contain the actual requests. For one standalone placement:

```bash
curl --fail-with-body -i -H 'Content-Type: application/json' \
  --data '{"eventId":"74c6c857-4dd0-4e05-bdc0-577ba374f7d2","orderId":1042,"sku":"keyboard-1","quantity":1,"placedAt":"2026-10-27T13:10:00Z"}' \
  http://localhost:8080/_demo/order-placed
curl --fail http://localhost:8080/orders/1042/context | jq .
```

A publisher response of **204** confirms broker acknowledgement. Processing is asynchronous; poll the context endpoint as the scripts do. If publishing returns **503**, acknowledgement was not confirmed and delivery is uncertain. Retry the same event, including UUID and revision.

## Contracts and behavior

Business Kafka keys are canonical decimal order IDs. They must match the payload's positive `orderId`. Events require UUID `eventId` values and instant timestamps. The publisher emits ISO-8601 timestamps.

- `order-placed`: `eventId`, `orderId`, nonblank `sku`, positive `quantity`, `placedAt`. The original outbox contract is unchanged. Checkout means `PLACED`; there is no payment status.
- `inventory-status`: `eventId`, `orderId`, positive `revision`, `status` (`RESERVED` or `REJECTED`), `occurredAt`.
- `shipping-status`: the same revisioned fields, with status `PACKED`, `IN_TRANSIT`, `DELAYED`, or `DELIVERED`.
- `source-heartbeats`: `source` (`checkout`, `inventory`, or `shipping`) and `emittedAt`, keyed by source name.

Each revisioned source owns its sequence **per order**. Higher revisions replace its fact; lower revisions cannot replace it even with a newer timestamp. Equal revision/status/time is idempotent, including when its event UUID differs. Equal revision with changed status or time records a conflict and preserves the stored fact. Checkout requires identical UUID and content for a duplicate. Conflicts stay visible until an operator repairs/rebuilds the projection; a later revision does not clear them.

Any source can arrive first. `complete` requires all three facts and no conflict. It does not mean successfully delivered: a `REJECTED` inventory fact still counts as a reported fact. `missingSources` names absent facts. There is no growing list of processed UUIDs.

Heartbeats are independent of business-event dates. More than five seconds of future skew is rejected before updating progress and logged; older valid heartbeats do not move progress backward. `UNKNOWN` means no usable evidence, `RECENT` means within the configured age/skew allowance, and `STALE` means older than that age. These values describe source reporting, not consumer lag. `catchUpVerified` is always false.

`order-context-store` and `source-heartbeat-store` use local RocksDB storage backed by Kafka changelogs. A request performs local key lookups. The two reads do not form an atomic cross-source snapshot.

The HTTP endpoint returns 200 for any known order, including partial/conflicted orders; 404 means no processed events for that ID; 503 means stores are unavailable. Errors use `code` and `message`. A 503 includes `Retry-After: 1`. The endpoint makes no internal retry loop.

Malformed JSON, unknown enums, missing required values, nonpositive revisions, key mismatches, and business tombstones are processing faults. They stop processing visibly rather than silently discarding business facts. Production needs a controlled quarantine and recovery policy. The strict schema also rejects unknown properties; coordinate schema evolution before adding fields.

## Tests and packaged application

Stop dev mode before running `clean`:

```bash
./mvnw clean verify
```

This runs reducer, serialization, topology, clock, HTTP, dev-publisher, and health tests, then `@QuarkusIntegrationTest` against the packaged JVM application. Real Kafka Dev Services brokers are used for application tests. Failsafe is enabled by default. Test-profile and packaged tests check that all four demo POST routes return 404. No native-image claim is made.

Without a broker, only the deterministic suite can run:

```bash
./mvnw test -Dtest=OrderReducerTest,LivenessEvaluationTest,OrderTopologyTest,JsonSerdesTest
```

A packaged application needs an externally managed broker and all four topics. Use one instance and one partition per topic for this example:

```bash
export KAFKA_BOOTSTRAP_SERVERS=your-broker:9092
java -Dkafka-streams.state.dir=./state -jar target/quarkus-app/quarkus-run.jar
```

Replace `your-broker:9092` with the actual broker address. The production build has no accessible `/_demo` routes and no configured outgoing publisher channels. Configure authentication, per-order/tenant authorization, TLS, Kafka access control, retention, replication, and disk monitoring before exposing the read API.

## Use the earlier checkout producer

The [checkout/outbox companion](https://github.com/myfear/the-main-thread/tree/main/quarkus-checkout-outbox) can publish `order-placed` directly. Configure `KAFKA_BOOTSTRAP_SERVERS` for both applications to the same broker, use separate HTTP ports, and ensure all four topics exist. Do not publish a simulated placement for an order ID already owned by real checkout. Inventory and shipping still require their own producers; checkout's existing application does not implement this heartbeat contract. A monitor or source change must provide actual checkout heartbeats, otherwise its signal remains `UNKNOWN`. Do not invent heartbeat evidence on its behalf.

## Recovery and deployment limits

Kafka retains the changelogs needed to rebuild local stores, subject to the cluster's retention, replication, and access policies. To verify restoration manually, keep an external broker alive, populate an order, stop the application gracefully, then restart the **same application ID** with a **new empty local state directory**. Query the order and inspect nonzero changelog-restoration counts in the startup log. Merely restarting with existing local files does not prove changelog restoration. A removed Dev Services container cannot restore its deleted Kafka data.

Interactive queries are local. Multiple instances need ownership metadata and request routing, plus a design for making source-wide heartbeat evidence available to each serving node, or a separate serving store. Keep matching business-topic partition counts and compatible key partitioners. Rebalances and restoration can return 503. Store cardinality grows with retained orders; this demo has no expiry or deletion policy. Design tombstones, changelog retention, disk capacity, restoration time, and consumer-lag monitoring together.

`/q/health/ready` checks topic availability and store queryability. `/q/health/live` includes Streams state. Neither proves that each source is reporting or that business topics have caught up. A production action policy needs stronger progress evidence and authorization than this response supplies.

## Stop and reset

Press Ctrl+C in the dev terminal. Dev Services owns the disposable broker and stops it with that session. With dev mode stopped, remove this demo's local state by running:

```bash
./mvnw clean
```

The next isolated dev session starts with a new broker; do not expect retained orders. Against an external retained broker, deleting local state alone does **not** erase orders: they can be restored. Changing an application ID creates a new consumer group and internal topics and can replay retained inputs. Use an explicit reset/retention plan for that environment.
