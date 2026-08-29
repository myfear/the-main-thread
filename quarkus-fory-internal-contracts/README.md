# Quarkus Fory Internal Contracts

This small Quarkus application keeps a catalog snapshot available as JSON and accepts the same snapshot on a Fory-only internal pricing endpoint. It accompanies the [hands-on tutorial](article.md).

The demo uses Quarkus 3.39.1, Java 21, and Quarkus Fory 1.6.0.

## Run the Application

```bash
./mvnw quarkus:dev
```

The public endpoint returns a JSON snapshot:

```bash
curl -s http://localhost:8080/catalog/snapshots/sample
```

`POST /internal/pricing/quote` accepts and returns `application/fory`. The test acts as its Java client because a shell cannot construct the binary payload by hand.

## Run the Tests

```bash
./mvnw test
```

The test sends a real `application/fory` request, deserializes the binary response, verifies the quote, and checks that JSON does not accidentally become a second internal contract.
