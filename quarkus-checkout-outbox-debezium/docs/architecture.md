# Transaction and recovery boundaries

Checkout calls `CheckoutService.persistOrderAndOutbox` through Quarkus's transactional interceptor. Both inserts commit before control returns to `place`. A crash selected for that request occurs afterward. There is no Kafka producer in the application.

PostgreSQL logical decoding exposes committed changes through `pgoutput`. The pre-created `checkout_outbox_pub` publication emits inserts for `public.outbox_event` only. The `checkout_cdc` login has LOGIN and REPLICATION, database CONNECT, schema USAGE, and SELECT on that table; it is not a superuser. The local `checkout` owner prepares the role and publication after Flyway, outside the application migration.

The `checkout_outbox_slot` replication slot keeps a capture position and prevents removal of WAL the slot needs, subject to PostgreSQL's configured limits and slot validity. The Connect worker stores connector configuration, source offsets, and status in Kafka's compacted internal topics. Replacing the worker container preserves those topics because the broker has a separate persistent volume. Both the source slot and Kafka-backed connector state are relevant to recovery.

The Outbox Event Router extracts the stored JSON and metadata. It routes to `order-placed`, converts `aggregateid` to the string key, and emits the outbox UUID in the `id` header. JSON expansion plus a schema-less JsonConverter produces a top-level object, retaining the existing `placedAt` field. No extra metadata is placed into the value envelope. The standard `OrderPlacedDeserializer` remains unchanged.

The fulfillment consumer uses its own Kafka group, `checkout-fulfillment`, and commits its SQL effect in a separate transaction. Repeated event IDs are rejected by `fulfillment.event_id` uniqueness. The SQL targets that constraint, allowing unrelated failures such as a missing referenced order to propagate. This protects the demonstrated database insert; an external side effect needs its own idempotency design.

## Failure behavior

- **Checkout rollback:** neither order nor outbox insertion commits, and logical decoding does not publish the aborted insert.
- **Checkout stops after commit:** the order/event survives in PostgreSQL. Connect can publish independently. The consumer in the same stopped JVM resumes on restart.
- **Connect stops:** checkout accepts orders. Its slot becomes inactive and PostgreSQL retains needed WAL within the configured limits. The worker resumes from its durable source state after restart when the required WAL and offsets survive.
- **Worker container replaced:** configuration and source offsets come from Kafka internal topics. Container recreation alone does not create a new CDC history.
- **Ambiguous publication or consumer redelivery:** Kafka may contain repeated records. Consumer deduplication handles the repeated UUID. No end-to-end exactly-once guarantee is asserted.
- **Slot invalidated or offsets lost:** ordinary restart is insufficient. Restore/reconcile state and design a backfill or snapshot procedure before resuming. Blindly creating a new slot with `no_data` can omit historical events.

## WAL and retention

`restart_lsn` identifies the oldest WAL the slot might require. `confirmed_flush_lsn` is the position acknowledged by the logical consumer. The query in `config/debezium/slot-status.sql` samples their values and the distance from the current WAL position to `restart_lsn`. A null position during slot initialization has no numeric distance. Distance measures retained-log pressure across WAL activity, not business-event count or exact disk allocation.

The local 256 MB slot retention setting is enforced around checkpoints. It can make old WAL eligible for removal and render a lagging slot unusable. Monitor available disk, slot validity, capture lag, connector task failures, and offset persistence together. PostgreSQL 18's idle-slot timeout, when enabled by an operator, also affects prolonged outages. This small test does not measure maximum sustainable outage time or throughput.

A quiet captured table can also lag in acknowledging WAL while other database activity generates log records. Review Debezium heartbeats for that workload. A heartbeat table and `heartbeat.action.query` require deliberate publication/filter/SMT handling; this demo does not add that second capture path. Do not apply the outbox SMT indiscriminately to heartbeat records.

The outbox is insert-only on the application path and retains its rows. Row count has no per-event delivery meaning once `published_at` is removed. Archive/delete policies need explicit recovery requirements and a verified relationship to capture progress. This example has no cleanup scheduler. Deleting a row does not acknowledge downstream processing.

## Ordering and scaling

A stable aggregate key preserves the partition choice while the partition count and key mapping stay stable. This example emits one `OrderPlaced` per order, so it does not demonstrate ordering across multiple events for one aggregate. Kafka reconfiguration and consumer concurrency require separate analysis.

The PostgreSQL connector uses one task. Increasing `tasks.max` does not parallelize a logical replication stream. Connect can manage failover across workers in an appropriate deployment, but this one-worker local stack does not demonstrate that topology.

## Why an independent Compose stack

Quarkus Dev Services remains the application-test path. Quarkus also supports Compose Dev Services; it was considered for the shared experiment. The process scenarios intentionally kill checkout, stop or replace Connect independently, and retain infrastructure between launches. Explicit `podman compose` commands make those lifecycles visible and reproducible from the packaged application. The demo profile disables Dev Services and sets both shared endpoints, avoiding a second isolated broker or database.

References: [Debezium PostgreSQL connector](https://debezium.io/documentation/reference/3.7/connectors/postgresql.html), [Outbox Event Router](https://debezium.io/documentation/reference/3.7/transformations/outbox-event-router.html), [PostgreSQL logical replication settings](https://www.postgresql.org/docs/18/logical-replication-config.html), and [PostgreSQL replication slots](https://www.postgresql.org/docs/18/view-pg-replication-slots.html).
