#!/usr/bin/env python3
"""Apply the source-checked quality gate used in the article."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


FEATURE_FACTS = {
    "resolveFeatures at line 1582": ("resolveFeatures", ":1582"),
    "installBundles at line 1640": ("installBundles", ":1640"),
    "uninstallBundles at line 1666": ("uninstallBundles", ":1666"),
    "resolveBundles at line 1694": ("resolveBundles", ":1694"),
    "preStartBundles at line 1711": ("preStartBundles", ":1711"),
}


def load_run(summary: Path, run_name: str) -> dict[str, Any]:
    payload = json.loads(summary.read_text(encoding="utf-8"))
    for run in payload.get("runs", []):
        if run.get("run") == run_name:
            return run
    raise SystemExit(f"run not found: {run_name}")


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: grade.py SUMMARY_JSON RUN_NAME")
    run = load_run(Path(sys.argv[1]), sys.argv[2])
    answer = str(run.get("answer", ""))
    checks = {
        label: symbol in answer and line in answer
        for label, (symbol, line) in FEATURE_FACTS.items()
    }
    score = sum(checks.values())
    print(
        json.dumps(
            {
                "run": run.get("run"),
                "status": run.get("status"),
                "correct_feature_calls": score,
                "possible_feature_calls": len(checks),
                "passed": run.get("status") == "success" and score == len(checks),
                "checks": checks,
            },
            indent=2,
            sort_keys=True,
        )
    )


if __name__ == "__main__":
    main()
