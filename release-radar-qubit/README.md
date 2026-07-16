# ReleaseRadar with Quarkus Qubit

This demo supports the tutorial in [article.md](article.md). It uses Qubit to generate JPA Criteria query executors from Java lambdas for captured filters, DTO projections, grouping with `HAVING`, and a scalar subquery.

The project is pinned to Java 25, Quarkus 3.32.2, and the preview Qubit 1.0.0 extension.

## Run it

Start Podman, then run Quarkus dev mode from this directory:

```bash
./mvnw quarkus:dev
```

PostgreSQL starts through Quarkus Dev Services and is seeded from `src/main/resources/import.sql`. The Qubit page is available through the Quarkus Dev UI at <http://localhost:8080/q/dev-ui/>.

Query deterministic blockers:

```bash
curl -sG http://localhost:8080/issues/blockers \
  --data-urlencode 'asOf=2026-07-15T12:00:00' \
  --data-urlencode 'olderThanHours=24' \
  --data-urlencode 'severity=CRITICAL' \
  --data-urlencode 'severity=HIGH' \
  --data-urlencode 'limit=20'
```

Query grouped service hotspots:

```bash
curl -s 'http://localhost:8080/issues/hotspots?minimumOpen=2'
```

Query open issues above the open-issue impact average:

```bash
curl -s http://localhost:8080/issues/outliers
```

## Test it

```bash
./mvnw test
```

The four `@QuarkusTest` checks execute all three Qubit call sites against PostgreSQL Dev Services and verify the request limit.

## Production boundary

The production profile validates the schema and expects an external datasource. It does not run `import.sql` or create tables. Add database migrations before deploying this demo as an application.

Qubit 1.0.0 is a preview extension. Keep its version pinned and execute every query call site in tests; the article documents a generation-error path that logs an error without failing the Maven build.
