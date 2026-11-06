# Local workshop code review

A stateless Quarkus/LangChain4j application using a model installed from the workshop registry. It sends prompts to laptop Ollama; it does not call the Pi for inference or download models automatically.

Companion to [Ollama for Workshops: A Model Registry on a Raspberry Pi](https://www.the-main-thread.com/p/ollama-workshop-registry-raspberry-pi).

## Run

Use Java 25 and local Ollama 0.40.13 or newer. From the parent directory, install the model first:

```bash
bash scripts/setup-participant.sh --registry http://PI_ADDRESS:5002
```

Replace `PI_ADDRESS` with the organizer's registry host or IP address.

Then, from this directory:

```bash
export WORKSHOP_MAVEN_URL=http://PI_ADDRESS:5003/central
export MVNW_REPOURL="$WORKSHOP_MAVEN_URL"
./mvnw -s settings-workshop.xml quarkus:dev
```

In another terminal in the same directory:

```bash
curl -fsS http://127.0.0.1:8080/review \
  -H 'Content-Type: application/json' \
  --data-binary @review-request.json
```

The organizer must prepare the Maven cache first. The wrapper downloads Maven itself through `MVNW_REPOURL`; `settings-workshop.xml` sends dependency and plugin downloads to the Pi. On Windows, use `mvnw.cmd` and the PowerShell environment settings in the parent participant guide.

To build directly from Maven Central outside the workshop, unset `MVNW_REPOURL` and omit `-s settings-workshop.xml`. The supplied settings file leaves your global Maven configuration unchanged.

## Request and response

`POST /review` accepts JSON with `code` and `question` strings, both non-empty. Keep the code below 8001 characters and the question below 1001 characters; short methods fit the configured context best. The HTTP request body limit is 32 KiB. Character limits do not guarantee that arbitrary source fits the model's token context.

A successful response is `{"answer":"..."}`. The supplied request contains an off-by-one array loop. The answer should identify it and propose `i < numbers.length`. Model wording can vary; inspect its advice before using it.

HTTP 400 reports missing, blank, or oversized fields (malformed JSON also receives 400). HTTP 503 reports a local model request failure: check Ollama, the alias, and the application log. HTTP 502 reports an empty model response.

There is no conversation memory, filesystem access, code execution, tool calling, or automatic editing. Two Java types implement the example: `CodeReviewer` is a declarative AI service; `ReviewResource` validates requests and returns JSON.

## Configuration

The default base URL is `http://127.0.0.1:11434`, model ID `workshop-qwen:1.7b`, context 4096 tokens, thinking disabled, temperature zero, and timeout 180 seconds. Quarkus binds to loopback. Ollama Dev Services is disabled so it cannot start a separate server or pull a default model.

To use another local Ollama port:

```bash
QUARKUS_LANGCHAIN4J_OLLAMA_BASE_URL=http://127.0.0.1:11435 ./mvnw -s settings-workshop.xml quarkus:dev
```

Use `quarkus.langchain4j.ollama.chat-model.model-id` in `src/main/resources/application.properties` if changing the alias. In this extension version that setting is fixed at build time; rebuild the packaged application after changing it. Keep the alias and organizer's model list aligned.

## Package before the workshop

```bash
./mvnw -s settings-workshop.xml package
java -jar target/quarkus-app/quarkus-run.jar
```

Use the Maven environment variables above and keep the Pi reachable while downloading dependencies. Afterward, Maven can build with `-o` from the laptop cache if all required artifacts are present. The packaged application runs with the local model after network access is removed. Keep the complete `target/quarkus-app/` directory, not only `quarkus-run.jar`.

The example uses Quarkus platform 3.40.1.1 and its matching LangChain4j BOM.
