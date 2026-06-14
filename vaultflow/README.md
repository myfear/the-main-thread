# VaultFlow

VaultFlow is a small Quarkus document intake service for a Semgrep walkthrough. It stores metadata in PostgreSQL with Hibernate ORM and Panache, exposes a JSON REST API, and keeps the agent-facing security rules close to the code.

## What the app does

- Creates document records with an external ID, owner email, storage key, and checksum
- Reads a single document by external ID
- Searches documents by owner email
- Seeds PostgreSQL data automatically in dev and test mode with `src/main/resources/import.sql`
- Includes local Semgrep rules, a `pre-commit` hook config, and a sample GitHub Actions workflow

## Endpoints

- `POST /documents`
- `GET /documents/{externalId}`
- `GET /documents/search?ownerEmail=legal@parchment.example`

## Running the app

Run dev mode from the project root:

```bash
./mvnw quarkus:dev
```

Quarkus Dev UI is available at `http://localhost:8080/q/dev` while dev mode is running.

## Testing

Run the Quarkus tests:

```bash
./mvnw test
```

The tests use Dev Services, so PostgreSQL starts automatically in dev and test mode when a container runtime is available.

## Security files

- `.semgrep/vaultflow-rules.yaml`
- `.pre-commit-config.yaml`
- `.github/workflows/semgrep.yml`
- `AGENTS.md`

## Related guides

- [Hibernate ORM with Panache](https://quarkus.io/guides/hibernate-orm-panache)
- [Writing REST Services with Quarkus REST](https://quarkus.io/guides/rest)
- [Configure data sources in Quarkus](https://quarkus.io/guides/datasource)
- [Dev Services for databases](https://quarkus.io/guides/databases-dev-services)
