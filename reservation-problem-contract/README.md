# Reservation Problem Contract

Companion code for the Main Thread article [Quarkus HTTP Problem: One Error Contract, Fewer Exception Mappers](article.md).

The application is a warehouse reservation API. `POST /reservations` uses `quarkus-http-problem` so framework failures, Bean Validation, domain conflicts, and unexpected exceptions share one `application/problem+json` contract.

## Stack

- Java 25
- Quarkus 3.39.1
- Platform BOM `io.quarkus.platform:quarkus-bom` (manages `quarkus-http-problem` 3.38.2)
- `quarkus-rest-jackson`
- `quarkus-hibernate-validator`
- `quarkus-smallrye-openapi`

## Run it

```bash
./mvnw quarkus:dev
```

Successful reservation:

```bash
curl -i -X POST http://localhost:8080/reservations \
  -H 'Content-Type: application/json' \
  --data '{"sku":"mouse-1","quantity":1}'
```

Insufficient stock (`409`):

```bash
curl -i -X POST http://localhost:8080/reservations \
  -H 'Content-Type: application/json' \
  --data '{"sku":"keyboard-1","quantity":5}'
```

Unexpected failure (`500` with `supportId`):

```bash
curl -i -X POST http://localhost:8080/reservations \
  -H 'Content-Type: application/json' \
  --data '{"sku":"ledger-offline","quantity":1}'
```

Dev UI for the mapper list, post-processor order, and a generated problem:

- [Http Problem card](http://localhost:8080/q/dev-ui/)
- [Post Processors](http://localhost:8080/q/dev-ui/quarkus-http-problem/post-processors)
- [Test](http://localhost:8080/q/dev-ui/quarkus-http-problem/test)

## Tests

```bash
./mvnw test
```

The tests cover the HTTP contract, OpenAPI `application/problem+json` for the declared `409`, the support-ID post-processor order, and the Jakarta REST entity bypass.

## Guides

- [Quarkus HTTP Problem](https://github.com/quarkiverse/quarkus-http-problem)
- [RFC 9457](https://datatracker.ietf.org/doc/html/rfc9457)
- [Quarkus REST JSON](https://quarkus.io/guides/rest-json)
- [Hibernate Validator](https://quarkus.io/guides/validation)
- [SmallRye OpenAPI](https://quarkus.io/guides/openapi-swaggerui)
