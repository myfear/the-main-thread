Use the agent-memory skill.

Update the existing `preferences.md` record for user `demo-user`. Keep the current facts and add:

- The user prefers Podman over Docker for container examples.

Read the record first to get the current version token. Use `python3 -m app.memory put` with that token as `--if-version`. Do not edit files under `memory/` directly. Do not change `.bob/`, `AGENTS.md`, or application code. Finish with `python3 -m app.memory list`.
