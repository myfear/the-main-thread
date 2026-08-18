---
name: safe-release-change
description: Implement a bounded release-policy feature while preserving acceptance tests and reporting verification evidence
---

Work only on the requested release-policy behavior.

1. Read `AGENTS.md`, `app/release_policy.py`, the unit tests, and the acceptance tests.
2. Treat `acceptance/` and `.bob/` as read-only control files.
3. Explain the smallest behavior change before editing.
4. Implement production code under `app/`.
5. Add unit tests under `tests/` when they add coverage beyond `acceptance/`.
6. Run `python3 -m unittest discover -s tests -v`.
7. Run `python3 -m unittest discover -s acceptance -v`.
8. Read `.bob/state/final-report.md` if it exists and reconcile any mismatch with the commands you ran.
9. Finish with changed files, commands, exit codes, and remaining limits.

