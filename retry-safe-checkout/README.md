# Retry-Safe Checkout

This Quarkus demo shows how `Idempotency-Key` protects a checkout endpoint from duplicate HTTP retries. PostgreSQL stores orders, and a deliberately slow fulfillment gateway makes concurrent requests visible.

The application uses Quarkus 3.37.2, Java 21, and `quarkus-http-idempotency` 0.1.0.

## Run the Demo

Start the Podman machine on macOS or Windows:

```bash
podman machine start
```

Then start Quarkus:

```bash
./mvnw quarkus:dev
```

Dev Services starts PostgreSQL and Flyway creates the order table.

The API exposes:

- `POST /orders` creates an idempotent order and requires `Idempotency-Key`
- `GET /orders/{id}` returns an order
- `GET /orders/stats` returns order, fulfillment, and in-flight counts

## Run the Tests

```bash
./mvnw test
```

The tests cover completed replay, concurrent `409 Conflict`, mismatched-payload `422`, and a missing key.

## Scope

The demo uses the in-memory idempotency store, so it is correct for one running application instance. The [full tutorial](article.md) explains the multi-replica boundary and the typed-JSON replay problem verified against the Redis store in the published 0.1.0 release.
