# shipment-next-actions

A small Quarkus demo for the accompanying HAL tutorial. The app exposes a shipment workflow API where clients can fetch `application/hal+json` and follow valid next-action links instead of guessing transitions from status fields alone.

## Stack

- Quarkus 3.36.1
- JDK 21 target
- `quarkus-rest-jackson`
- `quarkus-hal`
- `quarkus-rest-links`

## Endpoints

- `POST /shipments` creates a shipment in `CREATED`
- `GET /shipments` lists all shipments as JSON or HAL
- `GET /shipments/{id}` returns one shipment as JSON or HAL
- `PUT /shipments/{id}/pay`
- `PUT /shipments/{id}/pack`
- `PUT /shipments/{id}/ship`
- `PUT /shipments/{id}/deliver`
- `PUT /shipments/{id}/cancel`

## Workflow

- `CREATED` -> `pay` or `cancel`
- `PAID` -> `pack` or `cancel`
- `PACKED` -> `ship`
- `SHIPPED` -> `deliver`
- `DELIVERED` and `CANCELLED` are terminal

## Run it

```bash
./mvnw quarkus:dev
```

Create a shipment:

```bash
curl -s -X POST http://localhost:8080/shipments \
  -H 'Content-Type: application/json' \
  -d '{
    "trackingNumber":"TMT-1001",
    "recipient":"Ada Lovelace",
    "destinationCity":"Berlin"
  }'
```

Fetch the HAL representation:

```bash
curl -s -H 'Accept: application/hal+json' \
  http://localhost:8080/shipments/1
```

Run the tests:

```bash
./mvnw test
```

## Guides

- [Quarkus REST guide](https://quarkus.io/guides/rest)
- [Quarkus HAL extension](https://quarkus.io/extensions/io.quarkus/quarkus-hal/)
