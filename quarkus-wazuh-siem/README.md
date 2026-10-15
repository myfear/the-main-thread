# Quarkus security events in Wazuh

Run a Quarkus application that records authentication, denied access, and a
simulated administrative export as JSON events. A local Wazuh manager collects
the file, Filebeat forwards alerts to the indexer, and the dashboard lets you
search them. Quarkus enforces authentication and roles; Wazuh collects and
correlates the resulting events for security information and event management
(SIEM). The sample uses Quarkus **3.40.1**, Java **21**, and Wazuh **4.14.8**.

## Prerequisites

- JDK 21 and `JAVA_HOME`; Maven is supplied by `./mvnw`.
- [Podman](https://podman.io/docs/installation), with its Linux VM started on macOS.
- A [Compose V2 provider](https://podman-desktop.io/docs/compose/setting-up-compose).
  Select it with `export PODMAN_COMPOSE_PROVIDER=/absolute/path/to/docker-compose`.
  `podman compose version --short` must report `2.x`.
- Bash, `curl`, and `jq`.
- A Linux container host with at least 4 CPUs, 8 GB RAM and 50 GB disk capacity,
  as recommended by Wazuh. Allocate memory to the Podman VM on macOS.
- Linux `vm.max_map_count` of at least `262144`. On macOS, check with
  `podman machine ssh sysctl vm.max_map_count`. If smaller, set it inside the VM
  with `podman machine ssh sudo sysctl -w vm.max_map_count=262144`, and configure
  persistence there if your VM resets it after reboot.
- An API socket Java/Testcontainers can access. Podman normally supplies
  `/var/run/docker.sock` on macOS; otherwise set `DOCKER_HOST` to the Unix socket
  reported by `podman machine inspect`. On rootless Linux, start the user Podman
  socket and set `DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock`.

The three Wazuh images and certificate generator have native ARM64 and AMD64 builds.
The application binds `127.0.0.1:8088`; the dashboard binds `127.0.0.1:8443`.
Manager and indexer ports are not published. Public sample passwords are for this
isolated lab; do not expose it to a shared network.

## Build and run

Run the commands from the `quarkus-wazuh-siem` directory.

1. Build the application and run its tests:

   ```sh
   ./mvnw verify -DskipITs=false
   ```

   Tests disable Compose and need no Wazuh containers. The packaged application
   test checks that it refuses the development accounts. To run only the unit
   and in-process HTTP tests, use `./mvnw test`.

2. Prepare the audit file and local TLS certificates:

   ```sh
   ./scripts/bootstrap-wazuh.sh
   ```

   Bootstrap creates the audit file before the manager mounts its directory.
   Repeating bootstrap retains complete certificates.

3. Start the application and Wazuh:

   ```sh
   ./mvnw quarkus:dev
   ```

   Quarkus manages the stack through Compose Dev Services. Allow several minutes
   for the first image pulls and health checks. The application listens on
   `http://127.0.0.1:8088`, and the dashboard on `https://localhost:8443`.

## Send requests and inspect alerts

In a second terminal in the same directory, select the same Compose provider and
send the sample requests:

```sh
./scripts/simulate-attacks.sh
./scripts/verify-wazuh.sh
```

The first script expects HTTP `200`, `401`, `403`, and `200`, then sends five
failed logins. The second runs the opt-in Java pipeline test against the running
application and containers. It checks rule matching, correlation, and indexed
alerts for fresh requests. It also briefly stops and restarts the manager to
check that an export recorded locally during the interruption reaches the index.

To follow a request yourself, send an export and inspect the local JSON records:

```sh
curl -i -u operator:operator-dev-only -X POST http://127.0.0.1:8088/api/admin/export
jq -c . target/siem/security-events.jsonl
```

Copy the `X-Audit-Request-ID` response header. The same ID appears in the local
record's `request_id` field and lets you find the request in Wazuh.

Open [Wazuh Dashboard](https://localhost:8443), review its self-signed certificate
warning, and sign in with `admin` / `SecretPassword`. In **Threat hunting**,
select a recent time range and search `rule.groups:quarkus`. Application fields
appear under `data`, including `data.audit_event_type`, `data.principal`,
`data.source_ip`, and `data.request_id`. Filter `data.request_id` by the ID you copied.
Authentication-success records remain local; they have no sample alert rule.

## Application and configuration

`GET /api/profile` requires `user`; `POST /api/admin/export` requires `admin`.
`alice` / `alice-dev-only` has `user`; `operator` / `operator-dev-only` has
`user,admin`. Accounts and HTTP Basic are configured only in `%dev` and `%test`.
The export returns a simulated completed result with zero records.

The flat JSON contract contains schema version, event/request UUIDs, UTC time,
service, `audit_event_type`, outcome, principal, peer IP, method and known route.
Failed authentication has no validated principal. Credentials, claimed usernames,
query strings, exception messages, bodies, cookies and inbound request IDs are
excluded. Forwarded addresses are not trusted.

`com.example.siem.audit` uses a named synchronous file handler with `%s%n` and no
parent handlers. Rotation defaults to 10 MB and three backups, with boot rotation
disabled. Test and production profiles use separate files.

The application uses `quarkus-observability-devservices` to bring in Compose
deployment infrastructure. `quarkus.observability.enabled=false` disables its default
telemetry stack, so only the three Wazuh services start.

To run the application alone: `WAZUH_COMPOSE_ENABLED=false ./mvnw quarkus:dev`.

## Reload configuration

Wazuh imports `/wazuh-config-mount` into persistent configuration and retains existing
rule files on container restart. After editing the sample's manager config or rules:

```sh
./scripts/reload-wazuh-config.sh
./scripts/verify-wazuh.sh
```

Reload replaces this sample's active manager configuration and custom rules with
the versioned files, then restarts Wazuh processes. Do not use this script to manage
a separately administered manager.

## Optional: Check file rotation

Stop dev mode and wait for container shutdown, then restart with:

```sh
QUARKUS_LOG_HANDLER_FILE_AUDIT_ROTATION_MAX_FILE_SIZE=2K ./mvnw quarkus:dev
```

In the second terminal run `WAZUH_VERIFY_ROTATION=true ./scripts/verify-wazuh.sh`.
Java triggers actual rollover with HTTP requests and waits for an indexed export
after rollover. It does not assert that every event emitted during rotation was
retained. Restart without the override to restore 10 MB.

## Stop and reset

Press `Ctrl+C` to stop dev mode and wait for container shutdown. Quarkus retains named
volumes. Bootstrap and another dev start reuse certificates/state. Stop leftover
sample containers with `./scripts/cleanup-wazuh.sh`. For fresh Wazuh state, stop
dev mode first, then run `./scripts/cleanup-wazuh.sh --reset`. This removes only
this Compose project's containers/volumes. Local audit files and certificates
remain; a new manager may reread retained JSON. Remove the sample audit files
explicitly if you need empty event history. Do not run `mvn clean` during dev mode:
it deletes the mounted `target` directory.

## Troubleshoot startup and collection

- If the audit file stops receiving records after Dev MCP continuous testing,
  restart dev mode before running the pipeline test.
- Rule `86600` instead of the custom rules indicates an old contract containing
  `event_type`. Wazuh's Suricata grouping rule consumes it; update the app and rules
  together to use `audit_event_type`.
- On macOS the generator may fail its manager-CA copy after changing shared-mount
  ownership. Bootstrap restores owner access and copies the same root CA. It
  keeps the host directory mode `700` and makes individual development PEM mounts
  readable to container users. Linux retains generator UID ownership; diagnose
  rootless UID mapping if an image cannot read keys. `:z` permits SELinux shared mounts.
- Check socket/provider setup first. Inspect the stack with
  `podman compose -p quarkus-wazuh-siem -f infra/wazuh/compose.yml ps`, or replace
  `ps` with `logs --tail=100`.

## Production limits

- Synchronous logging adds request latency and can lose events through disk
  failures, rollover, abrupt shutdown or host failure. It supplies neither
  transactional persistence, `fsync`, nor exactly-once delivery. Wazuh can replay
  records, and correlation depends on ingestion timing and order.
- Production needs a real identity provider, trusted proxy configuration,
  restricted storage, retention/privacy policy and a durable pipeline or outbox
  where audit completeness is mandatory. Keep authorization in Quarkus; this
  sample has no active response.

## Third-party configuration

The configuration under `infra/wazuh/` is adapted from the official
[Wazuh 4.14.8 single-node deployment](https://github.com/wazuh/wazuh-docker/tree/v4.14.8/single-node).
The [upstream GPLv2 license](infra/wazuh/LICENSE) and copyright notices are retained.
The adaptation uses Podman, loopback-only dashboard access, health checks, and a
read-only mount for the application's audit file. Bootstrap generates development
keys and certificates locally; they are excluded from Git.
