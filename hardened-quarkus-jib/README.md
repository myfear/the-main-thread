# Hardened Quarkus with Jib

This Quarkus 3.37.2 demo builds a JVM container image with Jib and the Red Hat Hardened Images OpenJDK 25 runtime from Project Hummingbird. It deliberately has no Dockerfile.

Run the application tests:

```bash
./mvnw test
```

Build the image into the local Podman image store:

```bash
./mvnw verify -DskipITs=false -Dquarkus.container-image.build=true
```

On ARM64, add `-Dquarkus.jib.platforms=linux/arm64` to build the native platform.

Run it:

```bash
podman run --rm --name hardened-quarkus -p 8080:8080 \
  themainthread/hardened-quarkus-jib:1.0.0-SNAPSHOT
```

Then call `http://localhost:8080/status` and `http://localhost:8080/q/health/ready`.

The full tutorial is in [article.md](article.md).
