# shipment-schema-first

Schema-first GraphQL demo for the Main Thread article "Schema-First GraphQL on Quarkus: When SmallRye Is Too High-Level".

This app uses:

- `quarkus-vertx-graphql` for Vert.x Web GraphQL integration, GraphiQL, and GraphQL WebSocket protocol support
- `quarkus-arc` for CDI wiring
- GraphQL Java with SDL-first schema wiring
- the Vert.x router directly for `/graphql`

## What it shows

- SDL-first GraphQL with `src/main/resources/graphql/shipment.graphqls`
- GraphQL Java wiring from Quarkus CDI
- Header-scoped queries using `X-Warehouse-Code`
- HTTP query and mutation handling on `/graphql`
- Live subscription updates over GraphQL WebSocket
- Quarkus tests that hit the real GraphQL endpoint

## Important files

- `src/main/resources/graphql/shipment.graphqls`
- `src/main/java/com/themainthread/shipment/ShipmentGraphQLProducer.java`
- `src/main/java/com/themainthread/shipment/ShipmentGraphQLRoutes.java`
- `src/main/java/com/themainthread/shipment/ShipmentStore.java`
- `src/test/java/com/themainthread/shipment/ShipmentGraphQLTest.java`
- `article.md`

## Run in dev mode

```bash
./mvnw quarkus:dev
```

Then open:

- GraphQL endpoint: `http://localhost:8080/graphql`
- GraphiQL UI: `http://localhost:8080/q/graphql-ui/`

## Example query

```graphql
query {
  viewerWarehouse
  shipments {
    id
    destinationCity
    warehouseCode
    status
  }
}
```

## Example mutation

```graphql
mutation {
  updateShipmentStatus(id: "BER-1001", status: DELIVERED) {
    id
    status
  }
}
```

## Run tests

```bash
./mvnw test
```

## Related guides

- Quarkus reactive routes: <https://quarkus.io/guides/reactive-routes>
- Quarkus SmallRye GraphQL: <https://quarkus.io/guides/smallrye-graphql>
- Quarkus Vert.x GraphQL extension: <https://quarkus.io/extensions/io.quarkus/quarkus-vertx-graphql/>
- Vert.x Web GraphQL reference: <https://vertx.io/docs/vertx-web-graphql/java/>
