# Moving a Repository Policy Plugin to IBM Bob

The three JSON files show the same `PreToolUse` policy wired into IBM Bob, Codex, and Claude Code. The handler used by the lab accepts the payload shape in IBM's lifecycle documentation, the shape emitted by the installed Bob 2.0.2 runtime, and the corresponding Codex and Claude Code fields. It blocks by writing a reason to stderr and returning exit code `2`, which is the portable blocking contract for `PreToolUse` across the three hosts.

Bob has four native edit tools in 2.0.2: `write_file`, `apply_diff`, `search_and_replace`, and `insert_content`. The Bob matcher covers all four. It does not cover `execute_command`, an MCP filesystem tool, or another extension that can change files, so keep Bob's approval policy and CI controls in place.

IBM Bob's current documentation defines no loader for `.codex-plugin/plugin.json` or `.claude-plugin/plugin.json`. Move each component to its Bob-owned location:

- Skills: `.bob/skills/<name>/SKILL.md`
- Commands: `.bob/commands/<name>.md`
- Agent definitions: `.bob/agents/<name>.md`
- Hooks: `.bob/settings.json` plus scripts under `.bob/hooks/`
- MCP servers: `.bob/mcp.json`
- Main-agent roles and tool ceilings: `.bob/custom_modes.yaml`
- Standing repository guidance: `AGENTS.md` or `.bob/rules/`

Copy behavior before metadata. Keep the skill text, scripts, and MCP server implementation where their contracts are portable. Rewrite manifest paths, host environment variables, hook payload adapters, tool names, permissions, and event outputs.

`PostToolUse` and `Stop` need design changes when moving from Codex or Claude Code. Bob 2.0.2 ignores their stdout, and exit code `2` cannot block or continue the agent. Use those Bob events for formatting, evidence capture, cleanup, or notifications. Feed stored evidence into a later `UserPromptSubmit` or `SessionStart` hook when Bob needs to see it.
