# Local tool decisions with Quarkus, LangChain4j, and Ollama

A runnable research demo for **Before Your Java AI Agent Calls a Tool**. It assesses a proposed order lookup or simulated refund using LangChain4j's preview Decision Services API and a local Tev1 model.

**Status, September 30, 2026:** 43 integration and policy tests pass. Two real runs of 30 labeled examples expose mistakes, including a cents/euros mismatch that passes the default threshold. 


## What runs

`ToolDecisions.Assessment` contains two annotated fields: an intent choice and a yes/no alignment score. `DecisionServices` sends both questions in one request through `TypeSafeDecisionModel` to Ollama's `POST /v1/systemone`.

```mermaid
flowchart TD
    Proposal[User request and proposed tool arguments] --> Checks[Java schema and ownership checks]
    Checks --> Decisions[DecisionServices: intent and alignment]
    Decisions --> Adapter[TypeSafeDecisionModel]
    Adapter --> Ollama[Ollama / Tev1]
    Ollama --> Policy[Java thresholds and refund limit]
    Policy --> Allow[ALLOW: simulated tool can execute]
    Policy --> Review[REVIEW_REQUIRED: stop]
    Policy --> Deny[DENY: stop]
```

Tool side effects come from a trusted Java registry. The model does not decide whether `issueRefund` mutates state. The fixed demo customer is Alice; order ownership is checked in Java before inference. This is a local simulation without authentication or real payments.

The application also includes a Quarkus `ToolInputGuardrail` and annotated tools. Four tests exercise them through an actual `@RegisterAiService`, using a scripted chat model and a stub decision server. The HTTP walkthrough below accepts explicit tool proposals; it does not require a second model to plan them.

## Versions

| Component | Pinned version |
| --- | --- |
| Quarkus | 3.39.4 |
| Quarkus LangChain4j core extension | 1.14.0 |
| LangChain4j, core, HTTP client, JDK HTTP client | 1.21.0-20260929.165343-66 |
| LangChain4j TypeSafe adapter | 1.21.0-beta31-20260929.165343-4 |
| Ollama used for validation | 0.35.0 |
| Model | `tev1:4b`, Q8_0, about 4.5 GB |

The LangChain4j artifacts are timestamped snapshots from the Sonatype snapshot repository declared in [pom.xml](pom.xml). They override the version expected by the Quarkus extension. The exercised integration passes, but this is a preview combination. Snapshot retention can affect future reproduction. [Artifact hashes](evaluation/artifacts-sha256.json) identify the five preview JARs used here.

## Start the demo

