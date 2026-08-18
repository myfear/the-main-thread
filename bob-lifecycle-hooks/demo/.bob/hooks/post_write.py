#!/usr/bin/env python3
from __future__ import annotations

from hooklib import read_payload, run_verification, timestamp, write_state


def main() -> int:
    read_payload()
    passed, output = run_verification()
    status = "PASS" if passed else "FAIL"
    write_state(
        "last-verification.txt",
        f"timestamp={timestamp()}\nstatus={status}\n\n{output}\n",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

