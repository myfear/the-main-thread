#!/usr/bin/env python3
"""Create a compact, publishable summary from BobShell JSONL runs."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any


def load_events(path: Path) -> list[dict[str, Any]]:
    events: list[dict[str, Any]] = []
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        try:
            value = json.loads(line)
        except json.JSONDecodeError:
            continue
        if isinstance(value, dict):
            events.append(value)
    return events


def normalize(text: str, workspace: str) -> str:
    prefix = workspace.rstrip("/") + "/"
    return text.replace(prefix, "")


def summarize(path: Path, workspace: str) -> dict[str, Any]:
    events = load_events(path)
    tool_uses = [event for event in events if event.get("type") == "tool_use"]
    tool_results = [event for event in events if event.get("type") == "tool_result"]
    result = next(
        (event for event in reversed(events) if event.get("type") == "result"),
        {},
    )
    limit_reached = any(
        event.get("type") == "error" and "limit" in str(event.get("message", "")).lower()
        for event in events
    )
    assistant_text = "".join(
        str(event.get("content", ""))
        for event in events
        if event.get("type") == "message" and event.get("role") == "assistant"
    )
    tool_names = [str(event.get("tool_name", "")) for event in tool_uses]
    normalized_results = [
        normalize(str(event.get("output", "")), workspace) for event in tool_results
    ]
    observed_paths = sorted(
        {
            match
            for text in [assistant_text, *normalized_results]
            for match in re.findall(r"(?:dev|\.bob)/[^\s:`'\"),]+", text)
        }
    )
    stats = result.get("stats") if isinstance(result.get("stats"), dict) else {}
    return {
        "run": path.stem,
        "status": "limit_reached" if limit_reached else result.get("status", "missing"),
        "duration_ms": stats.get("duration_ms"),
        "cost": stats.get("session_costs"),
        "tool_calls": stats.get("tool_calls", len(tool_uses)),
        "mcp_calls": sum(name.startswith("mcp__") for name in tool_names),
        "native_calls": sum(not name.startswith("mcp__") for name in tool_names),
        "tool_result_chars": sum(len(text) for text in normalized_results),
        "tool_names": tool_names,
        "observed_paths": observed_paths,
        "answer": normalize(assistant_text, workspace),
    }


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: summarize.py RUN_DIRECTORY WORKSPACE")
    run_directory = Path(sys.argv[1]).resolve()
    workspace = str(Path(sys.argv[2]).resolve())
    summaries = [
        summarize(path, workspace)
        for path in sorted(run_directory.glob("*.jsonl"))
    ]
    payload = {"workspace": "<open-liberty-checkout>", "runs": summaries}
    print(json.dumps(payload, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
