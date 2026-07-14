# Honest Progress

This is the companion application for the follow-up article **100% Uploaded Is Not 100% Done**. It demonstrates two different progress signals in one Quarkus application:

- `XMLHttpRequest.upload` measures bytes moving from the browser to Quarkus.
- A durable PostgreSQL job records validation, batched import, final publication, failure, and cancellation.

The browser receives job snapshots over Server-Sent Events (SSE). Reloading the page does not lose an active job because the browser keeps only its job ID; PostgreSQL remains authoritative.

## Architecture

The application accepts an invoice CSV as multipart form data and moves it into a staging directory. The REST endpoint creates a queued job and responds with `202 Accepted` plus a `Location` header. A basic Quarkus Scheduler method claims one queued job at a time, validates the entire file, writes invoice rows in small unpublished batches, and publishes them in a final transaction.

The SSE endpoint polls the durable job record and emits a new snapshot only when its optimistic-lock version changes. SSE clients can disconnect and reconnect without affecting processing.

The demo is intentionally a single-application-instance design. See the article for the changes required for multiple replicas, resumable uploads, object storage, or a WebSockets Next control channel.

## Requirements

- Java 25
- Podman with its Docker-compatible socket enabled

Quarkus Dev Services starts PostgreSQL automatically. No local database configuration is required in dev or test mode.

## Run it

```bash
./mvnw quarkus:dev
```

Open <http://localhost:8080>. Choose a CSV or click **Run demo file**. The first bar shows the network transfer. The second bar follows the server job.

The input contract is:

```csv
invoice_number,amount,currency
INV-1001,125.40,EUR
INV-1002,48.99,USD
```

Invoice numbers must contain only letters, digits, and hyphens. Amounts must be positive, currencies must be three letters, and invoice numbers must be unique within a file.

## API

- `POST /api/imports` accepts a multipart `file` field and returns `202 Accepted`.
- `GET /api/imports/{id}` returns the authoritative job snapshot.
- `GET /api/imports/{id}/events` streams named `progress` events as SSE.
- `DELETE /api/imports/{id}` requests cooperative cancellation.

Try the API directly:

```bash
curl -i -F file=@src/main/resources/META-INF/resources/sample-invoices.csv \
  http://localhost:8080/api/imports
```

## Test it

```bash
./mvnw test
```

The tests cover successful import and publication, a terminal SSE snapshot, queued cancellation, duplicate detection with no partially published rows, rejected file types, and focused CSV parsing rules.

## Configuration

The defaults live in `src/main/resources/application.properties`:

- HTTP uploads are limited to a 25 MB multipart file and a 26 MB request body.
- Jobs are checked every second.
- SSE snapshots are checked every 500 ms.
- Five invoice rows are committed per processing batch.
- Staged uploads live below `target/` in dev and test mode.
- The visible processing delay is disabled in the production profile.

Production uses `validate` for schema management and expects a writable staging directory at `/var/lib/honest-progress/staged-uploads`. Configure the PostgreSQL datasource and manage the schema before deployment.

## Extensions

- [Quarkus REST](https://quarkus.io/guides/rest)
- [Hibernate ORM with Panache](https://quarkus.io/guides/hibernate-orm-panache)
- [PostgreSQL JDBC](https://quarkus.io/guides/datasource)
- [Quarkus Scheduler](https://quarkus.io/guides/scheduler)
- [Hibernate Validator](https://quarkus.io/guides/validation)
