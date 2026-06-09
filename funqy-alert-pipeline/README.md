# funqy-alert-pipeline

Small Quarkus sample for a hands-on tutorial about `quarkus-funqy-knative-events`, local Funqy HTTP calls, and CloudEvent-shaped function invocations on `localhost`.

The project exposes four Funqy functions:

- `GET /previewAlert` for a full end-to-end route preview using query parameter mapping
- `POST /ingestAlert` for a plain JSON function call that normalizes and classifies an alert
- `POST /` with CloudEvent binary headers to trigger `scoreAlert`
- `POST /` with a structured CloudEvent body to trigger `routeAlert`

The important boundary is deliberate: the broker-level hop belongs to Knative. This sample proves the function contract locally first, so you can keep the Quarkus dev loop before you deal with brokers and triggers.

## Prerequisites

- JDK 21 or newer
- Maven 3.9+ if you do not want to use the wrapper

## Run in dev mode

```bash
./mvnw quarkus:dev
```

## Preview the full route with a GET request

This is the fast first proof because it uses Funqy query parameter mapping instead of a JSON file:

```bash
curl -s 'http://127.0.0.1:8080/previewAlert?service=payments&environment=prod&region=us-east-1&summary=Checkout%20timeouts%20spreading&errorRatePercent=7.2&impactedCustomers=1800&acknowledged=false'
```

You should get a routing decision back with fields like:

- `severity: critical`
- `riskScore: 100`
- `destinationTeam: payments-oncall`
- `triggeringEventSource: localhost`

## Ingest a JSON alert

This is the plain Funqy HTTP path. The function name becomes the URL path.

```bash
curl -s -X POST http://127.0.0.1:8080/ingestAlert \
  -H 'Content-Type: application/json' \
  -d '{
    "service":"Search",
    "environment":"staging",
    "region":"eu-west-1",
    "summary":"Search latency climbing",
    "errorRatePercent":2.4,
    "impactedCustomers":420,
    "acknowledged":false
  }'
```

The response should include a normalized service name, a derived severity, and a dedupe key.

## Trigger `scoreAlert` with a binary CloudEvent

`scoreAlert` is mapped through `application.properties`, so the incoming CloudEvent type decides which function runs.

```bash
curl -si -X POST http://127.0.0.1:8080/ \
  -H 'Content-Type: application/json' \
  -H 'Ce-Id: binary-score-1' \
  -H 'Ce-Specversion: 1.0' \
  -H 'Ce-Type: ingestAlert.output' \
  -H 'Ce-Source: urn:test:binary' \
  -d '{
    "service":"catalog",
    "environment":"prod",
    "region":"us-west-2",
    "summary":"catalog misses rising",
    "errorRatePercent":3.2,
    "impactedCustomers":640,
    "acknowledged":false,
    "severity":"high",
    "dedupeKey":"catalog:us-west-2:catalog-misses-rising",
    "checkpoints":["validated","ingested"]
  }'
```

You should see CloudEvent response headers including:

- `ce-source: scoreAlert`
- `ce-type: com.mainthread.alert.scored`

The JSON body should show `riskScore: 86` and include the `scored` checkpoint.

## Trigger `routeAlert` with a structured CloudEvent

`routeAlert` uses `@CloudEventMapping` plus `@Context CloudEvent` to read event metadata.

```bash
curl -si -X POST http://127.0.0.1:8080/ \
  -H 'Content-Type: application/cloudevents+json' \
  -d '{
    "specversion":"1.0",
    "id":"structured-route-1",
    "source":"urn:test:structured",
    "type":"com.mainthread.alert.scored",
    "datacontenttype":"application/json",
    "data":{
      "service":"checkout",
      "environment":"prod",
      "region":"us-east-1",
      "summary":"checkout retries spiraling",
      "errorRatePercent":6.8,
      "impactedCustomers":900,
      "acknowledged":false,
      "severity":"critical",
      "riskScore":100,
      "dedupeKey":"checkout:us-east-1:checkout-retries-spiraling",
      "checkpoints":["validated","ingested","scored"]
    }
  }'
```

The response is another structured CloudEvent with:

- `type: com.mainthread.alert.routed`
- `source: routeAlert`
- `data.destinationTeam: checkout-oncall`
- `data.triggeringEventId: structured-route-1`

## Run the tests

```bash
./mvnw test
```

The test suite covers:

- GET query parameter mapping through `previewAlert`
- JSON POST invocation through `ingestAlert`
- binary CloudEvent routing for `scoreAlert`
- structured CloudEvent routing plus metadata capture for `routeAlert`

## Related guides

- [Funqy](https://quarkus.io/guides/funqy)
- [Funqy HTTP Binding (Standalone)](https://quarkus.io/guides/funqy-http)
- [Funqy Knative Events Binding](https://quarkus.io/guides/funqy-knative-events)

The full article draft for this sample lives in [article.md](article.md).
