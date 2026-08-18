#!/usr/bin/env python3
from __future__ import annotations

from hooklib import git_output, read_payload, run_verification, timestamp, write_state


def main() -> int:
    payload = read_payload()
    passed, output = run_verification()
    status = "PASS" if passed else "FAIL"
    session_id = payload.get("session_id", "unknown")
    changed = git_output("status", "--short")

    report = f"""# Bob Hook Report

- Timestamp: `{timestamp()}`
- Session: `{session_id}`
- Verification: **{status}**

## Changed Files

```text
{changed}
```

## Verification Output

```text
{output}
```
"""
    write_state("final-report.md", report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

