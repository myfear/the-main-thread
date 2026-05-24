# LoanFlow Zero-Trust Demo

Three Quarkus services demonstrating mTLS, service tokens, and edge policy checks for The Main Thread.

## Prerequisites

- JDK 25
- Podman (Quarkus Dev Services starts Keycloak in a Podman container)
- OpenSSL, `keytool`, `curl`, `jq`
- Quarkus CLI (uses the current platform BOM when you generate or build)

## Quick start

```bash
./scripts/generate-certs.sh

# Start loan-service first — Dev Services boots Keycloak on port 8180.
# Then start the other two in separate terminals:
cd loan-service && ./mvnw quarkus:dev
cd credit-service && ./mvnw quarkus:dev
cd document-service && ./mvnw quarkus:dev

./scripts/smoke-test.sh
```

Dev Services shares one Keycloak container across all three apps in dev mode. Pinning `quarkus.keycloak.devservices.port=8180` keeps the `curl` examples stable.

## Layout

- `article.md` — hands-on tutorial prose
- `implementation-plan.md` — build spec for agents and maintainers
- `loan-service/` — public edge API (HTTPS + user bearer tokens + branch policy)
- `credit-service/` — internal API (mTLS + `credit_check_run` permission)
- `document-service/` — internal API (mTLS + `document_write` permission)
- `infrastructure/` — canonical Keycloak realm export and generated certs
- `scripts/` — certificate generation and smoke tests

## Ports

- **Keycloak (Dev Services)** — http://localhost:8180
- **loan-service** — https://localhost:8443
- **credit-service** — https://localhost:8444
- **document-service** — https://localhost:8445
