# Release Policy Lab

This workspace is a bounded IBM Bob lifecycle-hooks exercise.

- Production code lives under `app/`
- Unit tests live under `tests/`
- `acceptance/` is read-only acceptance criteria; never edit it to make a run pass
- `.bob/`, `AGENTS.md`, and `.gitignore` are control files; do not change them during the feature task
- Keep the implementation on the Python standard library
- Run `python3 -m unittest discover -s tests -v`
- Run `python3 -m unittest discover -s acceptance -v`
- Report the commands, exit codes, and changed files when finished

