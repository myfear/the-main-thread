# Quarkus Checkstyle Boundaries

This Quarkus 3.39.1 demo keeps its domain package free of framework imports. Checkstyle's `ImportControl` check runs in Maven's `validate` phase and rejects dependencies that cross the declared package boundaries.

## Package Boundaries

- `domain` contains plain Java types and may import only the JDK and other domain types
- `application` contains the use case and port; it may depend on the domain and CDI
- `adapter.catalog` implements the catalog port
- `adapter.rest` maps HTTP requests and responses without exposing the domain record as the API contract

## Run the Application

```bash
./mvnw quarkus:dev
```

Request a product:

```bash
curl -i http://localhost:8080/products/sku-1
```

## Verify the Gate

Run the architecture check:

```bash
./mvnw validate
```

Run the application tests:

```bash
./mvnw test
```

The Checkstyle configuration lives in `config/checkstyle`. Its SARIF report is written to `target/checkstyle-result.sarif` for CI systems that can ingest static-analysis results.

## Quarkus Guides

- [Writing REST services with Quarkus REST](https://quarkus.io/guides/rest)
- [CDI reference](https://quarkus.io/guides/cdi-reference)
- [Testing your application](https://quarkus.io/guides/getting-started-testing)
