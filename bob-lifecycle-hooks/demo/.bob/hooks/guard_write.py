#!/usr/bin/env python3
from __future__ import annotations

import sys

from hooklib import path_is_allowed, paths_from_payload, read_payload


def main() -> int:
    try:
        payload = read_payload()
        paths = paths_from_payload(payload)
    except ValueError as exc:
        print(f"Blocked write: {exc}", file=sys.stderr)
        return 2

    if not paths:
        print("Blocked write: the hook payload contained no file path", file=sys.stderr)
        return 2

    blocked = [path for path in paths if not path_is_allowed(path)]
    if blocked:
        print(
            "Blocked write outside app/, tests/, and README.md: " + ", ".join(blocked),
            file=sys.stderr,
        )
        return 2

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

