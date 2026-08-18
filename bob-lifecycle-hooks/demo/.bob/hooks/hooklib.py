from __future__ import annotations

import json
import os
import re
import shlex
import subprocess
import sys
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


ROOT = Path.cwd().resolve()
STATE_DIR = Path(os.environ.get("BOB_HOOK_STATE_DIR", ROOT / ".bob" / "state"))
PATCH_PATH = re.compile(r"^\*\*\* (?:Add|Update|Delete) File: (.+)$", re.MULTILINE)


def read_payload() -> dict[str, Any]:
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError as exc:
        raise ValueError(f"Hook input is not valid JSON: {exc.msg}") from exc
    if not isinstance(payload, dict):
        raise ValueError("Hook input must be a JSON object")
    return payload


def event_name(payload: dict[str, Any]) -> str:
    value = payload.get("event", payload.get("hook_event_name", ""))
    return value if isinstance(value, str) else ""


def tool_name(payload: dict[str, Any]) -> str:
    value = payload.get("tool", payload.get("tool_name", ""))
    return value if isinstance(value, str) else ""


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


def path_is_allowed(raw_path: str) -> bool:
    candidate = Path(raw_path)
    resolved = candidate.resolve() if candidate.is_absolute() else (ROOT / candidate).resolve()

    try:
        relative = resolved.relative_to(ROOT)
    except ValueError:
        return False

    allowed = os.environ.get("BOB_HOOK_ALLOWED_PATHS", "app,tests,README.md")
    for entry in (item.strip() for item in allowed.split(",")):
        if not entry:
            continue
        allowed_path = Path(entry)
        if relative == allowed_path or allowed_path in relative.parents:
            return True
    return False


def git_output(*args: str) -> str:
    completed = subprocess.run(
        ["git", *args],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
        timeout=5,
    )
    return completed.stdout.strip() if completed.returncode == 0 else "unavailable"


def verification_commands() -> list[list[str]]:
    override = os.environ.get("BOB_HOOK_VERIFY_COMMAND")
    if override:
        return [shlex.split(override)]
    return [
        [sys.executable, "-m", "unittest", "discover", "-s", "tests", "-v"],
        [sys.executable, "-m", "unittest", "discover", "-s", "acceptance", "-v"],
    ]


def run_verification() -> tuple[bool, str]:
    sections: list[str] = []
    passed = True

    for command in verification_commands():
        try:
            completed = subprocess.run(
                command,
                cwd=ROOT,
                text=True,
                capture_output=True,
                check=False,
                timeout=12,
            )
            passed = passed and completed.returncode == 0
            output = "\n".join(
                part.strip() for part in (completed.stdout, completed.stderr) if part.strip()
            )
            sections.append(
                f"$ {shlex.join(command)}\nexit={completed.returncode}\n{output}".rstrip()
            )
        except (OSError, subprocess.TimeoutExpired) as exc:
            passed = False
            sections.append(f"$ {shlex.join(command)}\nerror={exc}")

    report = "\n\n".join(sections)
    return passed, report[-12000:]


def write_state(filename: str, content: str) -> Path:
    STATE_DIR.mkdir(parents=True, exist_ok=True)
    target = STATE_DIR / filename
    temporary = STATE_DIR / f".{filename}.tmp"
    temporary.write_text(content, encoding="utf-8")
    temporary.replace(target)
    return target


def timestamp() -> str:
    return datetime.now(UTC).replace(microsecond=0).isoformat()
