# PGlite with Endive on the JVM

Run an embedded PostgreSQL 17.5 database inside a Quarkus JVM application through
Endive. The Java adapter initializes a fresh database, exchanges PostgreSQL wire
messages, handles transactions, and snapshots data for a graceful restart.

Read [PGlite in Quarkus: The Java Adapter Behind Embedded PostgreSQL](https://www.the-main-thread.com/p/pglite-quarkus-endive)
for the adapter design and its integration boundaries.

This prototype uses PGlite's experimental WASI build with the
[wasipg SQL error recovery patches](https://github.com/moznion/wasipg/tree/fa290be67946c0d3c4540bd1400b11e193d874e1/build/patches).
The standard JavaScript PGlite package depends on Emscripten host functions.
The patched WASI artifact enables PostgreSQL's `setjmp`/`longjmp` recovery through
standardized WebAssembly exception handling. The JVM host is Java; the Go host
from wasipg is not used.

## Build and verify

Requirements: macOS or Linux, JDK 25, Bash, `curl`, `tar`, `shasum`, and network
access for the first dependency download. The sources target Java 21; JVM
verification uses Temurin 25 on macOS arm64. Maven is provided by the module's wrapper. The
compressed WASI bundle is included, so running the prototype does not require a
container engine or a native PostgreSQL installation.

From this module's directory, install the pinned Quarkus Endive extension and run
the JVM verification suite:

```bash
./scripts/bootstrap-endive.sh
./mvnw verify
```

Quarkus Endive has no Maven Central release at the pinned commit. The bootstrap
script verifies its source archive and installs it as the local version
`0.0.0-c5494f6e`, with Endive 1.1.0 and Quarkus 3.37.3. This version identifies the
prototype's local build; it is not an upstream release. The application imports
the Endive BOM to keep the runtime, WASI implementation, and compiler aligned.

The build applies Quarkus Java formatting and import sorting during
`process-sources`. Surefire tests exercise the Java adapter in `@QuarkusTest`;
Failsafe runs the HTTP contract against the packaged JVM application with
`@QuarkusIntegrationTest` and verifies persistence across two independent JVM
processes. Results are written to `target/surefire-reports` and
`target/failsafe-reports`.

## Run SQL

Start the application in dev mode:

```bash
./mvnw quarkus:dev
```

The first database request compiles the WASM module and runs `initdb`. Endive
compiles most functions to JVM bytecode and uses its interpreter for the large
PostgreSQL parser function. The compiled factory is reused when the Java adapter
opens another database.

Create a table and insert a row through the local SQL endpoint:

```bash
curl --fail-with-body http://127.0.0.1:8097/pglite/sql \
  -H 'Content-Type: application/json' --data-binary @- <<'JSON'
{"sql":"CREATE TABLE IF NOT EXISTS notes (id int PRIMARY KEY, body text); INSERT INTO notes VALUES (1, 'hello from WASM') ON CONFLICT DO NOTHING; SELECT * FROM notes"}
JSON
```

Each statement has its own result with `columns`, `rows`, and a PostgreSQL command
tag. Values use PostgreSQL's text format; SQL `NULL` becomes JSON `null`.

Run a group of statements in one transaction:

```bash
curl --fail-with-body http://127.0.0.1:8097/pglite/transaction \
  -H 'Content-Type: application/json' --data-binary @- <<'JSON'
{"statements":["INSERT INTO notes VALUES (2, 'committed')","UPDATE notes SET body = 'updated' WHERE id = 1"]}
JSON
```

The adapter holds the session lock for the whole transaction callback. A Java
exception or SQL error triggers `ROLLBACK` when a transaction remains active.
Use `/pglite/transaction` to group statements that leave transaction control to
the wrapper. A callback can issue `COMMIT`, then `BEGIN`, pass the final state
check, and return success after splitting the transaction. The API does not
enforce transaction ownership.

`/pglite/sql` exposes the one underlying session, including explicit `BEGIN`,
`COMMIT`, and `ROLLBACK`, for experiments. Transactions opened by one request
can include statements from another caller; session settings and temporary
objects are shared too. Keep the raw SQL API restricted to trusted local use.

Observe an SQL error and then query the same instance:

```bash
curl http://127.0.0.1:8097/pglite/sql \
  -H 'Content-Type: application/json' -d '{"sql":"SELECT 1 / 0"}'
curl --fail-with-body http://127.0.0.1:8097/pglite/sql \
  -H 'Content-Type: application/json' -d '{"sql":"SELECT 42 AS answer"}'
curl --fail-with-body http://127.0.0.1:8097/pglite
```

The first response has HTTP status 400 and SQLSTATE `22012`. The next query
returns `42`. `GET /pglite` exposes an instance ID and transaction status so you
can check that recovery kept the same session alive. PostgreSQL transaction
statuses are `I` (idle), `T` (in a transaction), and `E` (an aborted transaction).
After an error in an explicit transaction, PostgreSQL rejects further statements
with `25P02` until you issue `ROLLBACK`.

## Persist across graceful restarts

The WASI guest uses an isolated Jimfs filesystem. On clean shutdown, the adapter
rolls back an unfinished transaction, executes `CHECKPOINT`, calls PGlite's shutdown export,
copies `PGDATA` into a new snapshot, and atomically replaces its `CURRENT`
pointer. Reopening loads that snapshot into fresh WASM memory. A file lock
prevents two database instances from opening the same persistence directory.
The adapter explicitly sets the normal `"$user", public` search path after
authentication: the experimental WASI resume path otherwise leaves the session
with `search_path=pg_catalog`, hiding application tables.

The default directory is `target/pglite-data`. Override it to keep data outside
Maven's build output:

```bash
java -Dpglite.data-directory=./local-data \
  -jar target/quarkus-app/quarkus-run.jar
```

Stop the application gracefully, restart it with the same directory, and query
`notes` again. Snapshots are written on close, not on every commit. A crash or
forced termination can lose changes made since the last clean shutdown. This
prototype does not establish crash durability or compatibility with a normal
PostgreSQL server's data directory.

## Scope and provenance

The adapter implements the simple-query protocol, text results, one connection,
and synchronous transaction callbacks. Parameter binding, JDBC, Hibernate,
extension loading, streaming results, and native-image execution are outside
this prototype. The HTTP API accepts raw SQL for local experiments and binds to
loopback by default.

The bundle SHA-256 is checked before execution. Source commits, toolchain
versions, hashes, and build adaptations are recorded in
[`provenance/bundle.json`](provenance/bundle.json). The C patches are included in
[`provenance/patches`](provenance/patches), and upstream notices are in
[`licenses`](licenses). The artifact was built temporarily with Podman; the
delivered application and its verification use Java.

The integration uses a dynamically loaded payload and the named
`WasmQuarkusContext` from the
[Quarkus Endive extension](https://github.com/quarkiverse/quarkus-endive/tree/c5494f6e9702943702c79c4b0e6e92863e9ca77e).
See the [Endive WASI documentation](https://endive.run/docs/wasi/),
[Quarkus datasource guide](https://quarkus.io/guides/datasource), and
[Quarkus testing guide](https://quarkus.io/guides/getting-started-testing) for the
surrounding APIs. This adapter exposes its own SQL API. Quarkus JDBC datasource
configuration does not apply to it.
