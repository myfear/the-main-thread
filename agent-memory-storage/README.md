# Agent Memory Storage Lab

This project builds a durable agent memory as a storage system. It ships a small file-backed memory store, Bob lifecycle hooks that inject a compact index at session start, and three Bob Shell prompts that add, update, and delete a user preference record.

## Layout

- `article.md`: the Substack article
- `demo/`: clean starting workspace
- `scripts/run-lab.sh`: copy `demo/` to a disposable directory and run three Bob sessions
- `results/`: sanitized validation output (no API keys)

## Verify the store without Bob

```bash
cd demo
python3 -m unittest discover -s tests -v
```

## Run the three Bob sessions

Set `BOB_KEY_FILE` to a JSON file with a top-level `apikey` field. Do not commit that file.

```bash
export BOB_KEY_FILE=/absolute/path/to/bob-api-key.json
./scripts/run-lab.sh
unset BOB_API_KEY BOB_KEY_FILE
```

Review every command under `demo/.bob/settings.json` before passing `--trust`. Bob lifecycle hooks run with your user permissions.
