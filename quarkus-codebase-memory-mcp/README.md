# quarkus-codebase-memory-mcp

Companion repo for indexing the Quarkus platform tree with [codebase-memory-mcp](https://github.com/DeusData/codebase-memory-mcp) and comparing graph queries against a shell-only baseline in **IBM Bob**.

## Quick start

```bash
# 1. Install codebase-memory-mcp (once per machine)
curl -fsSL https://raw.githubusercontent.com/DeusData/codebase-memory-mcp/main/install.sh | bash
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# 2. Clone Quarkus if needed
git clone https://github.com/quarkusio/quarkus.git ~/Projects/quarkus

# 3. Copy Bob MCP config into the Quarkus workspace
mkdir -p ~/Projects/quarkus/.bob
cp .bob/mcp.json ~/Projects/quarkus/.bob/mcp.json
# If Bob cannot find the binary, edit command to the full path under ~/.local/bin

# 4. Verify install and index the tree
./scripts/verify-install.sh
./scripts/index-quarkus.sh ~/Projects/quarkus
```

Open **the Quarkus checkout** in IBM Bob. Reload MCP. Run the prompts in `prompts/` in fresh chats:

1. `04-index-quarkus.md` — index through Bob (optional if you indexed from the shell)
2. `03-architecture-overview.md` — `get_architecture` first
3. `01-baseline-config-mapping.md` — shell and file reads only
4. `02-graph-config-mapping.md` — graph tools first
5. `prompts/05-build-item-cypher.md` — short Cypher-only check (optional)

See `benchmarks/bob-run-notes.md` for exported Bob chat analysis (v1 and v2 runs).

Optional shell checks: `./scripts/run-baseline-rg.sh` and `./scripts/run-graph-queries.sh`.

## Contents

- `article.md` — full Main Thread walkthrough
- `.bob/mcp.json` — Bob MCP config (copy into the Quarkus workspace root)
- `prompts/` — Bob prompts for index, baseline, graph, and architecture runs
- `queries/` — starter `search_graph` args and Cypher queries
- `scripts/` — install check, index helper, baseline `rg`, graph query runner
- `benchmarks/rubric.md` — scoring rubric for baseline vs graph paths
- `benchmarks/bob-run-notes.md` — analysis of exported Bob chats


