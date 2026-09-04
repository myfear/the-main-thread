# Quarkus Aesh operations terminal

This is the runnable companion project for “Your Quarkus Application Just Grew a Terminal.” It embeds a bounded SwiftShip operations console in a Quarkus application and exposes the same Aesh commands through SSH and a browser WebSocket terminal.

The sample uses deterministic in-memory data. It demonstrates the terminal boundary; it is not a substitute for a durable dispatch service.

## Prerequisites

- JDK 25
- OpenSSH client tools

## Create a development SSH key

The current Aesh SSH stack does not support Ed25519 client keys. Generate an RSA key for this demo:

```bash
mkdir -p target/aesh
ssh-keygen -q -t rsa -b 3072 -N '' -f target/aesh/operator
cp target/aesh/operator.pub target/aesh/authorized_keys
```

## Run the application

```bash
./mvnw quarkus:dev
```

Open <http://localhost:8080/aesh/index.html> and sign in with the development-only credentials `operator` / `terminal`.

Alternatively, connect over SSH:

```bash
ssh -tt -i target/aesh/operator \
  -p 2222 \
  -o IdentitiesOnly=yes \
  operator@127.0.0.1
```

Try these commands:

```text
status
hub failures --hub=berlin
hub retry --hub=berlin SHP-1042 --dry-run
hub retry --hub=berlin SHP-1042 --confirm=SHP-1042
audit --limit=3
```

## Verify the project

```bash
./mvnw test
```

The test suite checks the command REPL, confirmation and idempotency behavior, HTTP role enforcement, and terminal transport readiness.

## Security boundary

The embedded users exist only in the Quarkus `dev` and `test` profiles. Production must supply a real HTTP identity provider, keep SSH keys and the generated host key outside the artifact, restrict both transports at the network layer, and persist operation audit events in a durable system.

The Aesh, Aesh SSH, and Aesh WebSocket extensions are preview features in Quarkus 3.39.1.
