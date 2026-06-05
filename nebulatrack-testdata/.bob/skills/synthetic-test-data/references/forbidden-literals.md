# Forbidden test literals

Do not hardcode these placeholder values in new tests:

- `SAT-001`, `SAT-NE-42`, or other IDs that violate `SAT-[A-Z]{2}-[0-9]{4}`
- `EVT-001` as the only event ID in a suite
- `user123@test.com` or other generic user placeholders
- `(0.0, 0.0)` coordinates in every test
- `"NOMINAL"` as the only telemetry state exercised

Use `SatelliteEventModels` or Instancio generators instead.

When you need a fixed ID for a negative test, make the invalid shape explicit in the test name and `@ValueSource` argument list.
