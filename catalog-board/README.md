# CatalogBoard

CatalogBoard is a small Quarkus Data Hibernate demo for The Main Thread. It shows the new `quarkus-data-hibernate` entry point with generated repository methods, build-time checked HQL, managed entity updates, a stateless repository method for explicit stock changes, validation, and REST tests.

## Features

- `Product` entity backed by Quarkus Data Hibernate
- Nested `Repo` and `InventoryRepo` repository interfaces
- `@Find` finder methods returning `Optional<Product>`
- `@HQL` query, update, and delete methods checked by the Hibernate processor
- Managed updates for normal product edits
- Stateless repository update for stock adjustments
- REST API with Bean Validation
- PostgreSQL Dev Services in dev and test mode

## Endpoints

- `GET /products?category=stationery&page=0&size=20` - list active products
- `GET /products/{sku}` - fetch one active product
- `GET /products/search?q=label` - search active products by name
- `GET /products/low-stock` - list active products at or below the reorder point
- `POST /products` - create a product
- `PUT /products/{sku}` - update product metadata
- `PATCH /products/{sku}/stock` - adjust stock through a stateless repository method
- `DELETE /products/{sku}` - mark a product as discontinued
- `DELETE /products/maintenance/discontinued` - physically delete discontinued products

## Run

Use Podman or another Testcontainers-compatible runtime for PostgreSQL Dev Services.

```bash
./mvnw quarkus:dev
```

The app listens on `http://localhost:8080`.

## Try It

```bash
curl -s -X POST http://localhost:8080/products \
  -H 'Content-Type: application/json' \
  -d '{
    "sku": "SKU-100",
    "name": "Field Notebook",
    "category": "stationery",
    "stock": 8,
    "reorderPoint": 3
  }' | jq

curl -s http://localhost:8080/products/SKU-100 | jq

curl -s -X PATCH http://localhost:8080/products/SKU-100/stock \
  -H 'Content-Type: application/json' \
  -d '{"delta": -6}' | jq

curl -s http://localhost:8080/products/low-stock | jq
```

## Test

```bash
./mvnw test
```

The test suite uses `@QuarkusTest`, REST Assured, and PostgreSQL Dev Services.

## Guides

- [Quarkus Data Hibernate](https://quarkus.io/version/main/guides/quarkus-data-hibernate)
- [Hibernate ORM](https://quarkus.io/guides/hibernate-orm)
- [Quarkus REST JSON serialization](https://quarkus.io/guides/rest#json-serialisation)
- [Hibernate Validator](https://quarkus.io/guides/validation)
- [Dev Services for databases](https://quarkus.io/guides/databases-dev-services)
