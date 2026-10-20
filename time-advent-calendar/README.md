# 24 doors, 24 time bugs

A Java 25 and Quarkus companion with 24 executable time experiments, an independent simulated clock per browser, and real PostgreSQL examples.

Companion to [24 Doors, 24 Time Bugs: A Java Advent Calendar](https://www.the-main-thread.com/p/java-time-advent-calendar-24-bugs), planned for December 24, 2026.

## Run the calendar

Prerequisites:

- JDK 25. Set `JAVA_HOME` to that installation.
- A running Podman engine. On macOS or Windows, start your Podman machine.
- Network access on the first run to download Maven dependencies and the PostgreSQL image.

From this directory:

```shell
./mvnw quarkus:dev
```

Open [the calendar](http://localhost:8080). Quarkus Dev Services starts PostgreSQL, and Flyway creates the experiment tables. No JavaScript build or separately installed Maven is required. On Windows, use `mvnw.cmd`.

Validated with Temurin 25+36, Quarkus 3.39.1, PostgreSQL 18.4 (the `postgres:18` image), and Podman 6.1.3. The image tag follows PostgreSQL 18 patch releases.

## Explore time

The calendar initially opens in **Explore** mode at noon UTC on December 24, 2026. All doors are available. Select a door to execute Java on the server, change its scenario, and inspect the returned values and relevant source excerpt.

**Replay** opens doors at the start of their December date in your selected zone. Choose **Before Berlin midnight**, then door 24: it is locked one second before its opening instant. Set the instant to `2026-12-23T23:00:00` UTC and apply it to open the door. **Advance 1 calendar day** uses Java's zoned calendar arithmetic; it can advance by 23 or 25 elapsed hours across a clock change.

The instant field is always UTC, regardless of your browser's locale. The zone controls calendar dates and unlock boundaries. Browser settings are saved in local storage; they are never installed as a mutable server-wide clock. Tabs in the same browser origin share saved settings on reload, but each active tab sends its own settings with each request.

Most lessons use named fixed fixtures so you can reproduce their result throughout the year. Doors 4 and 20 use your chosen instant; door 24 derives its due date from the calendar year and zone and simulates a later worker wake-up. **Use current time** takes a snapshot of your browser's clock; it does not start a ticking clock.

## The 24 doors

| Door | Experiment |
| --- | --- |
| 1 | Date-only values accidentally turned into instants |
| 2 | Local appointments without a zone |
| 3 | Region rules versus a fixed UTC offset |
| 4 | Deployment defaults changing the calendar date |
| 5 | Converting an event versus rescheduling it |
| 6 | Zoned value equality versus instant equality |
| 7 | A nonexistent local time in a DST gap |
| 8 | Two occurrences of a local time in a DST overlap |
| 9 | A calendar day versus 24 elapsed hours |
| 10 | A missing midnight or a skipped local date |
| 11 | Monthly recurrences drifting after February |
| 12 | February 29 anniversary policies |
| 13 | Week-based year formatting near New Year |
| 14 | SMART parsing adjusting invalid dates |
| 15 | Ambiguous numeric date formats |
| 16 | Actual PostgreSQL precision loss |
| 17 | Half-open business-day query boundaries |
| 18 | Weekdays versus a business holiday calendar |
| 19 | A hypothetical future time-zone rule change |
| 20 | Deterministic boundary testing with `Clock` |
| 21 | Wall-clock adjustments versus elapsed measurement |
| 22 | Actual PostgreSQL storage of instants and regions |
| 23 | Clock skew and causal ordering |
| 24 | Overdue jobs, rollback, retry, and a unique database effect |

Doors **7, 8, 9, 13, 20, and 24** are the article's deeper examples. Doors **16, 22, and 24** execute SQL against PostgreSQL.

## Call the API

Run a DST experiment:

```shell
curl --fail-with-body http://localhost:8080/api/doors/7/run \
  -H 'Content-Type: application/json' \
  -d '{"at":"2026-12-24T12:00:00Z","zone":"Europe/Berlin","mode":"explore","variant":"default"}'
```

The result includes `Valid offsets` as `[]` and Java's shifted `03:30` result. Use `variant: "alternative"` for the other scenario where offered.

Available endpoints:

- `GET /api/calendar?at=2026-12-24T12%3A00%3A00Z&zone=Europe%2FBerlin&mode=replay`
- `POST /api/doors/{1..24}/run` with the JSON settings above
- `POST /api/clock/advance` with `at`, `zone`, and `mode`
- `GET /api/jobs/{uuid}` to count committed effects for a job returned by door 24

Omitting `at` uses one captured server instant. Invalid settings return `400`; unknown doors return `404`; Replay runs before the opening instant return `403`. A missing job has zero effects. The lab accepts instants between 1900 and 2100.

## Verify the application

Keep Podman running and run:

```shell
./mvnw verify -DskipITs=false
```

This runs 52 application tests and 50 HTTP tests against the packaged JVM application. Tests cover all 24 default scenarios, 22 alternatives, unlock boundaries, invalid input, interleaved clocks, DST advancement, transaction rollback, and two competing workers.

`CalendarResourceTest` and `CalendarResourceIT` share the same HTTP contract. `GiftRecoveryTest` checks real database transactions. Dev Services supplies PostgreSQL to both test modes. Source formatting and import sorting run during `process-sources`.

## Understand the persistence boundary

Door 24 creates a fresh job UUID per run. It simulates worker downtime by supplying a later instant, rolls back one actual transaction, then retries. A primary key on `gift_effect.job_id` and `INSERT … ON CONFLICT DO NOTHING` permit one committed local effect per job.

This demonstrates database recovery semantics. It does not kill or restart the application, run a background scheduler, or deliver email. An external side effect needs its own delivery protocol, such as a transactional outbox with an idempotent receiver. Do not delete the idempotency record while retries for its job remain possible.

Door 24 removes this lab's jobs older than one real database day when creating a new job. Its foreign key removes their associated effects. The precision and zone experiments use temporary tables and roll back their work.

## Run the packaged application

After verification, the fast JAR is `target/quarkus-app/quarkus-run.jar`. Keep the entire `quarkus-app` directory together.

For a direct launch, supply an existing PostgreSQL database and a role that can create the demo tables:

```shell
export QUARKUS_DATASOURCE_JDBC_URL='jdbc:postgresql://localhost:5432/time_advent'
export QUARKUS_DATASOURCE_USERNAME='time_advent'
export QUARKUS_DATASOURCE_PASSWORD='your-local-database-password'
java -jar target/quarkus-app/quarkus-run.jar
```

Dev Services supplies the database during development and tests, not this direct production-profile launch. Flyway applies migrations on startup.

The application binds to loopback by default. The simulated clock is an intentional teaching input, not an authorization boundary. The example has no authentication, and the database experiments are intended for local use.

## Troubleshooting

- **No container engine found:** verify `podman info` succeeds and follow the [Quarkus Podman setup instructions](https://quarkus.io/guides/podman). A stopped Podman machine or inaccessible compatible socket prevents Dev Services from starting.
- **Unsupported Java release:** check `java -version` and `JAVA_HOME`; both must select JDK 25.
- **Port 8080 in use:** run `./mvnw quarkus:dev -Dquarkus.http.port=8088`, then use port 8088 in the browser and API commands.
- **A door is locked:** change to Explore or advance the applied clock. The footer and clock status show the calendar's context.
- **Reset the browser:** choose Christmas Eve and Explore, then apply the clock. Reloading keeps saved settings but clears the explored-door markers.

Stop dev mode with Ctrl+C. Dev Services manages the container lifecycle; if you have globally enabled Testcontainers reuse, its container may remain available after shutdown.
