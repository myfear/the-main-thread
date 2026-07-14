# SwiftShip Tracking

SwiftShip Tracking is the sample application for the Quarkus AOT JAR tutorial. It uses Quarkus 3.37.2, Java 25, PostgreSQL 18.4, Flyway, Hibernate ORM with Panache, Quarkus REST, SmallRye Health, and the Podman container-image extension.

The application exposes:

- `GET /api/shipments` — list all seeded shipments
- `GET /api/shipments/{trackingNumber}` — get one shipment
- `GET /api/shipments/summary` — count shipments by status
- `GET /q/health/ready` — readiness, including the datasource check

## Prerequisites

- JDK 25
- Podman 5 or later

On macOS or Windows, start the Podman machine first:

```bash
podman machine start
```

## Development and Tests

Quarkus Dev Services starts PostgreSQL automatically:

```bash
./mvnw quarkus:dev
```

Run the JVM test suite:

```bash
./mvnw test
```

Run the packaged integration tests:

```bash
./mvnw verify -DskipITs=false
```

## Fast JAR Baseline

Keep one PostgreSQL container running during startup measurements:

```bash
./scripts/postgres.sh start
./mvnw clean package -Dquarkus.package.jar.aot.enabled=false
java scripts/StartupBenchmark.java fast 20
```

The benchmark launches a fresh JVM for each run and measures time until `/q/health/ready` returns HTTP 200. Its first run primes the database and filesystem cache and is excluded from the result.

## Leyden AOT JAR

Train the cache through the packaged integration tests:

```bash
./mvnw clean verify \
  -Dquarkus.package.jar.aot.enabled=true \
  -DskipITs=false
```

Verify that the cache exists, then benchmark it in fail-fast AOT mode:

```bash
ls -lh target/quarkus-app/app.aot
java scripts/StartupBenchmark.java aot 20
```

`StartupBenchmark.java` adds `-XX:AOTMode=on`, `-XX:AOTCache=app.aot`, and `-Xlog:aot` in AOT mode. The JVM exits if the cache cannot be loaded, so a fallback JVM run cannot slip into the AOT numbers.

## AOT-Enhanced Container Image

Build the base image with Podman, train inside the packaged integration-test flow, and create the final image:

```bash
./mvnw clean verify \
  -Dquarkus.package.jar.aot.enabled=true \
  -Dquarkus.container-image.build=true \
  -DskipITs=false
```

The final image tag is `localhost/themainthread/swiftship-tracking:1.0.0-SNAPSHOT-aot`. Run it against the local PostgreSQL container:

```bash
podman run --rm --name swiftship-aot \
  --publish 8080:8080 \
  --env QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://host.containers.internal:5432/swiftship \
  --env QUARKUS_DATASOURCE_USERNAME=swiftship \
  --env QUARKUS_DATASOURCE_PASSWORD=swiftship \
  localhost/themainthread/swiftship-tracking:1.0.0-SNAPSHOT-aot
```

## Optional Native Image

Build the native executable and its Podman image with a separate tag:

```bash
./mvnw clean package -Dnative \
  -Dquarkus.native.remote-container-build=true \
  -Dquarkus.native.container-runtime=podman \
  -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.tag=native
```

That form is for macOS or Windows with a Podman machine. On Linux with a local Podman socket, replace `quarkus.native.remote-container-build=true` with `quarkus.native.container-build=true`.

## Gradle Note

The equivalent Gradle AOT build needs `--rerun-tasks` when the workspace has already produced a normal fast JAR:

```bash
./gradlew build quarkusIntTest \
  --rerun-tasks \
  -Dquarkus.package.jar.aot.enabled=true
```

During validation, Gradle otherwise considered `quarkusIntTest` up to date, reported a successful build, and produced no `app.aot`. The Maven workflow above uses `clean` to start each measured packaging mode from an empty output directory.

## Guides

- [Quarkus AOT caching](https://quarkus.io/guides/aot)
- [Hibernate ORM with Panache](https://quarkus.io/guides/hibernate-orm-panache)
- [Flyway](https://quarkus.io/guides/flyway)
- [SmallRye Health](https://quarkus.io/guides/smallrye-health)
- [Podman with Quarkus](https://quarkus.io/guides/podman)
