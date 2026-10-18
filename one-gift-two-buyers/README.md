# One gift, two buyers

Reproduce a lost update in a gift reservation API, then compare it with Hibernate optimistic locking. Two grandparents compete to reserve a gift from Anna’s wishlist. The browser and HTTP tests send their requests against PostgreSQL. The unversioned path returns two successes; the versioned path returns one success and one conflict.

## Requirements

- JDK 25 (`java -version`).
- Podman with a running machine on macOS or Windows, or a running socket on Linux.
- Network access for the first Maven dependency and PostgreSQL image downloads.

The companion uses Quarkus 3.39.1 and the pinned `docker.io/library/postgres:17.7` image. Maven 3.9.16 is provided by `./mvnw`. The Quarkus CLI is only required to regenerate the scaffold.

On macOS, run `podman machine start` if your machine is stopped. If no machine exists, run `podman machine init` first. See the [Quarkus Podman guide](https://quarkus.io/guides/podman/) for platform setup.

Run all Maven commands from this directory, next to `pom.xml`. No installed PostgreSQL server or database credentials are needed for dev mode and the tests. Leave datasource URL and credential overrides unset so Dev Services can provision PostgreSQL.

## Run the browser demo

```bash
./mvnw quarkus:dev
```

Dev Services detects the container engine and starts PostgreSQL. If Podman isn't detected, or you use rootless Podman, see [Podman troubleshooting](#podman-troubleshooting).

Open [the local demo](http://localhost:8080).

1. Leave **Without version check** selected and click **Send both reservations**. Both grandparents receive HTTP 200. The saved row has only one buyer, so one confirmed reservation has been overwritten.
2. Select **With optimistic locking** and click **Send both reservations**. This uses a separate, initially available row. One buyer receives HTTP 200 and the other receives HTTP 409. The saved owner matches the successful response; the version is 1.
3. Click **New round** to create another pair of gift rows. Either buyer can win.

To use two windows, expand **Try two browser windows / inspect the responses** and open the shared-round link. Reserve as the first grandparent in one window and as the second grandparent in the other within 10 seconds. Use **Refresh saved state** in either window to read the current database owner. Keep both windows on the same strategy and round.

The gate pauses each request after it reads an available gift and before it updates the row. A missing second request returns HTTP 503 after 10 seconds and rolls back. This gate is local to one application instance and exists only to reproduce the interleaving. The ordinary reservation endpoint has no gate.

Press Ctrl+C to stop dev mode and its Dev Services database. Container reuse is disabled. Demo data is disposable and the dev/test schemas are recreated at startup. After an interrupted process, use `podman ps` to identify this app's PostgreSQL container and `podman stop <container-id>` to stop that specific container.

## Run automated verification

```bash
./mvnw verify -DskipITs=false
```

This runs 10 in-process tests and 9 HTTP tests against the packaged JAR, with PostgreSQL supplied by Dev Services. The repeated race test executes five rounds per application mode. It asserts response codes, the conflict code, the committed owner, and the version increment. Additional tests cover sequential conflicts, validation, missing gifts, gate timeout and rollback, isolation between different gift IDs, static assets, and disabling demo routes.

The packaged tests select the explicit `it` profile through Failsafe. That profile enables the demo and recreates only the disposable test schema. A normal production launch defaults to demo endpoints disabled and schema validation.

For the race contract alone:

```bash
./mvnw test -Dtest=ReservationTest
```

No native image validation is claimed. The built JVM artifact is `target/quarkus-app/quarkus-run.jar`; launching it outside the integration-test harness requires an existing PostgreSQL database and schema, plus datasource configuration. This companion's documented interactive path is dev mode.

## Podman troubleshooting

Apply only the setting that matches your setup, in the same terminal that runs Maven. Then rerun the dev-mode or test command.

### A running Podman machine is not detected on macOS

If Dev Services cannot find the container engine even though the Podman machine is running, point the container client at its socket:

```bash
export DOCKER_HOST="unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')"
```

This override is unnecessary when Podman's Docker-compatible socket is already detected, for example through `/var/run/docker.sock`.

### Rootless Podman

If you run Podman in rootless mode, disable Ryuk, Testcontainers' cleanup helper:

```bash
export TESTCONTAINERS_RYUK_DISABLED=true
```

Leave Ryuk enabled for rootful Podman. Dev Services stops this example's database on normal shutdown even when Ryuk is disabled. After an interrupted process, identify and stop this app's container as described in the browser-demo instructions.

The [Testcontainers Podman notes](https://java.testcontainers.org/supported_docker_environment/#podman) explain the socket and Ryuk settings for each platform.

## HTTP API

All request bodies are JSON. IDs are UUIDs. The application binds to loopback by default.

- `POST /api/gifts` with `{"name":"Board game"}` returns 201, a `Location` header, and an available versioned gift.
- `GET /api/gifts/{id}` returns the current gift.
- `POST /api/gifts/{id}/reservations` with `{"buyer":"First grandparent"}` returns 200 after the service transaction commits. A concurrent stale update returns 409 with `code: reservation_conflict`; a gift already reserved when read returns 409 with `code: already_reserved`.
- `POST /api/demo/rounds` creates the two comparison rows and returns 201 with their shared ID.
- `GET /api/demo/rounds/{id}` returns both rows.
- `POST /api/demo/rounds/{id}/unsafe/reservations` and `POST /api/demo/rounds/{id}/optimistic/reservations` accept the same buyer body and coordinate exactly two competing reads.

Valid requests to demo routes return 404 when `gift.demo.enabled=false`, the default outside the dev, test, and explicit integration-test profiles. Malformed requests can be rejected by HTTP or request-body validation before reaching that check. Unknown gifts return 404. Blank or oversized gift names and buyer labels return 400. Gift names allow 120 characters and buyer labels allow 80.

A reservation is represented by the gift's `reservedBy` column. This demo does not create orders, charge buyers, authenticate users, or send confirmations. The buyer labels identify the first and second grandparents in the demonstration; they are not authenticated identities. Use authentication and authorization before adapting the API for real users. All writers of a protected row must respect the version contract; bulk SQL does not automatically inherit entity version checks.

## Implementation

- `Gift.java`: versioned Panache entity; `UnsafeGift.java`: deliberately unversioned comparison.
- `GiftService.java`: transactional reservation with explicit flush and a conflict on stale updates.
- `DemoService.java` and `RaceGate.java`: isolated comparison rows and bounded read coordination.
- `GiftResource.java`, `DemoResource.java`, and `ApiFailureMapper.java`: HTTP boundary and error responses.
- `src/main/resources/META-INF/resources/`: plain HTML, CSS, and JavaScript; no frontend build step or remote assets.
- `ReservationContract.java`: common HTTP assertions inherited by `ReservationTest` and `ReservationIT`.

The initial scaffold was generated with:

```bash
quarkus create app com.themainthread:one-gift-two-buyers \
  --java=25 --platform-bom=3.39.1 \
  --extensions=rest-jackson,hibernate-orm-panache,jdbc-postgresql,hibernate-validator \
  --no-code --no-dockerfiles --package-name=com.themainthread.gifts
```

References: [Panache transactions and flushing](https://quarkus.io/guides/hibernate-orm-panache/#transactions), [database Dev Services](https://quarkus.io/guides/databases-dev-services/), and [Jakarta Persistence optimistic locking](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2.html#_optimistic_locking).
