#!/usr/bin/env bash

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEMO="${ROOT}/demo"
RUN_ID="$(date -u +"%Y-%m-%dT%H-%M-%SZ")"
RUN_ROOT="${ROOT}/runs/${RUN_ID}"
WORKSPACE="${RUN_ROOT}/workspace"
RESULTS_DIR="${ROOT}/results"

if [[ -z "${BOB_KEY_FILE:-}" ]]; then
    echo "Set BOB_KEY_FILE to the JSON file containing the Bob API key." >&2
    exit 2
fi

if [[ ! -r "${BOB_KEY_FILE}" ]]; then
    echo "Cannot read BOB_KEY_FILE: ${BOB_KEY_FILE}" >&2
    exit 2
fi

if ! command -v bob >/dev/null 2>&1; then
    echo "bob is not available on PATH." >&2
    exit 2
fi

if ! command -v jq >/dev/null 2>&1; then
    echo "jq is required." >&2
    exit 2
fi

BOB_VERSION="$(bob --version 2>/dev/null | head -n1 | tr -d '\r')"
API_KEY="$(jq --exit-status --raw-output '.apikey | select(type == "string" and length > 0)' "${BOB_KEY_FILE}")"

mkdir -p "${RUN_ROOT}" "${RESULTS_DIR}"
rm -rf "${WORKSPACE}"
cp -R "${DEMO}" "${WORKSPACE}"

MAX_COST="${BOB_MAX_COST:-0.25}"
MAX_TURNS="${BOB_MAX_TURNS:-10}"

redact_stream() {
    local input_file="$1"
    local output_file="$2"
    python3 - "${API_KEY}" "${input_file}" "${output_file}" <<'PY'
import sys
from pathlib import Path

secret = sys.argv[1]
source = Path(sys.argv[2])
target = Path(sys.argv[3])
text = source.read_text(encoding="utf-8")
if secret:
    text = text.replace(secret, "[REDACTED]")
target.write_text(text, encoding="utf-8")
PY
}

parse_stream() {
    local stream_file="$1"
    python3 - "${stream_file}" <<'PY'
import json
import sys
from pathlib import Path

events = []
for line in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines():
    if not line.strip():
        continue
    try:
        events.append(json.loads(line))
    except json.JSONDecodeError:
        continue

result = next((event for event in reversed(events) if event.get("type") == "result"), {})
tool_uses = [event for event in events if event.get("type") == "tool_use"]
skill_names = []
for event in tool_uses:
    if event.get("tool_name") != "use_skill":
        continue
    params = event.get("parameters") or {}
    name = params.get("skill_name") or params.get("skillName") or params.get("name")
    if name:
        skill_names.append(name)

print(json.dumps({
    "status": result.get("status", "unknown"),
    "taskId": (result.get("stats") or {}).get("task_id"),
    "cost": (result.get("stats") or {}).get("session_costs"),
    "durationMs": (result.get("stats") or {}).get("duration_ms"),
    "toolCalls": (result.get("stats") or {}).get("tool_calls"),
    "toolNames": sorted({event.get("tool_name") for event in tool_uses if event.get("tool_name")}),
    "skillActivations": sorted(set(skill_names)),
}))
PY
}

memory_list() {
    (cd "${WORKSPACE}" && python3 -m app.memory list)
}

run_session() {
    local label="$1"
    local prompt_file="$2"
    local session_dir="${RUN_ROOT}/${label}"
    mkdir -p "${session_dir}"

    local bob_args=(
        run
        --format stream-json
        --workspace "${WORKSPACE}"
        --mode agent
        --max-cost "${MAX_COST}"
        --max-turns "${MAX_TURNS}"
        --disable-mcp
        --disable-subagents
        --trust
        --accept-license
        --log-level error
    )

    if [[ -n "${BOB_TEAM_ID:-}" ]]; then
        bob_args+=(--team-id "${BOB_TEAM_ID}")
    fi

    echo "[${label}] Bob is running..." >&2
    set +e
    BOB_API_KEY="${API_KEY}" bob "${bob_args[@]}" < "${prompt_file}" \
        > "${session_dir}/bob.stdout.raw.jsonl" \
        2> "${session_dir}/bob.stderr.raw.log"
    exit_code=$?
    set -e

    redact_stream "${session_dir}/bob.stdout.raw.jsonl" "${session_dir}/bob.stdout.jsonl"
    redact_stream "${session_dir}/bob.stderr.raw.log" "${session_dir}/bob.stderr.log"
    rm -f "${session_dir}/bob.stdout.raw.jsonl" "${session_dir}/bob.stderr.raw.log"

    local telemetry
    telemetry="$(parse_stream "${session_dir}/bob.stdout.jsonl")"
    local memory_after
    memory_after="$(memory_list)"

    python3 - "${label}" "${exit_code}" "${telemetry}" "${memory_after}" "${session_dir}/session.json" <<'PY'
import json
import sys

label, exit_code, telemetry_json, memory_after, output_path = sys.argv[1:6]
payload = {
    "label": label,
    "bobExitCode": int(exit_code),
    "telemetry": json.loads(telemetry_json),
    "memoryAfter": memory_after.strip(),
}
Path = __import__("pathlib").Path
Path(output_path).write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
print(json.dumps(payload))
PY
}

echo "Bob version: ${BOB_VERSION}" >&2
echo "Workspace: ${WORKSPACE}" >&2

SESSION_SUMMARY="${RUN_ROOT}/sessions.jsonl"
: > "${SESSION_SUMMARY}"

run_session add "${WORKSPACE}/prompts/01-add.md" >> "${SESSION_SUMMARY}"
run_session update "${WORKSPACE}/prompts/02-update.md" >> "${SESSION_SUMMARY}"
run_session delete "${WORKSPACE}/prompts/03-delete.md" >> "${SESSION_SUMMARY}"

VALIDATED_FILE="${RESULTS_DIR}/validated-$(date -u +"%Y-%m-%d").json"
python3 - "${BOB_VERSION}" "${MAX_COST}" "${MAX_TURNS}" "${WORKSPACE}" "${VALIDATED_FILE}" "${SESSION_SUMMARY}" <<'PY'
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

bob_version = sys.argv[1]
max_cost = float(sys.argv[2])
max_turns = int(sys.argv[3])
workspace = sys.argv[4]
validated_file = sys.argv[5]
summary_path = Path(sys.argv[6])

sessions = [json.loads(line) for line in summary_path.read_text(encoding="utf-8").splitlines() if line.strip()]

summary = {
    "generatedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
    "bobVersion": bob_version,
    "workspace": workspace,
    "settings": {
        "maxCost": max_cost,
        "maxTurns": max_turns,
        "mcp": False,
        "subagents": False,
    },
    "sessions": sessions,
}
Path(validated_file).write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
print(f"Validated summary: {validated_file}")
PY

echo "Run artifacts: ${RUN_ROOT}" >&2
