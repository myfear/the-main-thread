# RefundDesk Flow

RefundDesk is the demo application for the Quarkus Flow 0.11.0 tutorial in this folder. It shows the boundary between deterministic refund policy code and a workflow that coordinates waiting, callbacks, CloudEvents, and persisted workflow state.

## What It Uses

- Quarkus 3.33.2
- Quarkus Flow 0.11.0
- `quarkus-flow-mvstore` for local workflow persistence
- Quarkus REST Jackson
- Reactive Messaging Kafka
- SmallRye OpenAPI
- Kafka Dev Services with Podman

## Run the App

Start dev mode from this directory:

```bash
./mvnw quarkus:dev
```

If port 8080 is already in use:

```bash
./mvnw quarkus:dev -Dquarkus.http.port=8082
```

Submit a refund that is approved by policy:

```bash
curl -i -X POST http://localhost:8080/refunds \
  -H 'Content-Type: application/json' \
  -d '{
    "refundId": "refund-1001",
    "customerId": "customer-7",
    "amount": 42.00,
    "receiptPresent": true,
    "accountAgeDays": 120,
    "chargebackCount": 0
  }'
```

Read the result:

```bash
curl http://localhost:8080/refunds/refund-1001
```

Submit a refund that waits for manual review:

```bash
curl -i -X POST http://localhost:8080/refunds \
  -H 'Content-Type: application/json' \
  -d '{
    "refundId": "refund-1003",
    "customerId": "customer-9",
    "amount": 450.00,
    "receiptPresent": true,
    "accountAgeDays": 15,
    "chargebackCount": 2
  }'
```

Use the returned `workflowInstanceId` after the workflow reaches the `waitForReview` step:

```bash
curl -i -X POST http://localhost:8080/refunds/refund-1003/review/01J... \
  -H 'Content-Type: application/json' \
  -d '{
    "refundId": "refund-1003",
    "outcome": "APPROVED",
    "reviewer": "alex",
    "note": "receipt and order history checked"
  }'
```

## Test

Run the unit and Quarkus tests:

```bash
./mvnw test
```

The workflow test consumes the `refund.review.required` CloudEvent from `flow-out`, reads the `flowinstanceid`, and sends a callback through the HTTP endpoint. That mirrors the contract a real reviewer UI would use.

## Runner Example

The `workflows/refund-policy.yaml` file is a small YAML workflow for the Quarkus Flow Runner image. Run it from this directory:

```bash
podman run --rm \
  --name refunddesk-runner \
  -p 8081:8080 \
  -v "$PWD/workflows:/deployments/workflows:ro" \
  quay.io/quarkiverse/quarkus-flow-runner:0.11.0-minimal
```

Execute the mounted workflow:

```bash
curl -X POST 'http://localhost:8081/q/flow/exec/refunddesk/refund-policy/1.0.0?wait=true' \
  -H 'Content-Type: application/json' \
  -d '{
    "amount": 42.00,
    "receiptPresent": true,
    "chargebackCount": 0
  }'
```
