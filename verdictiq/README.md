# VerdictIQ

VerdictIQ is the Quarkus demo app behind the article in [article.md](/Users/meisele/Projects/the-main-thread-preview/verdictiq/article.md). It sends the same text to two local Ollama-backed models, stores a `PENDING` verdict immediately, and uses Quarkus Signals to wake a judge model only when the panel disagrees.

## What It Includes

- `POST /verdict` to submit text for classification
- `GET /verdict/{id}` to poll the workflow state
- Two panel AI services: `granite4:3b` and `mistral`
- One judge AI service: `qwen3:4b`
- Structured AI service responses mapped directly to `ModelVerdict`
- An in-memory verdict store
- Deterministic stub-backed `@QuarkusTest` coverage for the REST and workflow contract
- An opt-in integration test that runs ambiguous sentences through local Ollama models

## Requirements

- JDK 25
- Maven 3.9+ or the included Maven wrapper
- Ollama installed locally
- These models pulled locally:

```bash
ollama pull granite4:3b
ollama pull mistral
ollama pull qwen3:4b
```

## Configuration

Main runtime settings live in [src/main/resources/application.properties](/Users/meisele/Projects/the-main-thread-preview/verdictiq/src/main/resources/application.properties):

```properties
quarkus.langchain4j.ollama.base-url=http://localhost:11434
quarkus.langchain4j.ollama.timeout=240s
quarkus.langchain4j.ollama.devservices.enabled=false
quarkus.langchain4j.devservices.enabled=false
quarkus.langchain4j.granite.chat-model.provider=ollama
quarkus.langchain4j.ollama.granite.chat-model.model-id=granite4:3b
quarkus.langchain4j.mistral.chat-model.provider=ollama
quarkus.langchain4j.ollama.mistral.chat-model.model-id=mistral
quarkus.langchain4j.judge.chat-model.provider=ollama
quarkus.langchain4j.ollama.judge.chat-model.model-id=qwen3:4b
quarkus.langchain4j.ollama.granite.timeout=120s
quarkus.langchain4j.ollama.mistral.timeout=120s
quarkus.langchain4j.ollama.judge.timeout=120s
```

The sample expects a locally running Ollama instance on `localhost:11434`, so Dev Services are disabled in both the main app and the deterministic test profile.

## Run It

Start dev mode:

```bash
./mvnw quarkus:dev
```

Submit text:

```bash
curl -s -X POST http://localhost:8080/verdict \
  -H 'Content-Type: application/json' \
  -d '{"text":"I loved the feature until it deleted my data twice."}'
```

Poll the verdict:

```bash
curl -s http://localhost:8080/verdict/<id>
```

## Test It

Run the deterministic contract suite:

```bash
./mvnw test
```

Run the Ollama integration test:

```bash
./mvnw test -Dverdictiq.live=true
```

The deterministic suite uses these support classes:

- [StubAiProfile.java](/Users/meisele/Projects/the-main-thread-preview/verdictiq/src/test/java/dev/verdictiq/testsupport/StubAiProfile.java)
- [StubGranitePanelist.java](/Users/meisele/Projects/the-main-thread-preview/verdictiq/src/test/java/dev/verdictiq/testsupport/StubGranitePanelist.java)
- [StubMistralPanelist.java](/Users/meisele/Projects/the-main-thread-preview/verdictiq/src/test/java/dev/verdictiq/testsupport/StubMistralPanelist.java)
- [StubJudgeAiService.java](/Users/meisele/Projects/the-main-thread-preview/verdictiq/src/test/java/dev/verdictiq/testsupport/StubJudgeAiService.java)

## Related Guides

- [Quarkus Signals guide](https://quarkus.io/guides/signals)
- [Quarkus LangChain4j AI Services reference](https://docs.quarkiverse.io/quarkus-langchain4j/dev/ai-services.html)
- [Quarkus LangChain4j Ollama guide](https://docs.quarkiverse.io/quarkus-langchain4j/dev/guide-ollama.html)
- [Quarkus REST Jackson guide](https://quarkus.io/guides/rest#json-serialisation)
