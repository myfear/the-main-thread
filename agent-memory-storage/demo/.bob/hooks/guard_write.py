#!/usr/bin/env python3
from __future__ import annotations

import sys

from hooklib import paths_from_payload, path_targets_memory, read_payload


def main() -> int:
    try:
        payload = read_payload()
        paths = paths_from_payload(payload)
    except ValueError as exc:
        print(f"Blocked write: {exc}", file=sys.stderr)
        return 2

    if not paths:
        return 0

    blocked = [path for path in paths if path_targets_memory(path)]
    if blocked:
        print(
            "Blocked direct write to memory/. Use python3 -m app.memory put or delete: "
            + ", ".join(blocked),
            file=sys.stderr,
        )
        return 2

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
