# Cascaded token delegation with Quarkus and Keycloak

This demo accompanies the hands-on tutorial in [`article.md`](article.md). It runs three Quarkus services and proves that each hop exchanges the incoming access token for a new token with a narrower audience:

```text
tutorial-client -- aud=service-a --> order-service
order-service   -- aud=service-b --> inventory-service
inventory-service -- aud=service-c --> audit-service
```

The subject remains the same across the chain. The audience, authorized party, and token ID change at every hop.

## Requirements

- Java 25
- Podman with `podman compose`
- `curl`
- `jq`
- OpenSSL

## Run every check

The verification script starts Keycloak, builds all three services, starts their packaged applications, and tests the success path, audience rejection, requester-audience enforcement, scope downscoping, raw RFC 8693 exchanges, PKCE configuration, and a Keycloak outage.

```shell
./scripts/verify.sh
```

Service logs and failure response bodies are written to `.runtime/`. The script never prints access tokens or client secrets.

## Run the demo manually

Start Keycloak:

```shell
podman compose up -d keycloak
```

Start the services in separate terminals:

```shell
cd audit-service
./mvnw quarkus:dev
```

```shell
cd inventory-service
SERVICE_B_SECRET=service-b-secret ./mvnw quarkus:dev
```

```shell
cd order-service
SERVICE_A_SECRET=service-a-secret ./mvnw quarkus:dev
```

Get the local verification token and submit an order:

```shell
TOKEN=$(curl -fsS -X POST \
  http://localhost:8180/realms/delegation/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d grant_type=password \
  -d client_id=tutorial-client \
  -d username=alice \
  -d password=alice | jq -r .access_token)

curl -fsS -X POST http://localhost:8081/orders/order-42/submit \
  -H "Authorization: Bearer $TOKEN" \
  -H 'X-Correlation-ID: manual-run' | jq .
```

The password grant exists only to make the local shell verification deterministic. The same public client has authorization code flow with S256 PKCE enabled; use that flow for an interactive application.

The client secrets and passwords in the imported realm are local tutorial credentials. Replace them with secret-manager-backed credentials and a production Keycloak deployment before adapting the pattern.

## Components

- `order-service` listens on port 8081 and accepts only tokens with `aud=service-a`.
- `inventory-service` listens on port 8082 and accepts only tokens with `aud=service-b`.
- `audit-service` listens on port 8083 and accepts only tokens with `aud=service-c`.
- Keycloak listens on port 8180; its management health endpoint is mapped to port 9000.
- `keycloak/delegation-realm.json` contains the clients, audience mappers, user, and downscope client policy.

Stop Keycloak when you are done:

```shell
podman compose down
```
