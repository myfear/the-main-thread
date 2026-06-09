# checkout-service

`checkout-service` is the consumer side of the `dockyard-discovery` demo. It calls `catalog-service` through a REST client whose base URI uses the `stork://` scheme, so Stork resolves instances from Consul instead of a fixed host list.

The module targets Quarkus `3.36.1` on Java `21`.

## Endpoint

`GET /quote/{sku}`

Example response:

```json
{
  "sku": "sku-1",
  "price": 19.99,
  "instanceId": "catalog-2",
  "color": "green",
  "servedAt": "2026-06-09T08:00:00Z"
}
```

## Important Configuration

- `quarkus.stork.catalog-service.service-discovery.type=consul`
- `quarkus.stork.catalog-service.service-discovery.consul-host=localhost`
- `quarkus.stork.catalog-service.service-discovery.consul-port=8500`
- `quarkus.stork.catalog-service.service-discovery.refresh-period=2S`
- `quarkus.stork.catalog-service.load-balancer.type=round-robin`

These settings tell Stork where Consul lives, how often to refresh the instance list during the demo, and how to choose among the discovered instances.

## Running the Demo

Run the app in dev mode:

```bash
./mvnw quarkus:dev
```

The service expects:

- Consul on `localhost:8500`
- one `catalog-service` instance on `8081`
- an optional second `catalog-service` instance on `8082`

## Running Tests

Run the JVM tests:

```bash
./mvnw test
```

The test suite starts:

- two lightweight HTTP stubs that behave like `catalog-service`
- a fake Consul HTTP endpoint that returns those instances

That lets the tests verify Stork-backed round robin and failover without a real Consul process.

## Related Guides

- [Quarkus REST Client guide](https://quarkus.io/guides/rest-client)
- [SmallRye Stork guide](https://quarkus.io/guides/stork)
