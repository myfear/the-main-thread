# VaultBoard

VaultBoard is the companion app for [article.md](article.md). It shows how to resolve a tenant once at the HTTP boundary, store it in `TenantContext`, and let Hibernate ORM route to the right datasource without threading a `tenantId` through every service method.

The demo uses:

- Quarkus 3.37.0
- Java 25
- Quarkus Dev Services for PostgreSQL
- `io.quarkiverse.multitenancy:quarkus-multitenancy-http:0.1.0`
- `io.quarkiverse.multitenancy:quarkus-multitenancy-orm:0.1.0`
- `quarkus-smallrye-jwt` for the JWT tenant-resolution slice

## Quick start

Start the app from the module root:

```bash
./mvnw quarkus:dev
```

Dev Services brings up three PostgreSQL containers automatically:

- `__bootstrap` for the ORM bridge startup path
- `acme`
- `globex`

No Compose file or manual database setup is required.

The API is available at `http://localhost:8080`.

## Endpoints

- `GET /api/dashboards`
- `POST /api/dashboards`
- `GET /api/dashboards/tenant`

Check the resolved tenant:

```bash
curl -H "X-Tenant: acme" http://localhost:8080/api/dashboards/tenant
curl -H "X-Tenant: globex" http://localhost:8080/api/dashboards/tenant
```

Create one dashboard per tenant:

```bash
curl -X POST \
  -H "X-Tenant: acme" \
  -H "Content-Type: application/json" \
  -d '{"name":"ARR","ownerEmail":"alice@acme.example","monthlyBudget":120000.00}' \
  http://localhost:8080/api/dashboards

curl -X POST \
  -H "X-Tenant: globex" \
  -H "Content-Type: application/json" \
  -d '{"name":"Cash Flow","ownerEmail":"finops@globex.example","monthlyBudget":98000.00}' \
  http://localhost:8080/api/dashboards
```

List them back:

```bash
curl -H "X-Tenant: acme" http://localhost:8080/api/dashboards
curl -H "X-Tenant: globex" http://localhost:8080/api/dashboards
```

## Why the schema initializer exists

The preview ORM bridge needs the special `__bootstrap` datasource before a request tenant exists. For this demo, Hibernate metadata is anchored there, while the real tenant tables live in the `acme` and `globex` datasources.

That is why the app includes `TenantSchemaInitializer`: it creates the `dashboards` table and sequence in the tenant datasources at startup. For a production system, replace that bean with Flyway or Liquibase migrations per datasource.

## Testing

The verification flow in the article is split into three focused slices:

```bash
./mvnw test -Dtest=DashboardResourceTest
./mvnw test -Dtest=JwtTenantResolutionTest
./mvnw test -Dtest=HostTenantResolutionTest
```

The header-mode test clears both tenant databases before and after each method, so you can run the curl examples first and then run the test class without stale rows breaking the assertions.

## JWT demo notes

The default application mode is header-based resolution because that is the first worked slice in the tutorial.

The repo also includes dev-only PEM files in `src/main/resources/publicKey.pem` and `src/main/resources/privateKey.pem`. The JWT test profile switches the tenant strategy to `jwt`, disables the ORM header filter, and signs tokens with the bundled private key.

## Related reading

- [Quarkus Multitenancy extension](https://github.com/quarkiverse/quarkus-multitenancy)
- [Hibernate ORM with Panache guide](https://quarkus.io/guides/hibernate-orm-panache)
- [Hibernate ORM multitenancy guide](https://quarkus.io/guides/hibernate-orm#multitenancy)
- [Quarkus SmallRye JWT guide](https://quarkus.io/guides/security-jwt)
