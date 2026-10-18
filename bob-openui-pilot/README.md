# Bob + OpenUI pilot

Turn activity notes into a report whose layout is chosen by IBM Bob. Bob Shell emits OpenUI Lang over Agent Client Protocol (ACP), Quarkus streams the text as AG-UI events, and React renders a small, typed component library.

The report can contain facts, attendance, outcomes, evidence links, and missing information. The attendance component calculates totals and flags exercise completions that exceed attendance. You can inspect and download Bob's original OpenUI response.

## Prerequisites

- Java 25.
- Node.js and npm. Validated with Node.js 26.7.0 and npm 11.19.0.
- An IBM Bob API key, either as a JSON key file containing an `apikey` property or as the `BOB_API_KEY` environment variable.
- Network access for package downloads and Bob inference.

The pinned stack uses Bob Shell 2.0.8, Quarkus 3.40.1, Quinoa 2.9.2, SmallRye ACP 0.2.1, OpenUI React Lang 1.0.1, and OpenUI React Headless 0.18.1.

## Run the app

Run these commands from the `bob-openui-pilot` directory.

1. Install Bob locally:

   ```bash
   npm install --prefix .local/bob --no-audit --no-fund @ibm/bob@2.0.8
   .local/bob/node_modules/.bin/bob --version
   ```

2. Configure the server. Replace the key-file path with your downloaded Bob key file:

   ```bash
   export BOB_BINARY="$PWD/.local/bob/node_modules/.bin/bob"
   export BOB_KEY_FILE="/absolute/path/to/bob-key.json"
   ```

   Keep the key file outside the project. The server reads it when opening a Bob session; the browser never receives the key. Alternatively, supply `BOB_API_KEY` through your environment. An environment key takes precedence over the file.

3. Start Quarkus:

   ```bash
   ./mvnw quarkus:dev
   ```

   Quinoa installs and builds the frontend. Open [Bob report studio](http://127.0.0.1:8096/).

4. Select **Workshop**, **Video**, or **Conflicting counts**, then select **Generate report**. The result shows the connected Bob version and the completed stream's duration. **Stop generation** closes the current session.

Each generation sends your notes to IBM Bob. Reports are drafts; review their facts and conclusions before use. The application does not store reports, but Bob may retain its own local session history according to its configuration.

## Build and test

Run the Java tests without a Bob credential:

```bash
./mvnw test
```

Tests replace Bob with a deterministic test double and cover event order, input validation, error redaction, cancellation, concurrent requests, and denied ACP tool permissions. For continuous testing in dev mode, press `r` in the Quarkus terminal.

Build a JVM application:

```bash
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

Keep the configured Bob binary and key file available when running the packaged application. This pilot targets a local JVM process; container and native-image deployment are not included.

To change the component library, edit `src/main/webui/src/library.tsx`, then regenerate its prompt:

```bash
npm --prefix src/main/webui run generate
npm --prefix src/main/webui run build
```

The frontend build checks that `src/main/resources/openui-system-prompt.txt` matches the component schema and prompt rules. Rebuild or restart Quarkus after changing the frontend; this pilot uses built assets rather than a separate Vite development server.

## API and configuration

| Endpoint | Behavior |
|---|---|
| `POST /api/generate` | Accepts JSON with a UUID `runId` and `notes` of 1–12,000 characters. Returns `text/event-stream` with AG-UI run and text events. |
| `DELETE /api/generate/{runId}` | Closes the active Bob connection and returns `{"cancelled":true}`; returns `false` for an inactive ID. |

One generation can run at a time. A concurrent request returns HTTP 409. Invalid input returns HTTP 400. Failures after streaming begins use `RUN_ERROR`; success ends with `RUN_FINISHED`.

| Setting | Default | Purpose |
|---|---|---|
| `BOB_BINARY` | `bob` | Bob executable path. |
| `BOB_KEY_FILE` | Unset | JSON API key file path. |
| `BOB_API_KEY` | Unset | API key supplied directly to the server. |
| `BOB_PROMPT_TIMEOUT` | `120s` | Maximum wait for the ACP prompt response. |
| `QUARKUS_HTTP_PORT` | `8096` | Local application port. |

The server binds to `127.0.0.1`. Each request starts a fresh Bob process in an empty temporary workspace, requests ask mode, disables MCP and subagents, and denies ACP permission requests. This is a local presentation pilot, not an OS sandbox or a multiuser service. Generated components have no save or tool actions, and the renderer has no tool provider. Evidence links permit HTTPS URLs without embedded credentials.

## Troubleshooting

- **Bob could not complete this report:** Check the key-file path, key validity, executable path, and connectivity to IBM Bob. Raw provider errors are suppressed to keep credentials and provider details out of browser responses.
- **Another generation is running:** Stop the active request in its browser tab or wait for the configured timeout.
- **Incomplete or unsupported layout:** Inspect the generated source and generate again. This pilot does not automatically repair model output or retry a paid request.
- **The component prompt is out of date:** Run `generate`, then rebuild.

OpenUI Headless 0.18.1 expects an SSE `data: ` prefix with a space. Quarkus emits valid SSE without that optional space. `stream.ts` normalizes the prefix incrementally before passing the response to OpenUI's AG-UI adapter.

See the [Bob ACP documentation](https://bob.ibm.com/docs/shell/features/acp), [ACP registry](https://agentclientprotocol.com/get-started/registry), [OpenUI repository](https://github.com/thesysdev/openui), [Quarkus REST guide](https://quarkus.io/guides/rest), and [Quinoa documentation](https://docs.quarkiverse.io/quarkus-quinoa/dev/).
