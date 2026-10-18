# Glühwein live orders

A Christmas-market order tracker built with Java 25 and Quarkus 3.40.1. Place an order, prepare it in a separate counter tab, and watch the customer ticket update through Server-Sent Events (SSE). Disconnect the ticket, change the order, and reconnect to recover its current state.

Companion to [Your Glühwein Is Ready: Live Order Updates with Quarkus](https://www.the-main-thread.com/p/quarkus-server-sent-events-live-orders).

## Run the market

You need JDK 25, a browser, and network access for the first Maven dependency download. Check `java -version` and make sure `JAVA_HOME` points to your JDK 25 installation. No database, container engine, Node.js, or frontend build is required.

From this directory, next to `pom.xml`:

```bash
./mvnw quarkus:dev
```

Open [the order page](http://localhost:8080). The application binds to loopback because the counter is an unauthenticated local demo.

1. Choose a drink, enter your first name, select one to six cups, and choose **Place your order**.
2. Open **Stall counter** in another tab. Choose **Start preparing** for your order. Its ticket updates immediately.
3. Choose **Disconnect** on the customer ticket. At the counter, choose **Mark ready**. The disconnected ticket retains its last known state and displays a stale-state notice.
4. Choose **Reconnect**. The ticket receives the latest snapshot and says the drink is ready. Expand **Under the counter** to inspect the revision and JSON.
5. Refresh the customer page. The order ID in its URL restores the same ticket.

**Disconnect** closes only this ticket's SSE stream. It is a deterministic teaching control. EventSource normally reconnects after a dropped connection, but some HTTP errors stop automatic retries. The demo's error notice does not distinguish these terminal failures; use **Disconnect** then **Reconnect** once the endpoint is available. **Order another round** starts a new ticket; previous orders remain at the counter.

Stop with Ctrl+C. Orders are in memory, with a maximum of 100 per application run. Restart for a fresh market; old ticket links then return 404.

## Build and test

```bash
./mvnw verify -DskipITs=false
java -jar target/quarkus-app/quarkus-run.jar
```

The first command runs store concurrency tests, `@QuarkusTest` HTTP/SSE tests, and the same HTTP contract against the packaged JAR using `@QuarkusIntegrationTest`. Tests cover missed-update recovery, stale counter commands, validation, missing tickets, stream cancellation, and packaged browser assets. The Java tests require no browser or Python.

`./mvnw test` runs the unit and Quarkus application tests without the packaged JAR checks. Native-image execution has not been validated.

If port 8080 is occupied:

```bash
./mvnw quarkus:dev -Dquarkus.http.port=8090
```

Use the corresponding port in your browser. Development mode can reload the application after source changes; treat in-memory orders as disposable.

## Inspect the API

Create an order:

```bash
curl -i http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"name":"Mia","drink":"RED","quantity":2}'
```

Copy the UUID from the JSON response into `ORDER_ID`:

```bash
ORDER_ID='paste-the-returned-uuid-here'
curl -N "http://localhost:8080/api/orders/$ORDER_ID/events"
```

Leave the stream open. In another terminal, assign the same `ORDER_ID`, then advance the order:

```bash
ORDER_ID='paste-the-returned-uuid-here'
curl -i -X POST "http://localhost:8080/api/orders/$ORDER_ID/advance" \
  -H 'Content-Type: application/json' \
  -d '{"revision":1}'
```

Revision 1 is queued, 2 is preparing, and 3 is ready. To mark ready, send the same request with `"revision":2`. A stale revision or an attempt to advance a ready order returns 409. Only the server selects the next state.

- `POST /api/orders`: create a ticket, returning 201 and its `Location`. Drinks: `RED`, `WHITE`, `APPLE`. Names: 1–32 characters, not all whitespace. Quantity: 1–6.
- `GET /api/orders/{id}`: current ticket, or 404 if it does not exist.
- `GET /api/orders/{id}/events`: named `order` SSE events, each containing a complete ticket snapshot and a revision ID.
- `POST /api/orders/{id}/advance`: advance with the expected `revision`.
- `GET /api/orders`: all demo orders.
- `GET /api/orders/events`: named `board` events containing the current list of orders.

Each subscription receives a snapshot immediately. Reconnects also receive the current snapshot, regardless of `Last-Event-ID`; this example does not implement event replay. A slow subscriber can skip intermediate snapshots. The stream keeps the newest pending value, and heartbeat comments are sent every 15 seconds. Disconnecting removes the listener.

## Code map

- `Order.java`: immutable ticket snapshot and drink/status enums.
- `OrderStore.java`: synchronized in-memory transitions and atomic subscription/snapshot registration.
- `OrderResource.java`: validated HTTP commands and SSE transport.
- `ApiErrors.java`: JSON messages for HTTP errors.
- `src/main/resources/META-INF/resources/`: HTML, CSS, and JavaScript served directly by Quarkus.
- `src/test/java/com/themainthread/gluhwein/`: store, application, and packaged JAR checks.

## Local demo boundaries

The application has no authentication, customer ownership checks, payment handling, persistence, or multi-instance coordination. UUIDs identify tickets; they do not authorize access. The counter and order list are intentionally open on loopback. Do not expose this demo unchanged as a public ordering service.

A successful POST whose response is lost can create an order the browser cannot confirm. The UI asks you to check the counter before trying again; it does not automatically retry creation. A real service needs an idempotency strategy, identity and permissions, durable state, retention, connection limits, and deployment-specific streaming checks.

For deployment behind a proxy, configure its buffering and idle timeouts for SSE. The demo sends `Cache-Control: no-store`, `X-Accel-Buffering: no`, and heartbeat comments; these do not replace verifying your actual proxy.
