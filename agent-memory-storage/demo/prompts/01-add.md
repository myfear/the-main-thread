Use the agent-memory skill.

Create a new preferences record for user `demo-user` at path `preferences.md` with provenance `stated`. Store these facts:

- The user's time zone is Europe/Berlin.
- The user prefers Markdown deliverables.

Use `python3 -m app.memory put` with `--if-version new`. Do not edit files under `memory/` directly. Do not change `.bob/`, `AGENTS.md`, or application code. Finish with `python3 -m app.memory list`.
