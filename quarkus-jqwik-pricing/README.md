# Quarkus jqwik pricing

This Quarkus application demonstrates how 50 example-based tests can miss a monetary rounding bug that one property-based test finds and shrinks.

It requires Java 25 and targets Java 25 bytecode through Maven's `release` setting.

## Run the application

```bash
./mvnw quarkus:dev
```

Calculate a line-item price:

```bash
curl --request POST http://localhost:8080/prices/calculate \
  --header 'Content-Type: application/json' \
  --data '{"unitPrice":0.07,"quantity":3,"discountPercent":10}'
```

## Run the tests

```bash
./mvnw test
```

The test suite combines ordinary JUnit examples, jqwik properties, and a Quarkus HTTP test. See the [Quarkus REST guide](https://quarkus.io/guides/rest) and the [jqwik user guide](https://jqwik.net/docs/current/user-guide.html) for the underlying APIs.
