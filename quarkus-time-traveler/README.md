# quarkus-time-traveler

Companion app for the Main Thread tutorial on Hibernate 7.4 temporal entities and audit logs in Quarkus.

## What it demonstrates

- `Account` uses `@Temporal` with a separate history table so the API can resolve an `Instant` to a changelog revision and read the older snapshot with `SessionFactory.withOptions().atChangeset(...)`
- `AccountHolder` uses `@Audited` and a `@Changelog` entity so the API can expose audit history, changeset ids, timestamps, and modified entity names
- `ChangesetCoordinatorContributor` bridges a current Quarkus `3.37.0.CR1` bootstrap gap so Hibernate 7.4 state-management services are available during metadata building
- `LedgerRevisionSupplier` keeps temporal history and audit history on the same revision-id model in this preview-line setup
- Quarkus Dev Services starts PostgreSQL automatically in dev and test mode

## Endpoints

- `POST /accounts` creates an account
- `GET /accounts/{id}` reads the current account state
- `PUT /accounts/{id}/balance` changes balance and status
- `GET /accounts/{id}/snapshot?asOf=...` reads the account state effective at a past instant
- `POST /holders` creates an account holder
- `PUT /holders/{id}` updates KYC-facing holder data
- `GET /holders/{id}/audit` returns the audit trail for that holder

## Run it

```bash
./mvnw quarkus:dev
```

The app expects Java 21 and uses Quarkus `3.37.0.CR1` on purpose because Hibernate ORM `7.4.0.Final` is not in the latest stable Quarkus line yet.

## Test it

```bash
./mvnw test
```

## Related guides

- [Quarkus REST](https://quarkus.io/guides/rest)
- [Hibernate ORM in Quarkus](https://quarkus.io/guides/hibernate-orm)
- [Quarkus Datasource](https://quarkus.io/guides/datasource)
