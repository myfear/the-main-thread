# WindowWatch

WindowWatch is the Quarkus demo app behind [article.md](article.md). It makes LangChain4j retained memory visible with a browser tank gauge, a per-turn ledger keyed by `memoryId`, and OpenTelemetry attributes that report the same pressure in traces.

Two numbers matter, and they do different jobs:

- `windowwatch.budget.max-tokens=1200` is the retained-memory budget LangChain4j enforces for eviction
- `windowwatch.budget.model-context-tokens=262144` is the model context limit we show as reference for the last Ollama call

## What It Includes

- `POST /api/chat/{memoryId}` to send one prompt and return the answer plus budget snapshot
- `GET /api/budget/{memoryId}` to read the current in-process budget for a memory lane
- `GET /` static UI with the tank gauge, turn ledger, and **AUTO-SEND** stress loop
- `@MemoryId` chat memory backed by `TokenWindowChatMemory`
- `HuggingFaceTokenCountEstimator` for realistic token counting
- request-scoped capture of Ollama `TokenUsage` for last-call diagnostics
- deterministic stub-backed `@QuarkusTest` coverage for the HTTP contract
- a plain unit test for eviction behavior in `ConversationBudgetRegistry`

## Requirements

- JDK 25
- Maven 3.9+ or the included Maven wrapper
- [Ollama](https://ollama.com/download) running locally on `http://localhost:11434`
- one pulled chat model such as `qwen3:4b`
- a matching tokenizer file downloaded with `scripts/download-tokenizer.sh`

Pull the model:

```bash
ollama pull qwen3:4b
```

Download the tokenizer once:

```bash
chmod +x scripts/download-tokenizer.sh
./scripts/download-tokenizer.sh
```

The first dev run may take a minute while ONNX or DJL native libraries download.

## Configuration

Main runtime settings live in [src/main/resources/application.properties](src/main/resources/application.properties):

```properties
quarkus.langchain4j.ollama.base-url=http://localhost:11434
quarkus.langchain4j.ollama.chat-model.model-id=${OLLAMA_MODEL:qwen3:4b}
quarkus.langchain4j.ollama.devservices.enabled=false
quarkus.langchain4j.devservices.enabled=false
windowwatch.budget.max-tokens=1200
windowwatch.budget.model-context-tokens=262144
windowwatch.tokenizer.path=tokenizers/qwen3-tokenizer.json
```

## Run Locally

```bash
./mvnw quarkus:dev
```

Open [http://localhost:8080/](http://localhost:8080/).

Send one turn from the shell:

```bash
curl -s -X POST http://localhost:8080/api/chat/demo-1 \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"Remember customer Orbital Freight and a 14 minute outage."}'
```

Read the current budget:

```bash
curl -s http://localhost:8080/api/budget/demo-1
```

In dev mode, OpenTelemetry traces export to the console when `opentelemetry-exporter-logging` is on the classpath and `%dev.quarkus.otel.traces.exporter=logging` is set. After a chat request, search the log for `windowwatch.budget.used_tokens` or `windowwatch.request.input_tokens`.

## Tests

```bash
./mvnw test
```

Tests use a stub AI service and a fixed token estimator, so no live Ollama instance or tokenizer file is required.



