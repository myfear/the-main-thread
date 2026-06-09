# catalog-service

`catalog-service` is the provider side of the `dockyard-discovery` demo. It exposes one JSON endpoint and registers itself in Consul with an explicit instance ID so we can run the same codebase twice on different ports.

The module targets Quarkus `3.36.1` on Java `21`.

## Endpoint

`GET /catalog/{sku}`

Example response:

```json
{
  "sku": "sku-1",
  "price": 19.99,
  "instanceId": "catalog-1",
  "color": "blue",
  "servedAt": "2026-06-09T08:00:00Z"
}
```

## Important Configuration

- `HTTP_PORT` chooses the provider port, default `8081`
- `INSTANCE_ID` chooses the Consul service ID and response instance ID, default `catalog-1`
- `INSTANCE_COLOR` sets a visible marker in responses, default `blue`
- `CONSUL_HOST` and `CONSUL_PORT` point to the Consul agent, defaults `localhost:8500`
- `CONSUL_ADVERTISED_ADDRESS` is the address consumers should call, default `127.0.0.1`
- `CONSUL_HEALTH_CHECK_HOST` is the host Consul should probe for `/q/health/live`, default `host.containers.internal`
- `CONSUL_REGISTRATION_ENABLED` disables Consul registration when needed, default `true`

If you run Consul in Docker Desktop instead of Podman, `CONSUL_HEALTH_CHECK_HOST=host.docker.internal` is usually the right value.

## Running the Demo

Start one instance:

```bash
INSTANCE_ID=catalog-1 INSTANCE_COLOR=blue HTTP_PORT=8081 ./mvnw quarkus:dev
```

Start a second instance in another terminal:

```bash
INSTANCE_ID=catalog-2 INSTANCE_COLOR=green HTTP_PORT=8082 ./mvnw quarkus:dev
```

The provider assumes a Consul agent is already listening on `localhost:8500`.

## Running Tests

Run the JVM tests:

```bash
./mvnw test
```

The test profile disables Consul registration and checks the JSON response directly.

## Related Guides

- [Quarkus REST guide](https://quarkus.io/guides/rest)
- [SmallRye Health guide](https://quarkus.io/guides/smallrye-health)
