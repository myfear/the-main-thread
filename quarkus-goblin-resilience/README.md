# Quarkus Goblin resilience lab

This is the runnable companion project for “Make Your Quarkus Application Fail on Purpose.” It uses Goblin 0.0.1 to inject faults into an internal inventory endpoint while a public quote endpoint protects its REST client call with MicroProfile Fault Tolerance.

The two endpoints run in one JVM to keep the lab small. The REST client still crosses a real HTTP boundary:

```text
GET /quotes/{sku}
  -> InventoryGateway (@Timeout, @Retry, @Fallback)
  -> REST client
  -> Goblin request filter
  -> GET /internal/inventory/{sku}
```

## Prerequisites

- JDK 25
- No container runtime or external service

Goblin 0.0.1 is compiled for Java 25 and uses Quarkus 3.38.3.

## Run the latency experiment

Start Quarkus in dev mode:

```bash
./mvnw quarkus:dev
```

Call the public endpoint:

```bash
curl -s -w '\nstatus=%{http_code} time=%{time_total}s\n' \
  http://localhost:8080/quotes/sku-1
```

The configured Goblin delay is longer than the gateway timeout, so the result comes from the fallback:

```json
{"available":0,"service":"STANDARD","sku":"sku-1","source":"fallback"}
```

Open <http://localhost:8080/q/dev>, select **Goblin**, and use the dashboard to deactivate chaos or switch assault types. Goblin targets only `com.themainthread.goblin.inventory`, so the public endpoint remains reachable while the internal HTTP call fails.

## Run the deterministic test

```bash
./mvnw test
```

The test profile changes the assault to a fixed HTTP 503. The suite checks both the targeted internal failure and the public fallback response.

## Verify production mode

```bash
./mvnw package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar
```

In another terminal:

```bash
curl -s http://localhost:8080/internal/inventory/sku-1
```

Goblin 0.0.1 does not activate its assault engine in production launch mode, so this returns the live inventory response without injected latency.

## Scope

Goblin 0.0.1 is experimental. It injects faults at the inbound JAX-RS filter, keeps configuration and history in memory, and does not model pod failure, network partitions, or a broken process. Native compilation is outside the extension's version-one scope.
