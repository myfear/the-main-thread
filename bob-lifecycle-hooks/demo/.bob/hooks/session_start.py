#!/usr/bin/env python3
from __future__ import annotations

from hooklib import STATE_DIR, git_output, read_payload


def main() -> int:
    read_payload()
    last_report = STATE_DIR / "final-report.md"
    previous = (
        last_report.read_text(encoding="utf-8")[-2000:]
        if last_report.exists()
        else "No previous hook report exists."
    )

    print("Release Policy Lab context")
    print(f"Git branch: {git_output('branch', '--show-current')}")
    print("Allowed writes: app/, tests/, README.md")
    print("Protected acceptance criteria: acceptance/")
    print("Verification: python3 -m unittest discover -s tests -v")
    print("Acceptance: python3 -m unittest discover -s acceptance -v")
    print("Previous report:")
    print(previous)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

