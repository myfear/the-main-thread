# Shapeshift with a local decision model

Type a thought into one input and turn it into an event, reminder, checklist, timer, bill split, or note. Quarkus Web Bundler bundles and serves the JavaScript and CSS. LangChain4j Decision Services asks local Ollama which card fits and whether the input is urgent. Java parses the card fields; the browser lets you edit and save them.

This prototype recreates the interaction from [Anish’s Shapeshift](https://github.com/anishfn/shapeshift). Its frontend uses native JavaScript and CSS, with tooling managed by Web Bundler. You do not need to install Next.js, Node.js, or Bun separately, or configure a hosted API or API key. The upstream UI components are not copied.

## Start the application

You need JDK 21 or newer and Ollama 0.35.0 or newer. Run all commands from this directory. Maven is supplied by the wrapper. The first build downloads Java dependencies and Web Bundler’s native tooling. The model download uses about 4.5 GB.

Start a dedicated Ollama server in one terminal:

```bash
mkdir -p .ollama/models
OLLAMA_HOST=127.0.0.1:11436 \
OLLAMA_MODELS="$PWD/.ollama/models" \
OLLAMA_NUM_PARALLEL=1 \
OLLAMA_NO_CLOUD=true \
ollama serve
```

Download the model and start Quarkus in a second terminal:

```bash
OLLAMA_HOST=127.0.0.1:11436 ollama pull tev1:4b
./mvnw quarkus:dev
```

Open [the local prototype](http://127.0.0.1:8098). Click an example or type `Dinner with Priya tomorrow at 8pm`. The status shows the model response and request time. You can also choose any card directly, including when Ollama is unavailable.

Press **Enter** to save a card, **Shift+Enter** for a line break, and **Esc** to clear the input. Saved cards use this browser’s `localStorage`; **Edit** replaces the saved card and **Delete** removes it. A reminder is a saved card, and an event is a saved plan. Neither creates notifications nor writes to a calendar. Timers run while the page remains open.

Stop both processes with **Ctrl+C** in their terminals. The ignored `.ollama/models` directory retains the model for the next run. To use an existing Ollama server instead, set its server root when starting Quarkus, for example `OLLAMA_BASE_URL=http://127.0.0.1:11434 ./mvnw quarkus:dev`.

## Card parsing

- **Event and reminder:** recognizes `today`, `tomorrow`, full weekday names, and `YYYY-MM-DD`; times such as `8pm`, `8:30am`, and `at 20:00`. A weekday means its next occurrence. Missing or invalid fields remain visible for editing. Parsing uses `shape.zone`, which defaults to `Europe/Berlin`.
- **Checklist:** splits commas, semicolons, newlines, and `and`; strips common list prefixes. Edit any item, add an item, and check completed items.
- **Timer:** accepts whole-number hours, minutes, and seconds, including combined units, from 1 second to 24 hours. Decimal and negative durations need manual correction.
- **Split:** accepts `split`, `divide`, or `share`, a decimal-point amount, and `between`, `among`, `by`, or `/` followed by a numeric people count. Splits integer cents and reports how many people pay one extra cent. It accepts at most 1,000 people and 1,000,000 currency units. Currency conversion and locale-specific amounts are outside this parser.
- **Note:** preserves text as editable text. User input is rendered through text nodes and input values.

The model does no date arithmetic, amount calculation, or field extraction. Classifier accuracy and parser coverage are separate properties.

## Classifier and typing behavior

The released dependencies are Quarkus **3.40.1**, Web Bundler **3.0.0**, LangChain4j **1.22.0**, and its TypeSafe adapter **1.22.0-beta32**. Decision APIs are experimental. This prototype wires upstream `DecisionServices` with CDI; it does not use the Quarkus LangChain4j chat-model extension.

`TypeSafeDecisionModel` sends two questions to Ollama’s `POST /v1/systemone`: a seven-way choice, including `NONE` for incomplete input, and an urgency yes/no question. This keeps the question set small. Ollama returns model probabilities rather than generated JSON containing self-reported scores.

Before inference, the browser and server apply the same syntax rules from `InputReadiness`. Bare commands, dangling connectors, unfinished list separators or times, and split commands without numeric operands wait for more detail. They make no Ollama call and carry no model probabilities. The browser immediately removes an old automatic card when you edit its input. Explicitly choosing a card bypasses these automatic readiness checks so you can fill in its details yourself.

The browser uses a minimum 250 ms pause after an edit. For continuous single-character edits, it keeps the latest six intervals, takes their median, and waits 1.5 times that interval plus 50 ms, capped at 1,000 ms unless you choose a higher minimum. A paste or a pause of at least 1,500 ms resets the cadence estimate. This avoids classifying between slower keystrokes. Input method composition also waits until composition finishes.

It sends one classification at a time and coalesces intervening edits into the latest text. A revision check discards old responses. The server allows one inference at a time across clients and returns HTTP 429 to overlapping calls. There is no unbounded application inference queue or automatic retry loop. A failed request shows an error; manual card selection remains available.

The UI accepts a card only when the server reports `ready: true`, its probability is at least 0.55, and its margin over the next option is at least 0.15. Timers need a valid duration, splits need a valid people count, and checklists need at least two parsed items. Events and reminders can still have missing date or time fields for you to edit. These are prototype policies, not a calibration result. Open **Classification & typing measurements** to inspect the probabilities and change the minimum typing pause. `?cache=0` disables the server prediction cache for browser measurements. The cache holds at most 128 successful predictions and always reparses dates against the current clock.

The readiness rules recognize obvious English syntax, not every unfinished word or idea. Some coherent but unusual notes can also trigger a rule, such as a note ending in a connector or colon. You can choose a card manually when the automatic policy waits.

## Verify the implementation

The application tests use a stub Ollama server through the real LangChain4j adapter and do not need a downloaded model:

```bash
./mvnw test
```

The tests cover protocol mapping, malformed answers, empty and oversized input, cached results, manual selection, overlapping inference, calendar parsing, duration bounds, and cent arithmetic. The Maven source phase formats Java and sorts imports.


## HTTP endpoints

`POST /api/intent` accepts `{ "text": "25 min focus", "useCache": false }` and returns the prediction, parsed card, model time, server time, `ready`, and `waitingReason`. Input is limited to 500 characters. `useCache` defaults to true. Empty input bypasses the model and reports `source: "empty"`. Other syntax fragments report `source: "input-gate"`, `ready: false`, a null card, and empty probabilities. An Ollama result can also have `ready: false` when its selected card lacks usable parsed data.

`POST /api/parse` accepts `{ "text": "split 10 between 3", "intent": "SPLIT" }` to parse a manually selected card without model inference. The six accepted enum values are `EVENT`, `REMINDER`, `CHECKLIST`, `TIMER`, `SPLIT`, and `NOTE`.

`GET /api/config` returns the model, parsing zone, pause policy, shared readiness rules, and acceptance thresholds. `shape.timeout` defaults to 10 seconds; the LangChain4j adapter makes one attempt with no retries. The application binds to loopback for this local prototype.
