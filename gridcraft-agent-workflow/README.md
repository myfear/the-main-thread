# Editable Excel reports with GridCraft and IBM Bob

Create a small returns report with baseline and scenario columns, formulas, a column chart, and source provenance. Java reads a synthetic workbook and checks the saved XLSX independently with Apache POI. You can create the report through the GridCraft CLI or let IBM Bob build it through MCP.

The source deliberately stores a total of 60 even though its current inputs, 10, 20, and 50, add up to 80. The report keeps that distinction and changes the third return to 70 in a separate scenario column.

## Prerequisites

Use this directory within a checkout of the repository. The fixture generator lives in the adjacent `excel-context-xray` directory.

- macOS for the supplied installer and configuration scripts.
- Java 25 and JBang. The verifier pins Apache POI 5.5.1 and its other dependencies.
- `curl`, `unzip`, and `jq`.
- Bob Shell with authentication configured, if you use the agent workflow.
- GridCraft desktop, if you want to inspect and edit the result visually.

The commands target GridCraft CLI and desktop 0.5.0, Bob Shell 2.0.6, and JBang 0.138.0. The [GridCraft 0.5.0 release](https://github.com/storytold/gridcraft/releases/tag/v0.5.0) also provides binaries for other platforms; substitute their executable paths when adapting the commands.

## Create a report with the CLI

Run these commands from this directory:

```bash
sh scripts/install-macos.sh
jbang --java 25 ../excel-context-xray/scripts/CreateFixture.java build/source.xlsx
jbang scripts/VerifyGridcraft.java recipe

.runtime/gridcraft-cli-0.5.0-macos-universal/gridcraft-cli run \
  --script build/report.jsonl \
  --out build/report.xlsx \
  --print B5:C5 \
  --quiet

jbang --offline scripts/VerifyGridcraft.java verify build/report.xlsx
```

The installer downloads the release into `.runtime`, checks its pinned SHA-256, and extracts it. It leaves global commands unchanged. The archive checksum does not establish a valid macOS code signature; signature verification failed for this release's arm64 CLI and desktop binaries.

The first JBang invocation for each source downloads dependencies and compiles it. Later invocations can use `--offline` with the same JDK. Rerunning the fixture generator replaces `build/source.xlsx`; reserve that path for the supplied synthetic fixture.

Open `build/report.xlsx` and check these cells on `Report`:

| Cell | Expected result |
|---|---|
| B5 | Baseline total: 80, calculated by `SUM(B2:B4)` |
| C5 | Scenario total: 100, calculated by `SUM(C2:C4)` |
| C7 | Difference: 20, calculated by `C5-B5` |
| B8 | Original stored source total: 60 |
| B9 | Recalculated source total: 80 |
| B10 | Source range: `Transactions!D2:D4` |
| B11 | SHA-256 of the unchanged source workbook |

The verifier checks the formulas, saved results, independent POI calculations, chart, frozen header, layout, and source hash. It exits with an error if a check fails.

## Create a report with Bob

Prepare the fixture and recipe as above, then configure the project MCP server:

```bash
sh scripts/configure-bob.sh
```

Open this directory as the Bob workspace. The script preserves other project MCP servers and configures a headless `gridcraft` server with absolute paths. Inspect [.bob/mcp.example.json](.bob/mcp.example.json), confirm the server connects, and check that the [gridcraft-report skill](.bob/skills/gridcraft-report/SKILL.md) appears with Workspace scope. Automatic MCP approval starts empty.

Send [the report prompt](prompts/returns-report.txt) in Bob, or use an authenticated Bob Shell environment:

```bash
bob run --workspace "$PWD" --disable-subagents --disable-tool-groups todo \
  --max-turns 60 \
  --format json "$(cat prompts/returns-report.txt)"
```

Bob saves the report to `build/bob-report.xlsx`. The prompt requires a single rectangular data write, explicit range reads, save-and-reopen checks, and Java verification. Check the saved file yourself:

```bash
jbang --offline scripts/VerifyGridcraft.java verify build/bob-report.xlsx
```

The [report contract](.bob/skills/gridcraft-report/references/returns-report.md) defines the rows, formulas, and chart. The skill limits each read to 512 cells, but the native MCP server does not enforce that instruction. Configure credentials through your normal Bob setup and keep them out of the repository.

## Inspect the workbook in GridCraft

Open the saved report in GridCraft desktop at 100% zoom. Select B5, C5, and C7 to inspect their formulas. Select the chart to inspect its source cells: the two series compare 10/20/50 with 10/20/70 and exclude the totals row. The full source hash should fit in B11.

With AutoSave off, temporarily change C4 from 70 to 90. C5 should become 120, C7 should become 40, and the third scenario bar should reach 90. Undo the change and confirm that 70, 100, and 20 return.

If labels or the hash are clipped, check the units in your formatting commands. `home.columnWidth` uses `chars:26` for 26 characters; `width:26` means screen points. Row 11 uses the range `11:11` and a height of 54 screen points is saved as 40.5 typographic points.

## Check the MCP connection

Run the fixture-specific protocol probe:

```bash
jbang --offline scripts/VerifyGridcraft.java probe \
  .runtime/gridcraft-cli-0.5.0-macos-universal/gridcraft-cli
```

The probe checks recalculation, formula reads, the default read range, the server's oversized-range guard, and rejection of screenshots in headless mode. It starts its own MCP process and closes it on completion. For Bob sessions, stop the server in Bob's MCP settings when finished.

## Supported scope

This example accepts the known synthetic fixture. Adapt the Java extraction and report contract before using another workbook. Keep large-file analysis in Java and pass selected results into a separate GridCraft report.

The checks cover three formulas and one two-series chart. Microsoft Excel opening and repair behavior have not been checked. Runtime binaries, generated workbooks, and machine-specific MCP configuration are ignored by Git. The example starts no desktop control port.
