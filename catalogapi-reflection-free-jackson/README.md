# catalogapi-reflection-free-jackson

Quarkus demo for the **Reflection-Free Jackson Serializers** tutorial on [The Main Thread](https://www.the-main-thread.com).

## Run

```bash
./mvnw quarkus:dev
```

Enable reflection-free serializers:

```bash
./mvnw quarkus:dev -Dquarkus.profile=reflection-free
```

## Test

```bash
./mvnw test
```

## Benchmark

```bash
./scripts/compare-json-serialization.sh
```

