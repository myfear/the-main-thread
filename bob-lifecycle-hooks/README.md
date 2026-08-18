# IBM Bob Lifecycle Hooks Lab

This project supports the hands-on tutorial in [article.md](article.md). It uses IBM Bob 2.0.2 lifecycle hooks to inject repository context, block Bob's native edit tools outside a bounded path set, record verification after edits, and create a final local report.

## Layout

- `demo/`: clean starting workspace for the Bob task
- `solution/`: expected application behavior after the task
- `migration/`: equivalent `PreToolUse` configuration for Bob, Codex, and Claude Code

## Verify the Artifacts

The demo's unit and hook tests pass:

```bash
cd demo
python3 -m json.tool .bob/settings.json >/dev/null
python3 -m unittest discover -s tests -v
```

The demo acceptance suite is intentionally red until Bob implements `recommended_upgrade()`:

```bash
python3 -m unittest discover -s acceptance -v
```

The reference solution is fully green:

```bash
cd ../solution
python3 -m unittest discover -s tests -v
python3 -m unittest discover -s acceptance -v
```

## Run the Agent Loop with BobShell

BobShell 2.0.1 runs the same project hooks headlessly. Set `BOB_API_KEY` from a secret manager or a key file outside this repository, then run from `demo/`:

```bash
bob --version
bob run \
  --format pretty \
  --workspace "$PWD" \
  --mode agent \
  --max-turns 20 \
  --max-cost 2 \
  --disable-mcp \
  --disable-subagents \
  --trust \
  --accept-license \
  "Use the safe-release-change skill and implement the missing recommended_upgrade behavior described by the read-only acceptance tests. Keep acceptance/, .bob/, AGENTS.md, and .gitignore unchanged. Run the unit and acceptance suites."
```

Use a disposable copy of `demo/` because the task is expected to edit `app/` and `tests/`. After the task, inspect `.bob/state/last-verification.txt` and `.bob/state/final-report.md`.

Review every command under `demo/.bob/settings.json` before trusting the workspace. Bob lifecycle hooks run with the current user's permissions.
