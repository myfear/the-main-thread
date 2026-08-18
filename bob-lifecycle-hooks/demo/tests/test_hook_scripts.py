from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
HOOKS = ROOT / ".bob" / "hooks"


def run_hook(
    name: str,
    payload: dict[str, object] | str,
    extra_env: dict[str, str] | None = None,
) -> subprocess.CompletedProcess[str]:
    data = payload if isinstance(payload, str) else json.dumps(payload)
    env = os.environ.copy()
    if extra_env:
        env.update(extra_env)
    return subprocess.run(
        [sys.executable, str(HOOKS / name)],
        cwd=ROOT,
        input=data,
        text=True,
        capture_output=True,
        check=False,
        env=env,
        timeout=15,
    )


class GuardWriteTest(unittest.TestCase):
    def test_allows_documented_bob_path(self) -> None:
        result = run_hook(
            "guard_write.py",
            {
                "event": "PreToolUse",
                "session_id": "test",
                "tool": "write_file",
                "input": {"path": "app/release_policy.py", "content": "..."},
            },
        )

        self.assertEqual(0, result.returncode, result.stderr)

    def test_blocks_control_file(self) -> None:
        result = run_hook(
            "guard_write.py",
            {
                "event": "PreToolUse",
                "session_id": "test",
                "tool": "write_file",
                "input": {"path": ".bob/settings.json", "content": "{}"},
            },
        )

        self.assertEqual(2, result.returncode)
        self.assertIn(".bob/settings.json", result.stderr)

    def test_fails_closed_for_invalid_json(self) -> None:
        result = run_hook("guard_write.py", "not-json")

        self.assertEqual(2, result.returncode)
        self.assertIn("Blocked write", result.stderr)

    def test_blocks_path_outside_workspace(self) -> None:
        result = run_hook(
            "guard_write.py",
            {
                "event": "PreToolUse",
                "session_id": "test",
                "tool": "write_file",
                "input": {"path": "../escape.txt", "content": "..."},
            },
        )

        self.assertEqual(2, result.returncode)

    def test_accepts_claude_file_path_shape(self) -> None:
        result = run_hook(
            "guard_write.py",
            {
                "hook_event_name": "PreToolUse",
                "tool_name": "Write",
                "tool_input": {"file_path": "app/release_policy.py"},
            },
        )

        self.assertEqual(0, result.returncode, result.stderr)

    def test_accepts_installed_bob_runtime_shape(self) -> None:
        result = run_hook(
            "guard_write.py",
            {
                "hook_event_name": "PreToolUse",
                "session_id": "test",
                "cwd": str(ROOT),
                "tool_name": "apply_diff",
                "tool_input": {"path": "app/release_policy.py", "diff": "..."},
                "tool_use_id": "tool-test",
            },
        )

        self.assertEqual(0, result.returncode, result.stderr)

    def test_reads_codex_apply_patch_paths(self) -> None:
        result = run_hook(
            "guard_write.py",
            {
                "hook_event_name": "PreToolUse",
                "tool_name": "apply_patch",
                "tool_input": {
                    "command": "*** Begin Patch\n*** Update File: .bob/settings.json\n*** End Patch"
                },
            },
        )

        self.assertEqual(2, result.returncode)
        self.assertIn(".bob/settings.json", result.stderr)


class HookConfigurationTest(unittest.TestCase):
    def test_edit_matchers_cover_only_bob_native_edit_tools(self) -> None:
        settings = json.loads((ROOT / ".bob" / "settings.json").read_text())
        expected = {
            "write_file",
            "apply_diff",
            "search_and_replace",
            "insert_content",
        }

        for event in ("PreToolUse", "PostToolUse"):
            matcher = settings["hooks"][event][0]["matcher"]
            matched = {name for name in expected if re.fullmatch(matcher, name)}
            self.assertEqual(expected, matched)
            self.assertIsNone(re.fullmatch(matcher, "execute_command"))


class EvidenceHookTest(unittest.TestCase):
    def test_session_start_injects_bounded_context(self) -> None:
        with tempfile.TemporaryDirectory() as state_dir:
            result = run_hook(
                "session_start.py",
                {"event": "SessionStart", "session_id": "test"},
                {"BOB_HOOK_STATE_DIR": state_dir},
            )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("Allowed writes: app/, tests/, README.md", result.stdout)
        self.assertIn("Protected acceptance criteria: acceptance/", result.stdout)

    def test_post_write_records_verification(self) -> None:
        with tempfile.TemporaryDirectory() as state_dir:
            result = run_hook(
                "post_write.py",
                {
                    "event": "PostToolUse",
                    "session_id": "test",
                    "tool": "write_file",
                    "input": {"path": "app/release_policy.py"},
                    "output": "File written successfully",
                },
                {
                    "BOB_HOOK_STATE_DIR": state_dir,
                    "BOB_HOOK_VERIFY_COMMAND": f'{sys.executable} -c "print(12345)"',
                },
            )
            report = (Path(state_dir) / "last-verification.txt").read_text(
                encoding="utf-8"
            )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("status=PASS", report)
        self.assertIn("12345", report)

    def test_prompt_context_returns_previous_verification(self) -> None:
        with tempfile.TemporaryDirectory() as state_dir:
            Path(state_dir, "last-verification.txt").write_text(
                "status=FAIL\nexpected 2.1",
                encoding="utf-8",
            )
            result = run_hook(
                "prompt_context.py",
                {
                    "event": "UserPromptSubmit",
                    "session_id": "test",
                    "prompt": "Fix the failure",
                },
                {"BOB_HOOK_STATE_DIR": state_dir},
            )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("status=FAIL", result.stdout)
        self.assertIn("expected 2.1", result.stdout)

    def test_stop_writes_final_report(self) -> None:
        with tempfile.TemporaryDirectory() as state_dir:
            result = run_hook(
                "stop_report.py",
                {"event": "Stop", "session_id": "test-session"},
                {
                    "BOB_HOOK_STATE_DIR": state_dir,
                    "BOB_HOOK_VERIFY_COMMAND": f'{sys.executable} -c "print(67890)"',
                },
            )
            report = (Path(state_dir) / "final-report.md").read_text(encoding="utf-8")

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("Verification: **PASS**", report)
        self.assertIn("test-session", report)
        self.assertIn("67890", report)


if __name__ == "__main__":
    unittest.main()
