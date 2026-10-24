# Controlled cutover from polling to CDC

This is an operational design note for a write-gated transition from the original polling companion. The automated scenario starts with this variant's schema; it does not execute a live migration or establish a zero-downtime rollout procedure.

## Before changing the schema

1. Identify every polling publisher and every checkout writer, including old replicas, scheduled jobs, and direct database writers. Inventory pending events using `published_at IS NULL` while the old schema exists.
2. Gate new checkout writes. Let the original publisher drain, then reconcile remaining event UUIDs against Kafka and the relevant consumers. An acknowledgement followed by a publisher crash can leave a pending row whose Kafka record already exists. Handle that uncertainty as possible duplicate delivery; do not discard pending rows.
3. Preserve a database backup and an audit of event IDs, publication markers, and the selected cutover point. Confirm that the polling backlog is reconciled. A publication marker alone does not prove a consumer completed its business effect.
4. Stop all original publishers and wait for in-flight publication attempts to finish or be reconciled. The old publisher must not update the CDC outbox after migration.

## Migrate while writes are gated

1. Apply V2, dropping the unpublished-row index and `published_at`. Deploy the CDC application against the same database with checkout writes gated.
2. Provision the restricted CDC login and the publication for the outbox table. Register the source connector with a unique slot name and `snapshot.mode=no_data`.
3. Verify the publication contents, active logical slot, connector and task states, topic route, converters, and persistent Connect internal topics. Confirm all gated writers really stayed stopped until this capture point existed.
4. Admit one controlled order with a known event UUID, observe its PostgreSQL row, raw Kafka key/header/value, and fulfillment, then resume normal traffic.

`no_data` avoids taking a data snapshot, including historical rows already sent by the poller. It also does not recover rows committed before a newly created slot's capture point. If writes must resume sooner, design a separate coordinated slot/backfill procedure and test it for the deployment. The setup scripts are not that procedure.

An `initial` snapshot can publish the existing outbox, including previously delivered rows. The fulfillment unique constraint can prevent a second row for an event UUID, but it cannot eliminate replay traffic or automatically deduplicate external effects. Choose and reconcile the snapshot policy explicitly.

## Handle a failed cutover

Keep writes gated while investigating failure. Preserve the slot, required WAL, and Connect offsets. Record the last known capture/consumer positions and reconcile event UUIDs. Do not automatically remove the connector's source history to make a failed task restart.

Do not launch the original polling binary against a database without `published_at`. Rolling back requires a reviewed schema/application plan: stop CDC, reconcile events created or delivered after the cutover, restore compatible publication bookkeeping from the saved audit or another authoritative source, and only then enable a single publication mechanism. Recreating an empty marker column would make historical events eligible for replay.

The untested deployment decisions include backup restoration, prolonged WAL loss, Kafka topic loss, database failover, credential rotation, multi-worker Connect failover, and external side effects. Test the chosen recovery plan with the actual infrastructure before a production transition.
