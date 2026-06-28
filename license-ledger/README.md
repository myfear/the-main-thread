# License Ledger

`license-ledger` is a small Quarkus REST service for the SPDX hands-on article in this repo. It reviews Maven coordinates, turns them into package URLs, and tags the submitted SPDX license expression as `approved`, `manual-review`, or `blocked`.

The point of the app is not a production-ready approval engine. It gives the build a small but real dependency graph so the generated SPDX SBOM is worth reading.

## Stack

- Quarkus 3.27.2
- Java 21 bytecode target
- `quarkus-rest-jackson`
- `io.quarkiverse.spdx:quarkus-spdx-v3:0.0.1`
- `com.github.package-url:packageurl-java:1.5.0`

## Endpoints

- `GET /components/demo` returns three sample components with package URLs and policy decisions
- `POST /components/review` accepts JSON and returns the normalized component report

Example request:

```json
{
  "supplier": "Package URL",
  "groupId": "com.github.package-url",
  "artifactId": "packageurl-java",
  "version": "1.5.0",
  "licenseExpression": "MIT"
}
```

## Run It

Start dev mode:

```bash
./mvnw quarkus:dev
```

Package the app and run the tests:

```bash
./mvnw package
```

The current Quarkus 3.27.2 line behaves best with a Java 21 runtime. On this workstation, Java 25 still builds and tests, but Quarkus test runs print an extra `--add-opens java.base/java.lang=ALL-UNNAMED` warning from JBoss Threads. The article sticks to Java 21 to avoid turning that into the main story.

## SPDX Output

With `quarkus-spdx-v3`, packaging writes:

```text
target/quarkus-run-spdx.json
```

That file is SPDX 3.0.1 JSON-LD even though the extension currently uses a `.json` filename. The document starts with an `@context` entry that points to the SPDX 3.0.1 JSON-LD context.

The temporary validation run for the article also confirmed the `v2` path:

```text
target/quarkus-run-spdx.json
target/quarkus-run-spdx.spdx
```

That second file is the SPDX 2.3 tag-value serialization produced when `quarkus.spdx.format=all` is enabled.

## Validation Notes

The article uses the SPDX `ntia-conformance-checker` CLI for a reality check:

```bash
sbomcheck --sbom-spec spdx3 --comply ntia target/quarkus-run-spdx.json
```

On the current sample, the checker reports that the generated SBOM is useful but not fully NTIA- or CISA-minimum conformant because some component suppliers and versions are not populated in the generated data. That is an important result, and the article keeps it.

## Related Guides

- [Quarkus REST JSON guide](https://quarkus.io/guides/rest-json)
- [Quarkus CLI guide](https://quarkus.io/guides/cli-tooling)
- [Quarkus SPDX project](https://github.com/quarkiverse/quarkus-spdx)
