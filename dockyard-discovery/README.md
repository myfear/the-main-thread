# dockyard-discovery

Hands-on demo for [Service Discovery Without Kubernetes Magic](article.md): two Quarkus services, one Consul agent, and Stork-backed client-side discovery with round-robin selection.

- `catalog-service` registers two instances in Consul (`8081`, `8082`)
- `checkout-service` discovers `catalog-service` through Stork and exposes `/quote/{sku}`
- `./scripts/smoke.sh` verifies Consul registration and round-robin once everything is running

## Prerequisites

- JDK 21
- Podman (Docker works with the same Consul run flags)
- Three terminals for the two provider instances and the consumer

## Quick start

Start Consul:

```bash
podman run --name consul -d \
  -p 8500:8500 \
  -p 8600:8600/udp \
  hashicorp/consul \
  consul agent -dev -client=0.0.0.0
```

Start the provider twice:

```bash
cd catalog-service
INSTANCE_ID=catalog-1 INSTANCE_COLOR=blue HTTP_PORT=8081 ./mvnw quarkus:dev
```

```bash
cd catalog-service
INSTANCE_ID=catalog-2 INSTANCE_COLOR=green HTTP_PORT=8082 ./mvnw quarkus:dev
```

If you use Docker Desktop instead of Podman, add `CONSUL_HEALTH_CHECK_HOST=host.docker.internal` to both provider commands.

Start the consumer:

```bash
cd checkout-service
./mvnw quarkus:dev
```

Verify:

```bash
curl -s http://localhost:8080/quote/sku-1
./scripts/smoke.sh
```

Optional local verification:

```bash
cd catalog-service && ./mvnw test
cd ../checkout-service && ./mvnw test
```

