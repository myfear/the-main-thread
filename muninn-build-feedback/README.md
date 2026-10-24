# Maven build feedback for coding agents

A Java 25 Quarkus shipping quote API with Muninn enabled as a project-local Maven
core extension. Use the included scenarios to inspect structured build failures
and let a shell-capable coding agent repair a boundary defect.

Article: [Give your coding agent better Maven feedback with Muninn](https://www.the-main-thread.com/p/maven-muninn-coding-agent-build-feedback).

## Prerequisites

- JDK 25.
- Git and a POSIX shell for the scenario commands.
- Network access to Maven Central on the first build. The wrapper downloads
  Maven 3.9.16; no separate Maven installation is required.
- Optional: [IBM Bob Shell](https://bob.ibm.com/docs/shell/getting-started/install-and-setup)
  or another coding agent that can execute commands and read project files.

The application uses Quarkus 3.40.1.1 and Muninn 0.1.1. It needs no database,
container engine, or model API key to build, test, or serve requests.

## Build and verify

From this project directory, run the application tests and packaged-application
tests:

```sh
./mvnw --batch-mode --no-transfer-progress clean verify -DskipITs=false -Dmuninn.output=json
```

The fixed application runs 18 tests with no failures, errors, or skipped tests.
Muninn writes a JSON result to stdout and saves the report and full log under
`target/muninn-output/`.

For ordinary Maven console output, use `-Dmuninn.output=off`. Activation through
`MUNINN_OUTPUT=json` or `MUNINN_OUTPUT=off` is also supported; a command-line
`-Dmuninn.output=...` overrides the environment setting.

## Run the API

Start development mode with ordinary console output:

```sh
./mvnw quarkus:dev -Dmuninn.output=off
```

Send a shipping quote request from another terminal:

```sh
curl -sS http://localhost:8080/shipping-quotes \
  -H 'Content-Type: application/json' \
  -d '{"subtotal":100.00,"method":"STANDARD"}'
```

The response contains `"shipping":0.00` and `"currency":"EUR"`. Standard
shipping costs EUR 4.99 below EUR 100.00 and is free at or above that subtotal.
Express shipping costs EUR 9.99 at every subtotal. Subtotals must be nonnegative,
have at most two fractional digits, and have at most nine integer digits.
Both request fields are required. Stop development mode with Ctrl+C before
running the build scenarios.

To run the packaged application after verification:

```sh
java -jar target/quarkus-app/quarkus-run.jar
```

## Introduce and repair the boundary defect

Apply the scenario patch to the fixed application:

```sh
git apply --check scenarios/boundary-bug.patch
git apply scenarios/boundary-bug.patch
./mvnw --batch-mode --no-transfer-progress verify -DskipITs=false -Dmuninn.output=json
```

This intentionally fails `waivesStandardShippingAtThreshold`: the expected
shipping cost is 0.00 and the implementation charges 4.99. The report names the
test and includes the assertion and application test stack frames.

To try the repair with Bob Shell, set the output mode in the environment and
start an interactive session:

```sh
MUNINN_OUTPUT=json bob chat
```

Give the agent this task:

```text
Repair the shipping quote implementation to satisfy the business rules in AGENTS.md.
First run ./mvnw --batch-mode --no-transfer-progress verify -DskipITs=false.
Inspect the failure, make the smallest production-code fix, and rerun the same command.
Do not weaken assertions, change the build configuration, or skip tests.
Read the full build log or test reports when the summary does not provide enough detail.
```

Review the production-code diff, then rerun full verification yourself. To compare
the ordinary console experience, use a fresh copy of the same broken scenario and
start the agent with `MUNINN_OUTPUT=off`.

If you have not repaired the code, reverse the patch to restore the fixed version:

```sh
git apply -R scenarios/boundary-bug.patch
```

## Inspect a Quarkus build-time failure

From the fixed application, apply a deliberately incomplete CDI component:

```sh
git apply scenarios/missing-carrier.patch
./mvnw --batch-mode --no-transfer-progress package -DskipTests -Dmuninn.output=json
```

This focused packaging check skips tests so the failure reaches Quarkus's
build-time CDI validation. Expect `kind: "mojo"`, the `quarkus:build` goal, and
an unsatisfied `CarrierGateway` dependency. Read `target/muninn-output/build.log`
to find the injection target. JSON is a first diagnostic step; it does not contain
every framework detail.

Remove the deliberately broken component and run complete verification:

```sh
git apply -R scenarios/missing-carrier.patch
./mvnw --batch-mode --no-transfer-progress clean verify -DskipITs=false -Dmuninn.output=json
```

## Use Muninn in another Maven application

Copy the extension declaration from `.mvn/extensions.xml` into that application's
project-local extension list. Keep any existing extensions. Muninn is a Maven core
extension; do not add it as an application dependency or Quarkus extension.

Enable JSON for individual commands or agent sessions. The full log is available
at the path in the report. Diagnostic budgets can limit the initial response:

```sh
./mvnw verify -Dmuninn.output=json \
  -Dmuninn.output.maxErrors=5 \
  -Dmuninn.output.maxTests=5 \
  -Dmuninn.output.maxFrames=3
```

Use the lifecycle and test options appropriate to your project. Inspect test
counts and Maven's exit code. Muninn 0.1.1 can include counts from existing XML
reports when the current command skips tests. Use `clean verify` without test-skipping
properties for final verification. Muninn 0.1.1 documents Maven 3.9/3.10 support;
other Maven versions and logging backends can leave ordinary output on the
console, with `captured: false` in the report.
