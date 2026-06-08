# lambda-http-localhost

Small Quarkus sample for a hands-on tutorial about `quarkus-amazon-lambda-http`, the mock event server, and local Lambda-style development without leaving `localhost`.

The sample exposes two endpoints:

- `GET /quotes/{destination}` for a quote driven by path, query, and header input
- `POST /quotes/preview` for the same quote flow with a JSON body

It also includes a raw-event test that posts an `APIGatewayV2HTTPEvent` directly to `/_lambda_`.

## Prerequisites

- JDK 21 or newer
- Maven 3.9+
- Optional: AWS SAM CLI and Docker for the final packaged Lambda run

The project compiles with `maven.compiler.release=21` on purpose. The generated `sam.jvm.yaml` still targets the Lambda `java21` runtime, so keeping the bytecode on 21 avoids a deployment mismatch.

## Run in dev mode

```bash
./mvnw quarkus:dev
```

Then call the mock-event-backed HTTP endpoint:

```bash
curl -s 'http://localhost:8080/quotes/lisbon?speed=express&weightGrams=900' \
  -H 'X-Customer-Tier: gold'
```

You should get JSON back directly from the REST endpoint, including Lambda-shaped metadata such as `gatewayRequestId` and `stage`.

## Push a raw API Gateway event

Quarkus also exposes the raw event injection endpoint in dev mode:

```bash
curl -s -X POST http://localhost:8080/_lambda_ \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  --data @event.json
```

That response is an API Gateway proxy response object, so your business JSON lives inside the `body` field.

## Run the tests

```bash
./mvnw test
```

The tests cover:

- normal localhost calls through the mock event server
- JSON body handling
- invalid input
- a handcrafted raw `APIGatewayV2HTTPEvent`

## Package for Lambda

Build the deployment artifacts:

```bash
./mvnw package -DskipTests
```

That generates:

- `target/function.zip`
- `target/sam.jvm.yaml`
- `target/sam.native.yaml`

The JVM SAM template uses `io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest` with `Runtime: java21`. The native SAM template switches to `provided.al2023` and sets `DISABLE_SIGNAL_HANDLERS=true`.

## SAM CLI verification

If you want the packaging-level Lambda check instead of only the Quarkus mock-event loop, you need:

- AWS SAM CLI
- Docker or a Docker-compatible Podman socket

This sample was verified locally with SAM CLI 1.161.1 and Podman 5.8.2 on macOS because `/var/run/docker.sock` already pointed at the Podman machine socket.

Then run:

```bash
sam local start-api --template target/sam.jvm.yaml
```

Then hit:

```bash
curl -s 'http://127.0.0.1:3000/quotes/lisbon?speed=express&weightGrams=900' \
  -H 'X-Customer-Tier: gold'
```

## Related guides

- [AWS Lambda HTTP](https://quarkus.io/guides/aws-lambda-http)
- [Writing JSON REST Services](https://quarkus.io/guides/rest-json)
