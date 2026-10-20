Add express shipping quotes to this Quarkus application.

Requirements:

- `GET /shipping/quote` must continue to return `{"service":"standard","price":12}`.
- `GET /shipping/quote?speed=standard` must return the same standard quote.
- `GET /shipping/quote?speed=express` must return `{"service":"express","price":24}`.
- Any other `speed` value must return HTTP 400.
- Add tests for the new behavior.
- Edit only `src/main/java/com/mainthread/shipping/ShippingQuoteResource.java` and `src/test/java/com/mainthread/shipping/ShippingQuoteResourceTest.java`.
- Run `./mvnw test` before you finish.
- Do not commit the changes.

In the final response, summarize the files changed and the test result.
