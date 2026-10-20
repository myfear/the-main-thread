Add optional catalog search to this Quarkus application.

Requirements:

- `GET /catalog` must continue to return all three items.
- `GET /catalog?q=robot` must return only the item named `Robot arm`.
- Search must be case-insensitive and must match a substring in either the SKU or the name.
- A missing, empty, or blank `q` value must return all items.
- Add tests for the new behavior.
- Edit only `src/main/java/com/mainthread/catalog/CatalogResource.java` and `src/test/java/com/mainthread/catalog/CatalogResourceTest.java`.
- Run `./mvnw test` before you finish.
- Do not commit the changes.

In the final response, summarize the files changed and the test result.
