---
name: synthetic-test-data
description: Generate Quarkus test data for NebulaTrack satellite events using Instancio models, Datafaker, and domain-specific formats instead of hardcoded literals
---

Use this skill when writing or modifying tests for `SatelliteEventService`, `SatelliteEventResource`, or related NebulaTrack telemetry code in this module.

Read [domain formats](references/domain-formats.md) and [forbidden literals](references/forbidden-literals.md) before generating fixtures.

Working rules:

1. Reuse factories in `src/test/java/dev/quarkex/nebulatrack/testdata/support/SatelliteEventModels.java` before inventing new object setup.
2. Use **Instancio** (`org.instancio.Instancio`) for domain objects and reusable `Model<T>` templates.
3. Use **Datafaker** (`net.datafaker.Faker`) for standalone formatted strings when Instancio field generators are not enough.
4. Prefer `@RepeatedTest` or `@ParameterizedTest` with Instancio models over duplicating near-identical constructors.
5. Do **not** add Mockito stubs when real domain objects can exercise validation logic.
6. When a random test fails, reproduce it with `Instancio.of(model).withSeed(<seed>).create()` and leave the seed in a comment or `@Seed` annotation.

Boundary cases to cover:

- negative altitude
- out-of-range latitude and longitude
- blank `eventId`
- invalid `satelliteId` format
- `TelemetryState.ANOMALY` with null or blank `payloadJson`

Finish with:

- tests added or updated
- which factory methods or models you reused
- verification command run (`./mvnw test` or a focused `-Dtest=` class)
