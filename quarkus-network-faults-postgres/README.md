# Quarkus PostgreSQL network faults lab

Save shipping quotes with Hibernate ORM, then interrupt the PostgreSQL connection to explore JDBC timeouts, Agroal pool exhaustion, stale connections, and failed transactions.

Companion to [When PostgreSQL Stops Answering: Exploring Quarkus Connection Pools](https://www.the-main-thread.com/p/quarkus-network-faults-postgresql-pools).

## Start the lab

You need JDK 25, the Quarkus CLI, a running Podman engine, curl, and Bash. Run commands from this module directory:

```bash
quarkus dev --no-debug
```

The build uses Quarkus **4.0.0.Beta1** and Network Faults **0.2.0**. Dev Services starts `docker.io/library/postgres:18`; the verified image contained PostgreSQL **18.4**. The application listens on port **8097**. No separate proxy container is needed.

Open the [Network Faults Dev UI](http://localhost:8097/q/dev-ui/quarkus-network-faults/network-faults). The JDBC target is `datasource:<default>:jdbc`.

The database is disposable: Hibernate recreates the schema at dev-mode startup. End an active fault before editing code or restarting the application. On macOS, start your configured Podman machine before launching Quarkus; Dev Services needs a reachable container API.

## Explore the application

```bash
curl -sS -X POST 'http://localhost:8097/quotes?destination=Berlin'
curl -sS http://localhost:8097/quotes
curl -sS http://localhost:8097/quotes/count
curl -sS http://localhost:8097/quotes/preview
curl -sS -X POST http://localhost:8097/lab/pool/warm
```

`POST /quotes` saves a quote and returns HTTP 201. `GET /quotes` lists saved rows; `/quotes/count` counts them. `/quotes/preview` returns a fixed EUR 9.90 price without borrowing a database connection. It makes no external carrier call.

The pool holds at most two connections. `/lab/pool/warm` borrows both simultaneously, runs `select 1`, and returns them. `/lab/pool` exposes `active`, `available`, `awaiting`, `created`, and `destroyed` Agroal counters. Both lab routes exist only in dev and test profiles.

The lab maps database failures to HTTP 503 with a cause class and SQL state. These fields help distinguish the failures below; they are diagnostic output, not a proposed production error contract.

## Experiment 1: a query holds its connection

Warm the pool. In Dev UI, enter `hangs:next-write` in the target's fault selector, press Enter, and click **Apply**. Then run:

```bash
curl -sS --max-time 15 -w '\nHTTP %{http_code}; elapsed %{time_total}s\n' \
  http://localhost:8097/quotes/count
```

The verified run returned 503 after about **5 seconds**, with `PSQLException` and SQL state `08006`. The configured pgJDBC `socketTimeout=5` bounds a socket read. The one-second pool acquisition timeout does not interrupt a query after it has borrowed a connection.

Click **End**, then warm the pool again.

## Experiment 2: two held connections exhaust the pool

Apply `hangs:next-write` again after warming the pool, then run:

```bash
bash scripts/pool-pressure.sh
```

The script starts two database requests, waits until the pool reports two active connections, and sends a third request plus a database-independent preview request. It prints responses and timing; it does not turn the observations into a pass/fail test.

In the verified run:

- The third database request returned 503 after about **1 second**, with `SQLException` from pool acquisition.
- The preview returned 200 in about **2 ms**.
- Both earlier database requests returned 503 after about **5 seconds**, with `PSQLException`.

The preview demonstrates separation from this database pool under two blocked requests. It does not establish isolation under sustained load or exhaustion of shared HTTP execution resources.

Click **End**, then warm the pool again.

## Experiment 3: stale pooled connections

With no active fault and two warmed connections, click **Drop connections**. This resets the existing proxy connections once and allows replacements. Do not apply a hang or click End during this experiment.

Run the following pair of commands three times:

```bash
curl -sS -w '\nHTTP %{http_code}; elapsed %{time_total}s\n' \
  http://localhost:8097/quotes/count
curl -sS http://localhost:8097/lab/pool
```

With the default lab settings, the first two requests returned 503 while Agroal discarded the two dead connections. The third returned 200 using a replacement. Watch `available` fall and `destroyed` rise before `created` increases.

For a comparison, stop dev mode and restart with the supplied validation profile:

```bash
quarkus dev -Dquarkus.profile=dev,validated --no-debug
```

Warm the pool, click **Drop connections**, and repeat the three requests. All three returned 200 in the verified run: foreground validation discarded both dead connections before lending a replacement to the first query. That request took about **180 ms** locally.

The profile sets `quarkus.datasource.jdbc.foreground-validation-interval=PT0.001S`. This deliberately short interval makes the comparison easy to observe; it is not a production tuning recommendation. Restart with the ordinary dev command before the next experiment. Restarting also recreates the table.

## Experiment 4: a write loses its connection

Start with no active fault. Save a healthy quote, note the count, and warm both connections:

```bash
curl -sS -X POST 'http://localhost:8097/quotes?destination=Berlin'
curl -sS http://localhost:8097/quotes/count
curl -sS -X POST http://localhost:8097/lab/pool/warm
```

Select `dies-mid-request` and click **Apply**, then attempt a second save:

```bash
curl -sS -w '\nHTTP %{http_code}\n' -X POST \
  'http://localhost:8097/quotes?destination=Hamburg'
```

The verified run returned 503 with `BatchUpdateException` and SQL state `08006`. Click **End**, then inspect the database before sending another write:

```bash
curl -sS http://localhost:8097/quotes/count
curl -sS http://localhost:8097/quotes
```

The count was unchanged and only the healthy quote was present. A new save then returned 201 and increased the count by one:

```bash
curl -sS -X POST 'http://localhost:8097/quotes?destination=Hamburg'
curl -sS http://localhost:8097/quotes/count
```

The proxy treats PostgreSQL traffic as bytes. This experiment does not target a specific SQL statement or the COMMIT boundary, and a failed client response is not general proof that a transaction rolled back. The app performs no automatic write retries and has no idempotency mechanism.

Ending a scoped fault also flushes the Agroal pool. Recovery after **End** therefore includes extension cleanup. Experiment 3 observes connection replacement without that cleanup.

## Inspect Dev UI events

Use **Events** for connection activity and **Export report** for a Markdown report. JDBC has no HTTP request history: **Requests** is disabled, and zero HTTP requests is normal. Traffic can pass over existing pooled connections without increasing the new-connection count; inspect byte counts and connection events too. **End all faults** clears active faults across targets.

## Run the two extension checks and package

Stop dev mode first:

```bash
quarkus test --once
quarkus build --no-tests
```

The two tests cover:

- Holding established connections through the `DataSource` target, waiting for two proxy-held connections, and observing pool acquisition failure.
- Resolving `EntityManager` to the database target, resetting a write on its next network write, and recovering after scoped fault cleanup.

There is no general CRUD, DTO, or companion correctness suite. The four manual experiments are the main learning path. Both tests and the JVM build passed with the versions above.

The build creates `target/quarkus-app/quarkus-run.jar`. Running that package requires an existing PostgreSQL schema and the `QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME`, and `QUARKUS_DATASOURCE_PASSWORD` environment variables:

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

Keep the complete `target/quarkus-app` directory together. A packaged JVM smoke check saved and read a quote against the lab database; `/lab/pool` returned 404. Fault injection is a dev/test facility. The core extension is a compile dependency for Dev UI, so some Network Faults runtime libraries are present in the package. No native build was verified.

## Source map

- `SavedQuote` maps the `saved_quote` table and assigns UUIDs in Java.
- `QuoteStore` contains the Hibernate reads and transactional save, including an explicit flush.
- `QuoteResource` exposes saved quotes and the independent price preview.
- `PoolLabResource` warms the pool and exposes its counters in dev/test.
- `DatabaseFailures` logs database failures and returns the lab's diagnostic 503 response.
- `src/main/resources/application.properties` contains all pool, driver, and profile settings.

See the upstream [database guide](https://github.com/quarkiverse/quarkus-network-faults/blob/0.2.0/docs/modules/ROOT/pages/guide-database.adoc), [pool exhaustion guide](https://github.com/quarkiverse/quarkus-network-faults/blob/0.2.0/docs/modules/ROOT/pages/guide-pool-exhaustion.adoc), [Quarkus datasource guide](https://quarkus.io/guides/datasource), and [pgJDBC connection properties](https://jdbc.postgresql.org/documentation/use/) for the underlying configuration and APIs.
