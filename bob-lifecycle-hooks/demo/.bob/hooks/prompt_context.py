#!/usr/bin/env python3
from __future__ import annotations

from hooklib import STATE_DIR, git_output, read_payload


def main() -> int:
    read_payload()
    last_verification = STATE_DIR / "last-verification.txt"
    result = (
        last_verification.read_text(encoding="utf-8")[-3000:]
        if last_verification.exists()
        else "No verification hook has run yet."
    )

    print("Current workspace evidence")
    print(f"Changed files:\n{git_output('status', '--short')}")
    print("Last automatic verification:")
    print(result)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

