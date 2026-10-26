# Quarkus Visual Agent Studio

Edit a typed graph in React Flow, validate it, publish an immutable Open Workflow definition, and inspect a fixture review's actual Quarkus Flow execution events.

The companion includes a Main Thread styled editor, a Java validator/compiler, immutable publications, PostgreSQL lifecycle history, and a deterministic fixture review adapter. Reviews perform no inference. Live models, SSE, saved draft revisions, and durable human approval remain future work.

Read the walkthrough: [Quarkus Flow: Publish and Run a Visual Workflow](https://www.the-main-thread.com/p/quarkus-visual-workflow-editor).

## Prerequisites

- Java 25
- Node.js 22.12 or newer and npm for the editor (validated with Node.js 26.12.1)
- Podman with a running machine and the API socket available to Testcontainers
- Bash, curl, jq, and uuidgen for the smoke script

The application uses Quarkus 3.40.1.1, Quarkus Flow 1.3.0, and PostgreSQL 17.7. Maven is supplied by the wrapper in `backend/`. Dev Services starts PostgreSQL using Podman. No model server or GitHub account is required.

## Start the backend

From this companion's directory:

```bash
cd backend
./mvnw quarkus:dev
```

The HTTP server binds to `127.0.0.1:8126`. In another terminal, from the companion's directory:

```bash
bash scripts/graph-smoke.sh
```

This script loads the supplied graph through the API, validates and compiles it twice, publishes it, runs both PR fixtures, and verifies actual node events. It then publishes a changed policy and proves that the original version retains its behavior. It also checks that an arbitrary URL in node configuration is rejected before publication. Successful output ends with `Graph contract passed for 0.0.<number> and 0.0.<next-number>.`

For the original engine checkpoint and HTTP-failure checks:

```bash
bash scripts/smoke.sh
```

The script publishes a new version, waits for Runner discovery, runs both branches, checks live engine checkpoint rows, verifies an executor error fails the task and run, and checks that the executor rejects a request without credentials. Each failed check exits nonzero. Successful output ends with `Runtime spike passed for 0.0.<number>.`

The local development Studio/Runner token is `local-spike-runner`; the separate executor token is `local-spike-executor`. These are intentionally public demo values. The generated workflow contains neither value. Outside dev and test profiles, set `STUDIO_RUNNER_TOKEN` and `STUDIO_INTERNAL_TOKEN` explicitly. Keep this backend bound to loopback.

## Start the editor

Keep the backend running. In another terminal, from the companion directory:

```bash
cd frontend
npm ci
npm run dev
```

Open [Agent Studio](http://127.0.0.1:5178/). Vite binds to loopback and proxies `/api` to the backend on port 8126. To use a different backend address, set `STUDIO_BACKEND_URL` when starting Vite. The dev editor fills the public demo token automatically. You can change it under **API connection**; the token stays in memory and is never included in a graph download.

1. Select `policy` in **Selected node**. Leave **Minimum severity** at `HIGH`.
2. Select **Validate**, then **Publish version**. Wait for `READY`, which means the workflow is registered in the runtime.
3. Run the documentation fixture. Expect **Low-risk result** and a completed `lowOutput` task.
4. Run the security-change fixture. Expect **Human review required** and a completed `highOutput` task. This is a terminal routing decision; it creates no approval task or GitHub change.
5. Switch to **Draft graph**, change the policy threshold to `LOW`, and publish the next version. Running the documentation fixture now requires review.

The run button names the published version it will execute. Unpublished edits do not change that version. **Execution graph** shows a read-only snapshot of the graph used for the latest run, even after further draft edits. Node status comes only from recorded task events. **No recorded task** means that the journal contains no event for that node; the editor does not infer a successful or skipped task.

Use the connection checkboxes to show control, data, and capability edges. Drag nodes to arrange the graph, connect matching handles, or use **Connections** in the inspector to choose named ports with the keyboard. **Add a node** uses the backend catalog. The server checks complete topology and required bindings when you validate or publish. **Compiled workflow and publication details** exposes a draft YAML preview and the published artifact hash.

Drafts and the current run selection live in this browser tab. **Download graph** preserves the canonical draft JSON; **Import graph** restores it. A new tab loads a fresh example with a new flow ID. Publications and run events remain in PostgreSQL, but this editor does not yet include a publication browser or run-history picker. Keep the returned run ID to retrieve a completed run through the API.

Events are polled every 500 ms until a terminal event, an error, or 30 seconds. **Refresh events** resumes reads after a polling error or timeout; it never starts a replacement run. Ambiguous start responses block another run in this tab. The journal loss counter covers the backend process, not just the selected run. A fast fixture can finish between polls, so the UI may show completed nodes without displaying their intermediate started states.

To check the frontend and preview the built assets:

```bash
npm test
npm run build
# Stop the dev frontend before using the same port for preview.
npm run preview
```

The built preview requires entering the Studio token under **API connection**. It uses the same local API proxy. These commands do not package the frontend inside the Quarkus application.

## Inspect a graph run

Use a `runId` printed by the graph smoke script:

```bash
curl --fail-with-body -sS \
  -H 'Authorization: Bearer local-spike-runner' \
  http://127.0.0.1:8126/api/graph-runs/RUN_ID | jq
```

The result includes the pinned publication/version, engine instance ID, actual lifecycle events mapped to graph node IDs, terminal status, and structured output. Model mode is explicitly `fixture`. Model, prompt, and toolset nodes have no execution events because they are configuration capabilities.

Events are emitted by Quarkus Flow and saved by Studio's listener in its own PostgreSQL journal. Reads use the reserved run ID even if an uncertain start left the engine instance ID null. Persisted terminal events can supply the final status; partial history leaves `START_UNCERTAIN` in place. This read path does not reconcile or retry the execution.

The [example graph](backend/src/main/resources/studio/examples/pr-review-basic.graph.json) contains control, data, and capability edges. Review findings come from a bounded Java fixture adapter. Each capability card pins one fixed registration; the prompt is stored but not interpreted by this adapter. With the toolset attached, the fixture reads the supplied files through the counted callback once; without it, the fixture reads the same files from its request. Finding line numbers are fixed to the bundled diffs. The sample selector and policy threshold are the controls that change the demonstrated decisions.

Java policy applies that severity threshold and independently routes files in a `security/` directory, at the repository root or nested in the path, to review. Newly compiled workflows take the low-risk branch only for explicit boolean false; missing or malformed policy values default to review in the switch and both outputs. A high-risk run finishes with `REQUIRES_HUMAN_REVIEW`; it does not wait for approval. Existing publications retain their compiled behavior; publish a new version to use compiler `graph-compiler-v2`.

The main APIs are `GET /api/node-types`, `POST /api/graphs/validate`, `POST /api/graphs/compile`, `POST /api/graphs/publish`, `GET /api/publications/{id}`, `GET /api/publications/{id}/compiled`, `POST /api/publications/{id}/runs`, and `GET /api/graph-runs/{id}`. Compilation/publication accept `{version, graph}`; run input is `{fixture: "low-risk"}` or `{fixture: "high-risk"}`.

## Inspect a spike run

Use an `instanceId` printed by the smoke script:

```bash
curl --fail-with-body -sS \
  -H 'Authorization: Bearer local-spike-runner' \
  http://127.0.0.1:8126/api/spike/instances/INSTANCE_ID | jq
```

The response contains persisted lifecycle events, the current number of engine task-checkpoint rows, and the event journal's loss counter. The workflow has a two-second workflow wait after its HTTP policy task so live checkpoints can be observed. This wait is a diagnostic window, not a human approval.

Flow removes its instance and checkpoint rows when a workflow completes, fails, or is cancelled. A completed run can therefore have zero engine checkpoint rows while its Studio event history remains available. Engine checkpoints and historical execution events serve different purposes.

## Run the automated checks

From `backend/`:

```bash
./mvnw verify
```

`RuntimeSpikeTest` uses a temporary workflow directory and an available test HTTP port. It tests runtime discovery after startup, both branch paths, persisted instance/task state while a workflow is active, terminal checkpoint cleanup, retained Studio events, failure propagation, authentication, and immutable versions. PostgreSQL is required; there is no in-memory database substitute.

`GraphContractTest` adds catalog/schema checks, malformed-graph diagnostics, deterministic compilation, capability resolution, graph publication, both fixture routes, node-event correlation, version isolation, and executor authorization. It rejects tool-budget violations, unavailable tools, and malformed model output, and checks that sensitive-file policy requires review when model findings are empty.

The graph regressions also recreate an uncertain start with a missing instance mapping, check partial and completed journal reads by run ID, and execute the generated switch and both outputs against missing, malformed, and boolean policy values. Path checks cover root and nested `security/` directories and similarly named non-matches.

Dev Services data has a development lifecycle. Do not treat a Dev Services database as permanent storage. The event writer uses a bounded in-memory queue; abrupt process termination can lose events waiting in that queue. A loss counter detects rejected or failed writes during the current process. Crash-safe event delivery, replay over SSE, and restart recovery are later work.

The tested spike orders its HTTP call before the diagnostic wait. In JVM continuous tests with this release combination, placing an HTTP call after that wait exposed a `FlowHttpConfig` synthetic-injection error. A packaged-process reproduction has not been performed. That ordering is outside the verified path; durable continuation into later HTTP tasks needs separate investigation.

Stop dev mode with Ctrl+C. Published files remain under `backend/workflows/published/` and are ignored by Git. Keep graph publication files with their database records; removing a file can make a stored publication unavailable after restart. Resetting graph data requires treating the database and artifact directory together. Choose a new version to publish a changed definition.
