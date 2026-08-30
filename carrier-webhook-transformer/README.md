# Carrier webhook transformer

This Quarkus demo validates an HMAC-signed carrier webhook, sends the raw JSON to an approved QuickJS4j transformer, validates the returned canonical shipment event, and remembers duplicate event IDs for the running process.

It uses Quarkus 3.28.4, Java 21, and `io.quarkiverse.quickjs4j:quarkus-quickjs4j:0.0.3`.

## Run it

```shell
./mvnw quarkus:dev
```

In a second terminal, send the signed fixture:

```shell
./scripts/verify.sh
```

## Test it

```shell
./mvnw test
```

The test suite checks the success path, duplicate delivery, signature rejection, transformer rejection, and that a dynamic module does not receive the browser `fetch` API by default.

`WebhookLedger` is intentionally in-memory. It makes the idempotency behavior visible in one small example. A deployed service needs a durable table with a unique `(carrier, event_id)` constraint before it acknowledges webhooks across restarts or replicas.
