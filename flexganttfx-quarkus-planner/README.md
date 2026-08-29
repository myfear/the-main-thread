# FlexGanttFX dock planner

Maven reactor for a dock-door planning board:

- `contract` — shared JSON records for the REST boundary
- `backend` — Quarkus scheduling API with PostgreSQL and Flyway
- `desktop` — JavaFX client with FlexGanttFX 12.4.0

Requires **Java 25**, **OpenJFX 25.0.4**, and **Podman**. The parent POM manages `javafx-base`, `javafx-graphics`, and `javafx-controls` together so FlexGanttFX cannot leave the desktop with a mixed JavaFX runtime.

Install the reactor artifacts once before launching either application module independently:

```bash
./mvnw install -DskipTests
```

## Run the backend

```bash
cd backend
../mvnw quarkus:dev
```

Quarkus starts a PostgreSQL container, applies Flyway migrations, and seeds the schedule.

## Run the desktop client

In a second terminal:

```bash
./mvnw -pl desktop javafx:run
```

Optional API override:

```bash
PLANNER_API_BASE_URL=http://localhost:8080 ./mvnw -pl desktop javafx:run
```

## Verify

```bash
./mvnw test
```

Manual checks:

1. Drag `TRUCK-1042` onto the occupied interval on Door 5. The bar returns and the status line reports an overlap.
2. Update the booking with `curl`, then drag the stale local copy. The client replaces the bar with the server state.

```bash
curl -s -X PUT http://localhost:8080/api/bookings/booking-42/schedule \
  -H 'Content-Type: application/json' \
  -d '{
    "doorId": "door-4",
    "startsAt": "2026-08-20T08:00:00Z",
    "endsAt": "2026-08-20T09:00:00Z",
    "expectedVersion": 0
  }'
```

## API

- `GET /api/board?from=...&to=...` — dock doors and intersecting bookings
- `PUT /api/bookings/{id}/schedule` — propose a new door and interval with optimistic locking

## License note

FlexGanttFX 12.4.0 is dual-licensed under AGPLv3 or a commercial license from DLSC. Review which option fits your distribution model before shipping a product that includes it.
