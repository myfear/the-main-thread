# Quarkus Shim Secure Pipeline

This demo applies a temporary build-time patch to a fictional vendor policy SDK. The SDK treats every value except `DENY` as allowed. Quarkus Shim replaces that method during augmentation so only an explicit `ALLOW` decision succeeds.

The fictional SDK avoids making a vulnerability claim about a real library. It lives in its own Maven module so the Quarkus application consumes it as a normal binary dependency.

## Modules

- `vendor-policy` builds `com.themainthread.vendor:access-policy-sdk:1.0.0`
- `policy-service` exposes `GET /authorization/{decision}` and carries the shim
- `shim-policy.yaml` records the owner, target, expiry date, and removal condition
- `scripts` checks policy expiry and packaged build evidence

## Requirements

- Java 21
- A POSIX shell
- `curl` for the manual HTTP checks

No container runtime is needed.

## Test and package

Run the full reactor build from this directory:

```bash
./mvnw verify
./scripts/check-shim-policy.sh
./scripts/verify-build-evidence.sh
```

The build runs four JVM tests and three packaged-application tests. One JVM test disables Shim processing and confirms that the fictional vendor behavior still fails open. The other tests verify the patched behavior.

The packaged build also writes:

- `policy-service/target/shim/com.themainthread.vendor.LegacyDecisionEngine.txt`
- `policy-service/target/quarkus-run-cyclonedx.json`

## Run in dev mode

Start the reactor in dev mode:

```bash
./mvnw -pl policy-service -am quarkus:dev
```

In another terminal, compare an explicit allow decision with an unknown value:

```bash
curl -i http://localhost:8080/authorization/ALLOW
curl -i http://localhost:8080/authorization/REVIEW
```

The first request returns `200 OK`; the second returns `403 Forbidden`.

## Extension and guide links

- [Quarkus Shim](https://github.com/quarkiverse/quarkus-shim)
- [Quarkus Shim introduction](https://quarkus.io/blog/quarkus-shim/)
- [Quarkus REST JSON guide](https://quarkus.io/guides/rest-json)
- [Quarkus CycloneDX guide](https://quarkus.io/guides/cyclonedx)
