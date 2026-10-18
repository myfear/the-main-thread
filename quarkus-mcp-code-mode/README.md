# Code Mode with Quarkus and QuickJS

A Quarkus command-mode MCP gateway that executes submitted JavaScript against a generated SDK for Quarkus Agent MCP. Scripts can compose calls, filter logs, and compute reports. Java checks every upstream operation, and each script runs in a disposable worker JVM.

Companion to [Build code mode for Quarkus MCP with QuickJS](https://www.the-main-thread.com/p/quarkus-mcp-code-mode-quickjs).

## Prerequisites

- JDK 25, with `JAVA_HOME` set to its installation and its `bin` directory on `PATH`.
- Git and a POSIX shell.
- [JBang](https://www.jbang.dev/documentation/jbang/latest/installation.html) for the pinned upstream server, or an existing Quarkus Agent MCP 1.0.10 runner JAR.
- Network access for the first Maven/JBang dependency download. Maven is supplied by each project's wrapper.

The tested stack is Quarkus 3.40.1, Quarkiverse QuickJS4J 0.0.3, QuickJS4J 0.0.10, Quarkiverse MCP Server 2.0.0 for the stdio endpoint, MCP Java SDK 1.1.5 with Jackson 2 for the clients, and Quarkus Agent MCP 1.0.10. This companion targets JVM execution. Native-image execution is not verified. The saved examples require no model credentials, Node.js, Python, database, or container.

## Build and run

Run these commands from this directory:

```sh
./mvnw verify
cd demo-app
./mvnw verify
cd ..

export PROJECT_DIR="$(./scripts/prepare-demo.sh)"
export JBANG_BIN="$(command -v jbang)"
export START_DEMO=true
./scripts/run-examples.sh
```

`prepare-demo.sh` copies the supplied demo into `.local/demo-app` and creates a separate Git index there. The pinned upstream server checks that a Maven wrapper is tracked before executing it. The script prints the absolute project directory and reuses an existing copy on subsequent runs. It does not modify the surrounding repository's Git index.

The launcher starts the packaged gateway. Trusted startup asks its private upstream server to start the demo at `http://127.0.0.1:18191` and requests `/items`, which emits the sample log events once per application lifetime. `DEMO_PORT` selects another free port. `START_DEMO=true` is intended for this bundled demo; its readiness check expects `/items`.

The Java client then submits four scripts over MCP:

- [investigate.js](examples/investigate.js) checks status and payment errors, then discovers and calls the exception diagnostic only when a payment error is present. It returns status, matching errors, and `hasException`.
- [triage.js](examples/triage.js) combines status and log reads, returning only the marked error line alongside status.
- [incident-report.js](examples/incident-report.js) deduplicates request events, chooses the highest retry attempt, groups results by endpoint, and calculates nearest-rank p95 from successful final attempts.
- [diagnostic.js](examples/diagnostic.js) discovers the Dev MCP exception tool and invokes it with its schema's string-valued depth.

Each successful execution envelope has `ok: true` and a `value`. The triage result contains `DEMO_ERROR payment retry exhausted for request pay-1`. The incident report equals [incident-report.expected.json](examples/incident-report.expected.json), including `failedAfterRetry: ["pay-1"]`. The diagnostic reports `hasException: false`: logging an error did not throw an exception.

The client compares any adjacent `.expected.json` file with the returned value, checks that workers have exited, and fails on errors. Closing stdin stops the gateway and its owned upstream process tree, including the demo it started. It does not issue a stop request for independently managed applications.

Run an individual example in a new session with:

```sh
./scripts/run-examples.sh examples/incident-report.js
```

To use an already downloaded runner instead of JBang:

```sh
export UPSTREAM_JAR=/absolute/path/to/quarkus-agent-mcp-1.0.10-runner.jar
./scripts/run-examples.sh
```

The gateway verifies that JAR against its pinned SHA-256. `UPSTREAM_JAR` takes precedence over `JBANG_BIN`.

## Compare direct MCP with one code submission

After the build and environment setup above, run:

```sh
./scripts/compare-approaches.sh
```

This uses JBang to start the pinned Quarkus Agent MCP directly and sends the four separate `tools/call` parameter objects in [investigate-direct.json](examples/investigate-direct.json). The client replaces the `projectDir` placeholder with `PROJECT_DIR`. The demo is running and emits a payment error, so the sequence checks status, reads logs, discovers the exception diagnostic, and invokes it. Each direct response is available to the caller before the next request.

The client closes that session, starts the demo again through the gateway, and sends the complete [investigate.js](examples/investigate.js) body as one `code` request. That script makes the same four upstream calls and returns the filtered finding. It stops after status if the application is not running, and skips discovery and the diagnostic when there is no matching error. An unchecked diagnostic is represented by `hasException: null`.

The comparison prints both submissions and findings, with `equal: true` only after checking both against the expected payment error and `hasException: false`. Demo startup and MCP initialization are setup and are excluded from the displayed task calls. This is a deterministic replay of a task an agent could attempt; it does not run a model or measure token savings. Both sessions own and stop their demo processes. The script forces `START_DEMO=true`; use it with the supplied demo.

Quarkus Agent MCP already exposes dynamic Dev MCP operations through discovery and invocation tools. Code Mode adds JavaScript control flow and result processing. The `code` description includes the full generated SDK, so fewer exposed tool names do not by themselves prove a smaller model context.

## Connect an MCP client

After building, configure a stdio server in your MCP client. Clients using the common `mcpServers` JSON format can use this entry, replacing every absolute path:

```json
{
  "mcpServers": {
    "quarkus-code-mode": {
      "command": "/absolute/path/to/quarkus-mcp-code-mode/scripts/start-mcp.sh",
      "env": {
        "JAVA_HOME": "/absolute/path/to/jdk-25",
        "PROJECT_DIR": "/absolute/path/to/quarkus-mcp-code-mode/.local/demo-app",
        "JBANG_BIN": "/absolute/path/to/jbang",
        "START_DEMO": "true"
      }
    }
  }
}
```

Run `scripts/prepare-demo.sh` first. Keep the client session open while using the tools. The only public tool is `code`, with one required string argument named `script`. Its description includes the SDK declarations and argument schemas. The script receives `input.projectDir`, set by the gateway, and a `quarkus` SDK object:

```javascript
const result = quarkus.quarkus_logs({ projectDir: input.projectDir, lines: 200 });
if (result.isError) throw new Error("Log retrieval failed");
return result.content.filter(block => block.type === "text")
    .flatMap(block => block.text.split(/\r?\n/))
    .filter(line => line.includes("DEMO_ERROR"));
```

Return a JSON-compatible value directly. Promises, TypeScript syntax, and Node.js APIs are unsupported. `JSON.stringify(report)` returns a string inside the execution envelope; use `return report` for an object. Results from upstream calls preserve `content`, `structuredContent`, `isError`, and unknown fields. Check upstream `isError` in your script.

For an existing application, select its absolute directory and leave `START_DEMO=false`. Status works without starting an application; runtime diagnostics require a compatible application already available to the upstream server. The automated walkthrough uses the bundled demo so lifecycle ownership is explicit.

## Implementation

The main source package is `src/main/java/com/themainthread/codemode/`:

- `GatewayMain` coordinates Quarkus startup and shutdown. The CDI bean `GatewayTools` registers `code` through the Quarkiverse `ToolManager`, handles cancellation, and returns `ToolResponse`. `GatewayConfig` maps Quarkus configuration.
- `Catalog` derives JavaScript wrappers and TypeScript declarations from the pinned catalog, then checks a live upstream catalog for drift.
- `DynamicScript`, `HostBridge`, and `DynamicRuntime` connect dynamically supplied JavaScript to the QuickJS4J generated factory.
- `WorkerMain` handles one script. `ChildJvm` owns its process lifecycle.
- `GatewayExecutor` dispatches callbacks, enforces deadlines and budgets, and handles recovery.
- `Policy` validates original schemas, the authorized project path, and allowed outer/nested operations.
- The Quarkiverse stdio extension handles the public MCP endpoint. `GatewayInput` gates startup and bounds incoming lines; `OwnedTransport` and `Upstream` bound upstream frames and preserve raw MCP result envelopes.
- `ExampleClient` runs the saved scripts against the packaged gateway using the official MCP client. `ComparisonClient` checks the direct sequence and the single code submission against the same expected finding.

The worker classpath comes exclusively from the packaged `target/quarkus-app` distribution. Keep that whole directory when moving the application; `quarkus-run.jar` alone is insufficient.

Generated SDK files appear in `.local/session/sdk/`. Bounded execution traces appear in `.local/session/gateway.jsonl`; they record hashes, outcomes, operation names, and process metrics rather than script text, logs, or raw argument values. `SESSION_DIR` selects another directory. Run separate sessions with separate directories.

## Execution policy

The configured project is the only authorized project. The gateway allows status, logs, skills, documentation search, dynamic tool discovery, and the discovered `devui-exceptions_getLastException` diagnostic. Java denies other operations, including lifecycle changes, tests, and configuration changes, even when they appear in the generated catalog.

Diagnostic discovery must occur in the current upstream session. Its `maxCauseDepth` argument must be a string from `"1"` to `"5"`, serialized inside `toolArguments`. The wrapper does not authorize a nested method merely because the outer `quarkus_callTool` method is available.

Default limits are 32 KiB for submitted scripts and callback arguments, 10 callbacks per script, 16 KiB for returned JSON, 64 KiB for incoming stdio lines and upstream frames, a 15-second upstream call timeout, and a 60-second execution deadline. The generated SDK adds internal framing overhead. `gateway.tool-timeout-ms` and `gateway.execution-timeout-ms` configure the deadlines. Cleanup has additional bounded waits.

One script executes at a time. Overlap returns `BUSY`. These limits bound execution and individual messages; the extension owns protocol scheduling and this application does not impose a separate queue limit. Script and policy errors normally leave the upstream session available; timeout or transport failure closes the owned upstream process tree and clears discovery. This also stops a demo started by that upstream. A later script can reconnect, but reconnecting does not repeat demo startup. Restart the gateway with `START_DEMO=true` before using the demo's runtime diagnostics again. Each script has a new worker and new guest globals. There is no transaction or rollback across calls.

This is a local feasibility implementation. It exposes no application API for filesystem, networking, or arbitrary Java access to guest code, and workers receive an empty environment. The worker JVM nevertheless has the operating-system user's permissions. JVM heap settings do not bound total process RSS. Add OS-level isolation before accepting hostile code from unrelated users.

## Tests and troubleshooting

`./mvnw verify` checks the generated CDI factory, serialization, script examples, schema validation, path policy, protocol negotiation, cancellation, busy-loop termination, upstream failures, result limits, and recovery. Process tests need permission to inspect and terminate their child JVMs. `cd demo-app && ./mvnw verify` checks the REST endpoint. `scripts/run-examples.sh` verifies the packaged runtime and real upstream integration.

- **`CATALOG_CHANGED`:** use the pinned upstream release. Changes require auditing the schemas and updating both the generator and tests where necessary.
- **`DISCOVERY_REQUIRED`:** call `quarkus_searchTools` before the diagnostic. Rediscover after a transport/session reset.
- **`RESULT_LIMIT`:** return the required fields from inside JavaScript; avoid returning large logs or stringifying an already structured result.
- **`CLEANUP_FAILED`:** the server refuses more work because it cannot confirm resource termination. Check process-inspection permissions and the owned processes before restarting.
- **Connection closes during startup:** run `scripts/start-mcp.sh` directly with the same environment and inspect stderr. Check the JDK, JBang/JAR path, tracked demo wrapper, and selected port. The terminal will then wait for MCP input; stop it with Ctrl-C.
- **No request events:** use the bundled demo with `START_DEMO=true`. The report script expects its `R ` event format and a complete event window. It is not a general log parser.

After all client sessions have closed, remove `.local/` to reset the copied demo and session files. Build output remains under each project's `target/` directory.

References: [Quarkiverse MCP Server](https://github.com/quarkiverse/quarkus-mcp-server), [Cloudflare's Code Mode article](https://blog.cloudflare.com/code-mode/), [Quarkiverse QuickJS4J](https://github.com/quarkiverse/quarkus-quickjs4j), [Quarkus Agent MCP](https://github.com/quarkusio/quarkus-agent-mcp), and the [MCP Java SDK](https://java.sdk.modelcontextprotocol.io/latest/).
