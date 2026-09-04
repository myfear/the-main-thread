# Bob Shell and code-review-graph on Open Liberty

This directory contains the reproducible companion material for the article
[`article.md`](article.md). It connects `code-review-graph` 2.3.8 to IBM Bob Shell 2.0.2 over
MCP, indexes a pinned Open Liberty checkout, and runs the same two navigation prompts with and
without the graph.

The measured conclusion is deliberately unglamorous: the integration works, but this experiment
did not demonstrate a quality-gated improvement. Across two narrow-task pairs, the graph condition
was 18.8% faster on average but found one of ten requested current call sites; solo Bob found all
ten. Both conditions exhausted the higher budget twice on the wider trace.

## Contents

- `prompts/` contains the two read-only navigation tasks.
- `scripts/setup.sh` creates a pinned Open Liberty checkout, installs the pinned graph version,
  builds the graph, and adds the project MCP entry to Bob.
- `scripts/run-benchmark.sh` runs the solo and graph conditions and redacts the supplied API key
  from the JSONL stream.
- `scripts/summarize.py` extracts timing, reported cost, tool calls, and tool-result characters.
- `scripts/grade.py` checks the five source-verified call sites from the narrow task.
- `scripts/inspect_graph.py` reports resolved and bare call targets directly from the graph database.
- `results/benchmark.json` contains the compact published measurements.

Raw run transcripts are intentionally excluded. They contain large source excerpts, machine paths,
and transient task identifiers. The runner writes new transcripts under `runs/`, which Git ignores.

## Run the experiment

The setup needs `bob`, `git`, `uv`, `jq`, Python, and enough free disk space. The measured graph
database was 6.9 GiB.

```bash
./scripts/setup.sh /path/to/open-liberty /path/to/crg-venv
chmod 600 /path/to/bob-key.json
./scripts/run-benchmark.sh \
  /path/to/open-liberty \
  /path/to/bob-key.json
```

The key file must contain a non-empty `.apikey` string. The script loads it into `BOB_API_KEY` for
the Bob process, filters the event stream, and does not copy the value into the run directory.

Grade the feature-resolution answers after the run:

```bash
./scripts/grade.py \
  runs/<timestamp>/summary.json \
  01-feature-resolution-solo

./scripts/grade.py \
  runs/<timestamp>/summary.json \
  01-feature-resolution-graph
```

The model service is nondeterministic. Repeat each condition before making a general performance
claim, keep the pinned repository commit, and grade correctness before comparing speed or cost.

Inspect the graph relationships behind the result:

```bash
./scripts/inspect_graph.py /path/to/open-liberty
```
