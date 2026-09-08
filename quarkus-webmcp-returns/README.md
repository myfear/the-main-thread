# Quarkus WebMCP return workbench

Companion to [the tutorial](article.md). A Qute page exposes three native WebMCP tools; REST and backend MCP share one Java eligibility service. The demo only prepares an estimate. It has no return-submission or refund endpoint.

## Run

Requirements: JDK 25, Quarkus CLI, Chrome 152 (tested: 152.0.7977.77), Node.js (tested: 26.7.0), and optionally Bob Shell 2.0.2 with an account or inference API key. The bounded Bob runner also requires Python 3. Java platform: Quarkus 3.39.1.

From this directory:

```bash
quarkus dev
```

Open [the workbench](http://127.0.0.1:8097/). The normal JavaScript form works without WebMCP. To enable native tools in your browser, enable `chrome://flags/#enable-webmcp-testing` and relaunch. The Chrome MCP configuration enables the feature in its own isolated browser.

Select one keyboard and one cable with reason `damaged`: expect a **€108.00** draft. Clear the draft or reload to reset it. Stop dev mode with Ctrl+C. No database or containers are required.

## Test

```bash
npm ci
npm run test:browser
npm run test:mcp
```

Keep dev mode running for these Node tests. They use the installed Google Chrome, not a downloaded Chromium. The eight browser tests include native API calls, rejected input, cancellation, races, reload, fallback, and mobile layout. The MCP smoke test exercises both the real Chrome bridge and the backend `/mcp` endpoint.

Run the six Java contract tests with `./mvnw test`, or use Quarkus continuous testing. During development they were executed through Quarkus Agent MCP's `devui-testing_runTests` tool. Read [the verification record](VERIFICATION.md) for exact evidence and limitations.

## Bob

For an interactive session in this demo directory:

```bash
mkdir -p .bob
cp bob-mcp.example.json .bob/mcp.json
bob chat
```

If `.bob/mcp.json` already exists, merge the `chrome-returns` entry instead of replacing your configuration. The example leaves approval interactive. Ask Bob to open the workbench, discover its WebMCP tools, and prepare the damaged keyboard/cable return. A separate Chrome window opens. The browser is restricted to this local demo origin.

For the bounded experiment used in the article:

```bash
python3 scripts/run-bob.py --key-file /path/outside/repository/inference-key.json
python3 scripts/verify-bob.py
```

Alternatively set `BOB_API_KEY` before running `python3 scripts/run-bob.py`. Add `--headed` to show Chrome while Bob is working. The script uses a temporary Bob workspace, the locally installed pinned bridge, a 15-turn limit, Bob's `--max-cost 2`, and a 240-second timeout. It preapproves only the listed browser tools for this experiment. It disables Bob's read, edit, command, browser, and subagent groups; MCP remains available. Existing global Bob settings are not modified. The test assumes no unrelated globally configured MCP servers, as on the tested machine.

The downloaded key is read into the child process environment and never copied into the workspace. Raw redacted traces are gitignored. The verifier produces the small shareable `evidence/bob-verification.json` report. Its assertions intentionally target the default keyboard/cable prompt.

## Layout

- `src/main/java/com/themainthread/returns/`: eligibility service, REST resource, Qute page, backend MCP tool, JSON number configuration.
- `src/main/resources/templates/OrderPage/order.html`: human interface.
- `src/main/resources/META-INF/resources/app.js`: shared page operations and native WebMCP registration.
- `scripts/`: browser tests, MCP contract test, and bounded Bob experiment.
- `evidence/`: screenshots and verified results.

The application binds to loopback, serves synthetic data, and has no authentication. A production application must authorize each order operation server-side. Tool descriptions, browser schemas, and disabled form controls do not enforce that boundary.
