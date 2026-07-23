# Quarkus OpenFeature migration demo

This demo keeps application code on the `quarkus-flags` API while the `pricing-engine` flag is evaluated by OpenFeature and flagd. The quote endpoint passes the tenant ID as the OpenFeature targeting key.

Northwind receives the `dynamic` pricing engine and a 10% discount. Other tenants use the `stable` engine. If flagd is unavailable, the OpenFeature adapter returns the configured `stable` default.

## Prerequisites

- JDK 21
- Podman
- `curl`

## Run the tests

```bash
./mvnw test
```

The tests disable the external flagd connection. One test exercises the adapter default, and another uses the higher-priority Quarkus in-memory provider as a test override.

## Verify the safe default

Start Quarkus before starting flagd:

```bash
./mvnw quarkus:dev
```

In another terminal, request a quote:

```bash
curl -s 'http://localhost:8088/quotes/contoso?subtotal=100.00'
```

The response uses the registered OpenFeature default:

```json
{"discount":0.00,"flagOrigin":"quarkus.openfeature","pricingEngine":"stable","subtotal":100.00,"tenantId":"contoso","total":100.00}
```

The application remains available while the flagd provider reconnects in the background.

## Start flagd

From the module root, run the pinned flagd image with the local flag file mounted read-only:

```bash
podman run --rm --name flagd \
  -p 8013:8013 \
  -v "$(pwd)/flagd:/etc/flagd:ro" \
  ghcr.io/open-feature/flagd:v0.16.0 \
  start --uri file:/etc/flagd/pricing-flags.json
```

Once the provider reports that it is ready, compare two tenants:

```bash
curl -s 'http://localhost:8088/quotes/northwind?subtotal=100.00'
curl -s 'http://localhost:8088/quotes/contoso?subtotal=100.00'
```

Expected responses:

```json
{"discount":10.00,"flagOrigin":"quarkus.openfeature","pricingEngine":"dynamic","subtotal":100.00,"tenantId":"northwind","total":90.00}
{"discount":0.00,"flagOrigin":"quarkus.openfeature","pricingEngine":"stable","subtotal":100.00,"tenantId":"contoso","total":100.00}
```

Edit `flagd/pricing-flags.json` and replace `northwind` with `contoso`. flagd watches the mounted file, so the next two requests swap pricing engines without a Quarkus restart.

## Configuration

The application registers the flag with the OpenFeature adapter in `application.properties`:

```properties
quarkus.flags.openfeature.pricing-engine.type=string
quarkus.flags.openfeature.pricing-engine.default-value=stable
```

The flagd client connection uses typed application configuration:

```properties
pricing.flagd.enabled=true
pricing.flagd.host=localhost
pricing.flagd.port=8013
```

Set `PRICING_FLAGD_HOST` and `PRICING_FLAGD_PORT` when flagd runs on another host or port. Tests set `pricing.flagd.enabled=false` through the test profile.

## Endpoint

`GET /quotes/{tenantId}?subtotal={positive decimal}`

The response includes `flagOrigin` to make provider ordering visible in the demo. Production APIs normally would not expose that implementation detail.
