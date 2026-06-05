# carrier-bridge

`carrier-bridge` is a small Quarkus service that shows how to make an outbound REST client behave like part of a real system instead of a localhost demo. The app exposes `GET /tracking/{trackingId}`, calls a downstream carrier API through a declarative REST client, retries one transient `503`, maps downstream failures into useful API errors, and keeps sensitive outbound headers out of the logs.

The module targets Quarkus `3.36.1` on Java `21`. Tests use the Quarkiverse WireMock extension (`quarkus-wiremock` **1.6.3**).

## Features

- Explicit outbound `connect-timeout` and `read-timeout`
- One bounded retry for transient carrier outages
- REST client request-response logging with `masked-headers`
- Client-side error mapping for `404` and `503`
- Server-side error mapping to clean JSON API responses
- WireMock-backed tests for slow, flaky, and permanently broken downstream behavior

## Endpoint

`GET /tracking/{trackingId}`

Successful response:

```json
{
  "trackingId": "TRACK-123",
  "carrier": "Parcel Rocket",
  "status": "IN_TRANSIT",
  "lastUpdated": "2026-06-05T12:30:00Z"
}
```

Failure response:

```json
{
  "code": "carrier_unavailable",
  "message": "Carrier API is temporarily unavailable.",
  "downstreamStatus": 503
}
```

## Important Configuration

The demo keeps the failure budget intentionally small:

- `quarkus.rest-client."carrier-api".connect-timeout=100`
- `quarkus.rest-client."carrier-api".read-timeout=200`
- `quarkus.fault-tolerance."org.acme.carrier.bridge.TrackingService/fetchTracking".retry.max-retries=1`
- `quarkus.rest-client.logging.masked-headers=Authorization,Cookie,X-Carrier-Key`

The REST client logging category is set to `DEBUG` so the request-response logger actually emits entries during local runs and tests.

## Running the Demo

Run the app in dev mode:

```bash
./mvnw quarkus:dev
```

The OpenAPI UI is available in dev mode at <http://localhost:8080/q/swagger-ui/>.

## Running Tests

Run the JVM test suite:

```bash
./mvnw test
```

The test suite starts a WireMock Dev Service through the [Quarkus WireMock extension](https://docs.quarkiverse.io/quarkus-wiremock/dev/index.html) and verifies:

- success on the happy path
- a timeout against a slow downstream
- one retry for a fail-once `503`
- a clean `503` after a permanent outage
- a mapped `404` for unknown tracking IDs
- redaction of fake secrets in outbound REST client logs

## Related Guides

- [REST Client guide](https://quarkus.io/guides/rest-client)
- [Quarkus REST guide](https://quarkus.io/guides/rest)
- [SmallRye Fault Tolerance guide](https://quarkus.io/guides/smallrye-fault-tolerance)
- [OpenAPI and Swagger UI guide](https://quarkus.io/guides/openapi-swaggerui)
