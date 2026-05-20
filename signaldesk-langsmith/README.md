# SignalDesk LangSmith tracing demo

Companion app for **Quarkus LangChain4j to LangSmith: OTLP Tracing Without Extra Glue**.

**SignalDesk** is a tiny on-call support assistant with one LangChain4j AI service, one runbook tool, and OpenTelemetry export to LangSmith over OTLP. Local **Ollama** for dev; deterministic **ChatModel** stubs for CI.

## Prerequisites

- JDK **21**
- [Ollama](https://ollama.com/) on `http://localhost:11434` for dev runs
- A tool-capable model pulled, e.g. `ollama pull llama3.2` (override with `OLLAMA_MODEL`)
- [LangSmith](https://smith.langchain.com/) account and API key for trace export

## Environment variables

Quarkus exports via **OpenTelemetry OTLP**, not the LangChain Python SDK. These Python-only variables are **ignored** by this app:

- `LANGSMITH_TRACING`
- `LANGSMITH_OTEL_ENABLED`
- `LANGSMITH_ENDPOINT` (wrong name and usually missing the `/otel` path)

Set these instead (from [LangSmith OpenTelemetry docs](https://docs.langchain.com/langsmith/trace-with-opentelemetry)):

- **`LANGSMITH_API_KEY`** — required (`x-api-key` header)
- **`OTEL_EXPORTER_OTLP_ENDPOINT`** or **`LANGSMITH_OTLP_ENDPOINT`** — base OTLP URL ending in **`/otel`** (not `/otel/v1/traces`; Quarkus appends `v1/traces` for `http/protobuf`)
- **`LANGSMITH_PROJECT`** — must match the project name in the LangSmith UI (header `Langsmith-Project`)
- **`OLLAMA_MODEL`** — optional; defaults to `llama3.2`

**EU region example:**

```bash
export LANGSMITH_API_KEY=lsv2_pt_...
export OTEL_EXPORTER_OTLP_ENDPOINT=https://eu.api.smith.langchain.com/otel
export LANGSMITH_PROJECT=signaldesk-langsmith
```

**US region example:**

```bash
export LANGSMITH_API_KEY=lsv2_pt_...
export OTEL_EXPORTER_OTLP_ENDPOINT=https://api.smith.langchain.com/otel
export LANGSMITH_PROJECT=signaldesk-langsmith
```

Optional single header string (overrides the split properties above):

```bash
export OTEL_EXPORTER_OTLP_HEADERS="x-api-key=${LANGSMITH_API_KEY},Langsmith-Project=${LANGSMITH_PROJECT}"
```

After changing env vars, restart `./mvnw quarkus:dev` (export is read at startup).

## Troubleshooting: nothing in LangSmith

On startup, look for **`OtelExportConfigProbe`** in the log. It prints the resolved OTLP endpoint, project name, and whether `LANGSMITH_API_KEY` is set.

**1. Project name with spaces (very common)**  
`LANGSMITH_PROJECT="Quarkus Test App"` breaks Quarkus comma-separated headers — LangSmith may receive `Langsmith-Project=Quarkus` only. Fix:

```bash
export LANGSMITH_PROJECT=quarkus-test-app
```

Create/rename the project in LangSmith to match, or set the full header string yourself:

```bash
export LANGSMITH_OTLP_HEADERS="x-api-key=${LANGSMITH_API_KEY},Langsmith-Project=Quarkus%20Test%20App"
```

Then comment out the `quarkus.otel.exporter.otlp.traces.headers=...` line in `application.properties` and uncomment the `LANGSMITH_OTLP_HEADERS` line.

**2. EU vs US endpoint**  
EU dashboard accounts must export to `https://eu.api.smith.langchain.com/otel` (no `/v1/traces` suffix), not the US default. If the startup log shows `api.smith.langchain.com` without `eu.`, your `OTEL_EXPORTER_OTLP_ENDPOINT` was not visible to the JVM.

**3. Start dev mode in the same shell as `export`**

```bash
export LANGSMITH_API_KEY='lsv2_pt_...'
export OTEL_EXPORTER_OTLP_ENDPOINT='https://eu.api.smith.langchain.com/otel'
export LANGSMITH_PROJECT=quarkus-test-app
./mvnw quarkus:dev
```

**4. Enable export debug logs** (already on in `%dev`) — after a `curl`, search the console for `401`, `403`, `404`, or `Failed to export`.

**5. Wait for batch export** — spans flush on a short delay; wait 10–30 seconds, then refresh LangSmith.

**6. Open the matching project** in the UI — traces land in the project named in `Langsmith-Project`, not necessarily “default”.

## Commands

```bash
./mvnw test
./mvnw quarkus:dev
```

## Smoke `curl` recipes

Plain chat (no tool):

```bash
curl -s -X POST http://localhost:8080/signaldesk/assist \
  -H 'Content-Type: application/json' \
  -d '{"question":"What is our SLA for SEV-2?"}' | jq
```

Tool path:

```bash
curl -s -X POST http://localhost:8080/signaldesk/assist \
  -H 'Content-Type: application/json' \
  -d '{"question":"SEV-1 database failover — which runbook?"}' | jq
```

Controlled tool failure:

```bash
curl -s -X POST http://localhost:8080/signaldesk/assist \
  -H 'Content-Type: application/json' \
  -d '{"question":"Trigger runbook lookup for UNKNOWN-PLAN"}' | jq
```

Tests enable `SignalDeskStubChatModel` via `QuarkusTestProfile` and disable OTLP — no Ollama or LangSmith required for `./mvnw test`.

Full walkthrough: [article.md](article.md).

Code: https://github.com/myfear/the-main-thread/signaldesk-langsmith
