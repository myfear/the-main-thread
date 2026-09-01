from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path.cwd().resolve()
PATCH_PATH = re.compile(r"^\*\*\* (?:Add|Update|Delete) File: (.+)$", re.MULTILINE)


def read_payload() -> dict[str, Any]:
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError as exc:
        raise ValueError(f"Hook input is not valid JSON: {exc.msg}") from exc
    if not isinstance(payload, dict):
        raise ValueError("Hook input must be a JSON object")
    return payload


def tool_input(payload: dict[str, Any]) -> dict[str, Any]:
    value = payload.get("input", payload.get("tool_input", {}))
    return value if isinstance(value, dict) else {}


def paths_from_payload(payload: dict[str, Any]) -> list[str]:
    value = tool_input(payload)
    paths: list[str] = []

    for key in ("path", "file_path"):
        candidate = value.get(key)
        if isinstance(candidate, str) and candidate.strip():
            paths.append(candidate.strip())

    command = value.get("command")
    if isinstance(command, str):
        paths.extend(match.strip() for match in PATCH_PATH.findall(command))

    return list(dict.fromkeys(paths))


def path_targets_memory(raw_path: str) -> bool:
    candidate = Path(raw_path)
    resolved = candidate.resolve() if candidate.is_absolute() else (ROOT / candidate).resolve()
    memory_root = (ROOT / "memory").resolve()
    try:
        resolved.relative_to(memory_root)
        return True
    except ValueError:
        return False


def run_memory_list() -> str:
    import subprocess

    completed = subprocess.run(
        [sys.executable, "-m", "app.memory", "list"],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
        timeout=8,
    )
    if completed.returncode != 0:
        return "Memory index unavailable."
    return completed.stdout.strip()
