# Hibernate Search Noir

Companion project for *The Book That Exists but Cannot Be Found*.

The catalog stores Jo Nesbø and *The Snowman* in PostgreSQL and indexes them with Hibernate Search 8.4 into Elasticsearch 9. Search for `Nesbo` only hits after the `name` analyzer folds `ø` to `o`. The suite checks the database row, the indexed document, the analyzer tokens, and the ASCII search.

This example uses Quarkus 3.39.4 and Java 25. Dev Services starts PostgreSQL and Elasticsearch. Use disposable services for this demo. The `%dev` and `%test` profiles recreate the database and indexes. The `%prod` connection settings support a packaged demo; SQL seeding and the startup mass indexer run there too, and the mass indexer clears existing index contents before rebuilding them.

## Requirements

Use Java 25 and the Maven wrapper in this directory (`./mvnw`). Start Podman before launching the app so Dev Services can start PostgreSQL and Elasticsearch.

## Run

```bash
./mvnw quarkus:dev
```

Then:

```bash
curl 'http://localhost:8080/library/books?title=The%20Snowman'
curl 'http://localhost:8080/library/search?q=Nesbo'
curl --get --data-urlencode 'analyzer=name' --data-urlencode 'text=Nesbø' \
  'http://localhost:8080/library/analyze'
curl 'http://localhost:8080/library/index/author/1'
```

The search should return Jo Nesbø and *The Snowman*. The analyzer should emit `nesbo` for both `Nesbø` and `Nesbo`.

## Tests

```bash
./mvnw test
```

Look for `LibrarySearchTest` and `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`.

Stop a running application with `Ctrl+C`. Dev Services stops the containers.

## Guides

- [Use Hibernate Search with Hibernate ORM and Elasticsearch/OpenSearch](https://quarkus.io/guides/hibernate-search-orm-elasticsearch)
- [Dev Services for Elasticsearch](https://quarkus.io/guides/elasticsearch-dev-services)
- [Quarkus 3.24 release](https://quarkus.io/blog/quarkus-3-24-released/)
