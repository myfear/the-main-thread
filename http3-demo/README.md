# HTTP/3 with Quarkus 4

A small Quarkus **4.0.0.Beta1** application targeting **Java 21**. `GET /protocol` returns the Vert.x request version: `HTTP_1_1`, `HTTP_2`, or `HTTP_3`.

## Requirements

- Java 21 selected for both `java` and `JAVA_HOME`.
- Quarkus CLI for recreating the project; CLI 3.39.1 was used.
- Podman with a working Linux runtime. The executed VM was Linux ARM64.
- OpenSSL 3 for the disposable local certificate.
- Curl with `HTTP2` and `HTTP3` in `curl --version`.

The Maven Wrapper downloads Maven 3.9.16. Run all commands from this directory. On the tested Mac, `/usr/bin/curl` did not support HTTP/3; `/opt/homebrew/opt/curl/bin/curl` 8.21.0 did. Select an HTTP/3-capable curl before running the commands. For Homebrew:

```bash
export PATH="$(brew --prefix curl)/bin:$PATH"
curl --version
```

## Local certificate

```bash
mkdir -p .certs
openssl req -x509 -newkey rsa:2048 -noenc -days 7 \
  -subj '/CN=localhost' \
  -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1' \
  -keyout .certs/localhost-key.pem \
  -out .certs/localhost.pem
chmod 644 .certs/localhost-key.pem
```

The directory is Git-ignored. The readable key allows the non-root container user to access this disposable tutorial certificate. For production, use a managed certificate and a restricted secret mount.

## Build and test

```bash
./mvnw verify
```

The default build tests HTTP/1.1, HTTP/2, and HTTP/1.1 Alt-Svc using Java's HTTP client. The HTTP/3 test is skipped until an HTTP/3-capable curl executable is supplied. Enable it with:

```bash
./mvnw verify -Dhttp3.curl="$(command -v curl)"
```

Run the same three checks against the packaged application as well:

```bash
HTTP3_CERTIFICATE="$PWD/.certs/localhost.pem" \
HTTP3_PRIVATE_KEY="$PWD/.certs/localhost-key.pem" \
./mvnw verify -DskipITs=false -Dhttp3.curl="$(command -v curl)"
```

This executes three `@QuarkusTest` checks and three `@QuarkusIntegrationTest` checks. The Java HTTP client verifies negotiated HTTP/1.1 and HTTP/2, status, and body. The Java test launches curl with `--http3-only --fail` for HTTP/3 because Java 21's HTTP client does not implement that protocol. Test certificate trust is relaxed only in test code.

## Run the packaged JVM application

```bash
HTTP3_CERTIFICATE="$PWD/.certs/localhost.pem" \
HTTP3_PRIVATE_KEY="$PWD/.certs/localhost-key.pem" \
java -Dquarkus.http.host=127.0.0.1 -jar target/quarkus-app/quarkus-run.jar
```

In a second terminal:

```bash
curl -k --fail --http1.1 -i https://localhost:8443/protocol
curl -k --fail --http2 https://localhost:8443/protocol
curl -k --fail --http3-only -v --max-time 10 https://localhost:8443/protocol
```

HTTP/1.1 advertises `h3=":8443"; ma=86400`. The bodies identify all three protocols. `-k` is for the local certificate. Stop the JVM with Ctrl+C before starting the container.

**Beta1 observations:** dev-mode HTTP/3 returned HTTP 500 from `VertxHttpHotReplacementSetup`; HTTP/2 responses omitted Alt-Svc. Test mode and the packaged application served HTTP/3 successfully. The walkthrough uses the packaged application and inspects advertisement through HTTP/1.1. See [VALIDATION.md](VALIDATION.md) for evidence.

## Container experiment

The POM includes native QUIC libraries for Linux ARM64 and Linux x86_64, in addition to the host library selected by the extension's Maven profiles. Linux ARM64 was executed; Linux x86_64 packaging was inspected but not run.

```bash
podman build -f src/main/docker/Dockerfile.jvm -t localhost/http3-demo .

podman run --rm -d --name http3-tcp-only \
  -p 127.0.0.1:8443:8443/tcp \
  -v "$PWD/.certs:/certs:ro,Z" \
  localhost/http3-demo
podman logs http3-tcp-only
```

After the startup message, HTTP/2 succeeds and HTTP/3 fails:

```bash
curl -k --fail --http2 https://localhost:8443/protocol
curl -k --fail --http3-only --max-time 5 https://localhost:8443/protocol
```

Publish UDP as well:

```bash
podman stop http3-tcp-only
podman run --rm -d --name http3-both \
  -p 127.0.0.1:8443:8443/tcp \
  -p 127.0.0.1:8443:8443/udp \
  -v "$PWD/.certs:/certs:ro,Z" \
  localhost/http3-demo
podman logs http3-both
```

After startup, the same request returns `HTTP_3`:

```bash
curl -k --fail --http3-only --max-time 10 https://localhost:8443/protocol
```

## Cleanup

```bash
podman stop http3-both
rm -rf .certs
```

Optionally remove the image with `podman image rm localhost/http3-demo`. Regenerate the certificate before repeating packaged tests or the container experiment.

## References

- [Quarkus 4 Beta1 announcement](https://quarkus.io/blog/quarkus-4-0-0-beta1-released/)
- [HTTP/3 reference](https://quarkus.io/version/main/guides/http3-reference/)
- [TLS registry reference](https://quarkus.io/version/main/guides/tls-registry-reference/)
- [HTTP/3 with curl](https://curl.se/docs/http3.html)
