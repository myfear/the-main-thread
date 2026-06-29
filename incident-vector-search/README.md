# Incident Vector Search

Incident Vector Search is a small Quarkus service that stores Java incident reports in Qdrant and searches for similar failures. It uses deterministic feature hashing instead of an embedding model, so the demo stays local, repeatable, and easy to inspect.

The project supports the article in this directory:

- `article.md`

## What It Does

- Indexes incident reports with service, environment, exception type, message, stack frames, and resolution notes
- Converts incidents into 384-dimensional vectors with deterministic Java code
- Stores vectors and payload in Qdrant through the Quarkiverse Qdrant extension
- Searches for similar incidents with optional payload filters
- Uses Qdrant Dev Services in dev and test mode
- Exposes readiness through SmallRye Health

## Prerequisites

- JDK 21
- Podman or another Docker-compatible container runtime for Qdrant Dev Services
- `curl` and `jq`

## Run the App

```bash
./mvnw quarkus:dev
```

Quarkus starts the application on `http://localhost:8080`. Qdrant starts automatically as a Dev Service.

## Seed Example Incidents

```bash
curl -s -X POST http://localhost:8080/incidents/seed | jq .
```

Expected shape:

```json
{
  "indexed": 5,
  "ids": [
    "INC-1001",
    "INC-1002",
    "INC-1003",
    "INC-1004",
    "INC-1005"
  ]
}
```

## Search for a Similar Incident

```bash
curl -s -X POST http://localhost:8080/incidents/search \
  -H "Content-Type: application/json" \
  -d '{
    "incident": {
      "service": "checkout-service",
      "environment": "prod",
      "exceptionType": "java.lang.NullPointerException",
      "message": "Cannot invoke DiscountPolicy.percentage because policy is null while pricing cart",
      "stackTrace": [
        "dev.mainthread.checkout.CartPriceCalculator.applyDiscount(CartPriceCalculator.java:91)",
        "dev.mainthread.checkout.CheckoutService.priceCart(CheckoutService.java:47)",
        "dev.mainthread.checkout.CheckoutResource.pay(CheckoutResource.java:31)"
      ]
    },
    "limit": 3,
    "minScore": 0.60,
    "filterService": "checkout-service",
    "filterEnvironment": "prod",
    "onlyResolved": true
  }' | jq .
```

The first match should be `INC-1001`.

## Index Your Own Incident

```bash
curl -s -X POST http://localhost:8080/incidents \
  -H "Content-Type: application/json" \
  -d '{
    "id": "INC-2001",
    "service": "checkout-service",
    "environment": "prod",
    "exceptionType": "java.lang.IllegalStateException",
    "message": "Cart total changed after payment authorization started",
    "stackTrace": [
      "dev.mainthread.checkout.PaymentWorkflow.authorize(PaymentWorkflow.java:88)",
      "dev.mainthread.checkout.CheckoutResource.pay(CheckoutResource.java:31)"
    ],
    "resolvedBy": "Re-read the cart version before authorization",
    "incidentUrl": "https://runbooks.example.com/incidents/INC-2001"
  }' | jq .
```

## Health Check

```bash
curl -s http://localhost:8080/q/health/ready | jq .
```

When Qdrant is reachable, the readiness response is `UP` and includes the extension-published Qdrant check.
In this sample it appears as `Qdrant REST Client health check`.

## Run Tests

```bash
./mvnw test
```

The test suite starts Qdrant through Dev Services and verifies:

- Similar checkout failures rank above unrelated billing failures
- `POST /incidents/seed` indexes the sample set
- `POST /incidents/search` returns `INC-1001` for a matching checkout incident
- Invalid incident input returns HTTP 400

## Production Notes

For external Qdrant, disable Dev Services by setting a real host:

```properties
quarkus.qdrant.host=qdrant.example.com
quarkus.qdrant.port=6333
quarkus.qdrant.api-key=${QDRANT_API_KEY}
quarkus.qdrant.use-tls=true
```

The demo creates the collection if it does not exist. In production, create collections and payload indexes through a controlled migration or provisioning step.

## Built With

- Quarkus 3.37.0
- Java 21
- Quarkiverse Qdrant 0.1.0
- Qdrant `v1.18-unprivileged` in Dev Services
