# NebulaTrack satellite event formats

Use these rules when generating test data for `SatelliteEvent` and related types.

## SatelliteEvent fields

- **eventId** — non-blank string with `EVT-` prefix and alphanumeric tail
- **satelliteId** — must match `SAT-[A-Z]{2}-[0-9]{4}` (example: `SAT-NE-1042`)
- **latitude** — `[-90, 90]`
- **longitude** — `[-180, 180]`
- **altitudeKm** — non-negative; LEO tests often use `200..2000`, GEO near `35786`
- **state** — one of `NOMINAL`, `DEGRADED`, `ANOMALY`, `OFFLINE`
- **observedAt** — past `Instant` is fine for validation tests
- **payloadJson** — `TelemetryState.ANOMALY` events **must** include non-blank JSON payload

## Factory entry points

Prefer these methods in `SatelliteEventModels`:

- `validEvent()` — reusable `Model<SatelliteEvent>` baseline
- `anyValidEvent()` — one passing event
- `anomalyMissingPayload()` — anomaly rejection case
- `withNegativeAltitude()` — altitude rejection case
- `randomSatelliteId()` — Datafaker regex ID when you only need the string

## Instancio patterns

- Override one field at a time on `validEvent()` for parameterized boundary tests.
- Use `.supply(field(SatelliteEvent::satelliteId), () -> FAKER.regexify("SAT-[A-Z]{2}-[0-9]{4}"))` for regex-shaped IDs. Do not pass regex syntax to `gen.text().pattern()` unless you intend a literal pattern string.
