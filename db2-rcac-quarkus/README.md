# Db2 row and column access control with Quarkus

Run a customer directory whose tenant isolation and sensitive-field masking are enforced by Db2. The same JDBC queries run through three fixed database identities. They contain no tenant predicate, and the Java repository returns the values Db2 supplies.

Companion for [Db2 Row and Column Access Control with Quarkus](https://www.the-main-thread.com/p/db2-row-column-access-control-quarkus).

The application uses Java 25, Quarkus 3.40.1 LTS, the platform-managed IBM JCC driver, and Db2 Community 12.1.5.0. It has no ORM, UI, or performance benchmark.

## Prerequisites

- Java 25 selected in your shell, including `JAVA_HOME` when required by your installation.
- Podman with a running Linux VM on macOS. Allocate four CPUs, 8 GiB of memory, and at least 15 GiB of free disk space for the image and databases.
- Bash, OpenSSL, and curl on the host.
- Permission to run the Db2 Community image under its non-production license.

Native x86-64 Linux is the reference platform. The pinned image has no ARM64 variant. On Apple Silicon, `--allow-emulation` explicitly requests Linux amd64 execution; success depends on your VM and emulator. The verification commands use real Db2 and fail when it is unavailable.

Check the Java installation from this project directory:

```bash
java -version
./mvnw -version
```

Both must report Java 25. Run every Maven command from this directory.

## Build without Db2

```bash
./mvnw verify
```

The default build compiles the application and all tests, runs the pure Java principal-routing test, and packages the application. Database security and HTTP tests are opt-in; this command alone does not establish RCAC correctness.

## Configure local credentials

Read the [Db2 Community container instructions and license requirements](https://www.ibm.com/docs/en/db2/12.1.x?topic=deployments-db2-community-edition-docker). After accepting those terms, create private local credentials:

```bash
./scripts/configure.sh --accept-license
```

This creates ignored `.env.db2` with file permissions limited to your user. The script refuses to overwrite an existing file. Alternatively, copy `.env.db2.example`, replace every password, and explicitly change `LICENSE` to `accept`.

The env file uses literal `KEY=value` entries without quotes, shell expansions, or inline comments. The scripts parse an allowlist of keys as data. They do not execute the file. A container env file does not export credentials into the host shell; the launch scripts pass only the required runtime credentials to Maven or Java.

## Start and provision Db2

On native x86-64:

```bash
./scripts/start-db2.sh
```

On Apple Silicon, explicitly enable functional emulation:

```bash
./scripts/start-db2.sh --allow-emulation
```

The script starts `db2-rcac`, binds port 50000 to localhost, and preserves data in the `db2-rcac-data` Podman volume. `DB2_PORT` in `.env.db2` changes the host port. It waits for Db2 initialization, rather than treating a running container as a ready database.

The IBM image uses `--privileged` for local instance initialization. Keep this setup confined to the local lab; it is not a production container deployment recipe. The script fixes `BLU=false` because these controls apply to an ordinary row-organized table.

Provision the application database and verify access through JDBC:

```bash
./scripts/init-db.sh RCACLAB
./scripts/verify-db2.sh RCACLAB
```

Initialization creates ordinary OS accounts outside the Db2 administration group, six fictional customers, a protected principal-to-tenant mapping, a row permission, and two `AT READ` column masks. Application data privileges are granted after both controls are activated. `db2inst1` performs setup; the application does not use its password or run DDL.

The policies and deterministic data are in `src/main/resources/sql/setup.sql`. The separate `write-probe-grants.sql` file adds only the integration database's write privileges and masking comparison.

A second initialization checks the installation marker and preserves data. Partial or incompatible installations fail. To discard a lab database, stop the application and explicitly request a reset:

```bash
./scripts/init-db.sh RCACLAB --reset-lab
```

Reset accepts only the dedicated `RCACLAB` and `RCACIT` database names. It never removes the Podman volume or another database.

## Inspect the API

Start Quarkus in development mode:

```bash
./scripts/app.sh dev
```

From another terminal in this directory:

```bash
./scripts/request.sh alice /customers
./scripts/request.sh bob /customers
./scripts/request.sh auditor /customers/101
./scripts/request.sh alice /customers/201
./scripts/request.sh unmapped /customers
curl -i http://localhost:8080/customers
```

The request helper calls curl with the private HTTP credentials. The access contract is:

- `alice` uses database user `TENA`, sees customer IDs 101–103 in tenant A, and receives null email and credit-limit fields.
- `bob` uses `TENB`, sees IDs 201–203 in tenant B, and receives the same masking.
- `auditor` uses `TENAPII`, sees tenant A only, and receives original sensitive values through the Db2 `RCAC_PII` role.
- `unmapped` authenticates but has no server-side pool mapping, so the API returns 403.
- Missing or invalid HTTP credentials produce 401.
- Foreign-tenant and nonexistent IDs produce the same 404 JSON: `{"message":"Customer not found"}`.

Tenant, datasource, and disclosure-role query parameters or headers do not select the database identity. The fixed mapping uses only the authenticated principal.

To run the packaged application, stop dev mode first:

```bash
./mvnw verify
./scripts/app.sh jar
```

HTTP Basic authentication and the embedded user store are local tutorial choices. Use a suitable identity provider and TLS when adapting the application beyond localhost.

## Run the complete database verification

Keep the HTTP application stopped while preparing and running the integration suite:

```bash
./scripts/init-db.sh RCACIT
./scripts/verify-db2.sh RCACIT
./scripts/test-db2.sh
```

`RCACIT` has the same row and column policies, but grants the tenant-A sensitive identity additional DML privileges for write probes. `RCACLAB` stays read-only. The integration database also contains a fictional `AT RESULT` masking example to demonstrate predicate inference; the API never queries it.

The suite checks raw JDBC isolation, masking and predicates, denied privilege escalation, permitted and rejected writes, pooled connection reuse, concurrent requests, HTTP authentication, and identical 404 responses. It runs `@QuarkusTest` checks and the same HTTP contract against the packaged JVM application with `@QuarkusIntegrationTest`.

Every pool has one physical connection. The tests require a stable database application handle within each pool, distinct handles between pools, and unchanged row/column visibility after SQL errors and rollback. Connections are not switched between users.

Integration tests fail when Db2 or required credentials are missing. They never select an embedded replacement database. JDBC write probes require `CURRENT SERVER` to be `RCACIT`, use local transactions, and roll back their changes.

## Understand the boundary

`CREATE PERMISSION` restricts rows using the database `SESSION_USER` and the protected tenant mapping. `CREATE MASK` returns sensitive fields only for the database disclosure role. `AT READ` applies masking before predicates, which changes the meaning of queries that filter or sort on those fields.

The Quarkus process remains trusted to authenticate the caller and select the right pool. It possesses all three pool credentials. A compromised process could deliberately choose another identity, and an administrator can change the policies. This example verifies enforcement under fixed database identities; it does not establish protection against those actors.

One pool per tenant and disclosure scope is a bounded demonstration. A system with many tenants needs a separate connection-management design. Db2 trusted contexts are a possible subject for that design; `SET SESSION AUTHORIZATION` is not an appropriate replacement for trusted pooled user switching.

## Stop the lab

Stop Quarkus with Ctrl+C, then preserve the database volume while stopping Db2:

```bash
./scripts/stop-db2.sh
```

Starting the same container again reuses the volume. Use the explicit database reset only when you intend to discard its lab data.

## Troubleshooting

- **Image or initialization failure:** inspect `podman logs db2-rcac`. Check free VM disk space and memory. On ARM64, verify that your Podman VM has an amd64 emulator configured. A pulled image is not proof that Db2 initialized.
- **Missing password:** run through the scripts, or provide the corresponding environment variable explicitly. The application never supplies a password fallback.
- **Unexpected identity or role:** application startup fails if a pool does not connect as its expected Db2 user or if its sensitive-data role differs from the configured scope. Rerun the JDBC preflight after correcting database grants.
- **Initialization refuses a rerun:** inspect the error before using `--reset-lab`. The installer will not silently replace a partially initialized schema.
- **DML probe targets the wrong database:** initialize `RCACIT` and use `scripts/test-db2.sh`; write probes refuse to run against `RCACLAB`.
- **Port conflict:** change `DB2_PORT` before creating the container. An existing container retains its original port mapping.
