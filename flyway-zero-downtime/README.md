# Zero-Downtime Flyway Migrations with Quarkus

This Quarkus 3.37.2 demo renames `customer.full_name` to `display_name` through four Flyway stages. Runtime release modes emulate the old, bridge, and modern application versions so mixed-version behavior can be tested against one PostgreSQL database.

## Run the tests

Start the Podman machine and Docker-compatible socket if your platform needs them, then run:

```bash
./mvnw test
```

The tests use Quarkus Dev Services with PostgreSQL 18.4. They prove:

- A direct column rename breaks the legacy SQL query with PostgreSQL SQLSTATE `42703`
- The expanded schema accepts legacy and bridge writes at the same time
- The compatibility trigger keeps both name columns synchronized
- Backfill and constraint stages preserve existing data
- The contract stage removes `full_name` only after the modern release is ready

## Run the mixed-version stage manually

Package the application and start PostgreSQL:

```bash
./mvnw package -DskipTests

podman run --name flyway-zero-downtime-db --replace --detach \
  --publish 5432:5432 \
  --env POSTGRES_DB=customers \
  --env POSTGRES_USER=customers \
  --env POSTGRES_PASSWORD=customers \
  docker.io/library/postgres:18.4-alpine
```

Start a legacy instance on port 8081 and a bridge instance on port 8082. Both commands target schema version 2:

```bash
java \
  -Dquarkus.http.port=8081 \
  -Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/customers \
  -Dquarkus.datasource.username=customers \
  -Dquarkus.datasource.password=customers \
  -Dmigration-demo.release=LEGACY \
  -Dmigration-demo.schema-target=2 \
  -jar target/quarkus-app/quarkus-run.jar
```

```bash
java \
  -Dquarkus.http.port=8082 \
  -Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/customers \
  -Dquarkus.datasource.username=customers \
  -Dquarkus.datasource.password=customers \
  -Dmigration-demo.release=BRIDGE \
  -Dmigration-demo.schema-target=2 \
  -jar target/quarkus-app/quarkus-run.jar
```

Create and rename a customer through the legacy instance, then read it through the bridge instance:

```bash
curl --fail-with-body \
  --header 'Content-Type: application/json' \
  --data '{"email":"grace@example.com","displayName":"Grace Hopper"}' \
  http://localhost:8081/customers

curl --fail-with-body \
  --request PUT \
  --header 'Content-Type: application/json' \
  --data '{"displayName":"Rear Admiral Grace Hopper"}' \
  http://localhost:8081/customers/1/name

curl --fail-with-body http://localhost:8082/customers/1
```

The article explains the remaining backfill and contract stages.