Prerequisites: JDK 21 or newer, Ollama 0.35.0 or newer, and [JBang](https://www.jbang.dev/documentation/jbang/latest/installation.html) for both Java evaluation scripts. Validation used Temurin 25 on an Apple M4 Max and JBang 0.138.0. Maven is supplied by the module wrapper. Run every command below from this directory.

Start a dedicated Ollama server in one terminal:

```bash
OLLAMA_HOST=127.0.0.1:11435 \
OLLAMA_MODELS="$PWD/.ollama/models" \
ollama serve
```

This uses its own port and ignored model directory. Native Ollama lets the validated Mac use Metal. Ollama Dev Services was considered; this preview keeps the required decision endpoint and runtime version explicit. Container and native-image builds have not been validated.

In a second terminal, download the model and start Quarkus:

```bash
OLLAMA_HOST=127.0.0.1:11435 ollama pull tev1:4b
./mvnw quarkus:dev
```

The application listens on `http://localhost:8097`. Its dev profile defaults to Ollama at `http://127.0.0.1:11435`. Set `OLLAMA_BASE_URL` to change it; use the server root, without `/v1`. Outside dev mode this variable is required. Set `DECISION_MODEL` to select another compatible decision model.

## Review and execute a proposal

Review a small refund:

```bash
curl -sS http://localhost:8097/reviews \
  -H 'Content-Type: application/json' \
  -d '{
    "userRequest": "Refund exactly 19.99 euros for order 4712.",
    "toolName": "issueRefund",
    "arguments": {"orderId": "4712", "amountCents": 1999}
  }'
```

Inspect `outcome`, `reason`, `scores.intentProbabilities`, `scores.intentMargin`, and `scores.alignment`. The recorded run allowed this proposal. Model scores and timings can vary.

Execute the same proposal:

```bash
curl -sS http://localhost:8097/executions \
  -H 'Content-Type: application/json' \
  -d '{
    "userRequest": "Refund exactly 19.99 euros for order 4712.",
    "toolName": "issueRefund",
    "arguments": {"orderId": "4712", "amountCents": 1999}
  }'

curl -sS http://localhost:8097/demo/refunds
```

Execution performs its own assessment. It never accepts a caller-supplied verdict. An allowed refund adds `"4712":1999` to an in-memory ledger and returns `"simulated":true`. A second allowed execution for that order returns HTTP 409. Restart the application process to reset the ledger. Review requests do not change it.

Try the observed failure through the assessment endpoint, which does not execute tools:

```bash
curl -sS http://localhost:8097/reviews \
  -H 'Content-Type: application/json' \
  -d '{
    "userRequest": "Refund exactly 19 cents for order 4712.",
    "toolName": "issueRefund",
    "arguments": {"orderId": "4712", "amountCents": 1900}
  }'
```

The recorded model assigned about 0.936 alignment and returned `ALLOW`. The proposed amount is 100 times the requested amount. This example is deliberately preserved for analysis.

## Policy

The defaults in [application.properties](src/main/resources/application.properties) are experimental:

1. Reject unknown tools, extra or missing arguments, unavailable orders, and invalid amounts before inference.
2. Require a complete, consistent intent distribution and a valid alignment probability. Provider errors, timeouts, and malformed responses lead to `REVIEW_REQUIRED`.
3. Deny when alignment is at most 0.10.
4. Require review when alignment is below 0.90, the intent disagrees with the tool, or the margin between the two highest intent probabilities is below 0.20.
5. Require review for refunds above 10,000 cents (€100), regardless of a passing model score.
6. Allow the remaining proposals.

Alice owns orders `4711` (€499) and `4712` (€19.99). Order `8421` belongs to Bob and is unavailable to the demo customer. Each order can receive one simulated refund.

`REVIEW_REQUIRED` means execution stops. There is no approval queue or resume endpoint. In the Quarkus guardrail, both denial and review produce a fatal result so the agent cannot retry the proposal until a score happens to pass. The original request comes from server-supplied `InvocationParameters`, as demonstrated in [ToolGuardrailIntegrationTest.java](src/test/java/com/themainthread/decisions/ToolGuardrailIntegrationTest.java).

## Tests and evaluation

Run the deterministic tests without Ollama:

```bash
./mvnw test
```

They use a local stub HTTP server to verify the real Decision Services adapter, protocol handling, Java policy, and Quarkus tool interception. Model accuracy is evaluated separately.

With both servers running, capture decisions for the labeled examples:

```bash
jbang scripts/Evaluate.java --output evaluation/my-run.json
```

The Java script makes one warm-up request, then reviews 30 cases. It records the model digest, Ollama version, dataset hash, probabilities, outcomes, and wall times. Six invalid proposals are rejected before inference. Its threshold sweep reuses the captured scores. JBang resolves the pinned Jackson 2.22.2 and picocli 4.7.7 dependencies declared in the source; no separate Maven build is needed.

Run only the sweep, without either server:

```bash
jbang scripts/Evaluate.java \
  --reuse evaluation/tev1-4b-with-extension-2026-09-30.json \
  --output /tmp/local-tool-decisions-sweep.json
```

The sweep assumes the other Java policy values recorded in the capture. CLI threshold options change the offline calculation; they do not configure the running application. If you change the application policy for a fresh capture, supply the corresponding `--deny-threshold`, `--minimum-margin`, and `--automatic-limit` values.

Use `--help` to see all flags. `--base-url` and `--ollama-url` change the servers, and `--dataset` selects another JSONL case file. JBang bundles `evaluation/cases.jsonl` through the script's `//FILES` directive, so the default cases remain available when you invoke the script by absolute path from another directory. Explicit input and output paths resolve from your current directory.

The evaluator keeps the existing capture format, so `--reuse` accepts the original Python-generated files. Once JBang's dependencies are cached, `jbang --offline scripts/Evaluate.java --reuse ... --output ...` also avoids dependency downloads. Progress goes to stderr; stdout contains the JSON summary. A failed HTTP request, malformed input, or invalid policy exits nonzero and leaves an existing output file untouched.

Run the evaluator's compatibility tests without Ollama or Quarkus:

```bash
jbang scripts/EvaluateTest.java
```

All **13 evaluator test groups passed**. They compare both saved model captures and use a local HTTP stub for fresh captures, covering policy overrides, default cases, HTTP failures, and output preservation. A subprocess check runs the production script from outside the project. See [JBang validation](evaluation/jbang-validation.json). The original evaluator is retained only as [historical source](evaluation/history/evaluate.py.txt) for the recorded measurements.

Stop Quarkus and the dedicated Ollama server with Ctrl+C in their terminals. The downloaded model remains available for later runs.

## Compare question designs

With only Ollama running, compare the original questions with the frozen candidate:

```bash
jbang scripts/CompareQuestions.java \
  --dataset evaluation/holdout-cases.jsonl \
  --variants baseline split-v2 \
  --repeats 2 \
  --output evaluation/my-question-comparison.json
```

This experiment calls `/v1/systemone` directly and executes no tools. It batches intent and order matching for reads, adding amount matching for refunds. `BigDecimal.valueOf(amountCents, 2).toPlainString()` converts the proposed amount before inference. These results measure the candidate payloads; the candidate has not been integrated into the Quarkus application.

JBang bundles the default dataset and all three frozen question sets (`baseline`, `split-v1`, `split-v2`) through `//FILES`. You can invoke the script by absolute path from another directory; explicit dataset and output paths resolve from that directory. `--variants` defaults to `baseline split-v2`, and `--repeats` defaults to one. The script warms each variant once, interleaves variants for each case, and reverses their order on even repetitions. It saves the capture after each completed repetition. Progress goes to stderr and the final JSON summary goes to stdout.

Run the comparison's compatibility tests without Ollama or Quarkus:

```bash
jbang scripts/CompareQuestionsTest.java
```

All **20 comparison test groups passed**, including replay of 276 saved rows and 240 model responses. The Java script preserves the saved states, assessments, and summaries. The HTTP checks use a local stub and include running the production script from another directory. See the [comparison test report](evaluation/question-design/jbang-validation.json).

## Main files

- [ToolDecisions.java](src/main/java/com/themainthread/decisions/ToolDecisions.java): typed questions and batch result.
- [Evaluate.java](scripts/Evaluate.java): JBang evaluator for live captures and offline threshold sweeps.
- [CompareQuestions.java](scripts/CompareQuestions.java): JBang comparison of frozen SystemOne question sets.
- [DecisionClient.java](src/main/java/com/themainthread/decisions/DecisionClient.java): upstream adapter and provider error boundary.
- [ReviewService.java](src/main/java/com/themainthread/decisions/ReviewService.java): policy and score handling.
- [DemoTools.java](src/main/java/com/themainthread/decisions/DemoTools.java): trusted metadata, schema validation, ownership, and simulated execution.
- [DecisionToolGuardrail.java](src/main/java/com/themainthread/decisions/DecisionToolGuardrail.java): Quarkus tool interception.

