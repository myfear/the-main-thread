#!/usr/bin/env bash

set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
    echo "Usage: $0 <worktree-directory> <prompt-file> [Quarkus-test-port]" >&2
    exit 2
fi

if [[ -z "${BOB_KEY_FILE:-}" ]]; then
    echo "Set BOB_KEY_FILE to the JSON file containing the Bob API key." >&2
    exit 2
fi

if [[ ! -r "${BOB_KEY_FILE}" ]]; then
    echo "Cannot read BOB_KEY_FILE: ${BOB_KEY_FILE}" >&2
    exit 2
fi

container_name="${BOB_CONTAINER_NAME:-bob-worktree-lab}"
test_port="${3:-8081}"
repository_root="$(git rev-parse --show-toplevel)"
repository_root="$(cd "${repository_root}" && pwd -P)"

if [[ ! "${test_port}" =~ ^[0-9]+$ ]] || ((test_port < 1024 || test_port > 65535)); then
    echo "Quarkus test port must be an integer from 1024 through 65535." >&2
    exit 2
fi

worktree_path="$1"
if [[ "${worktree_path}" != /* ]]; then
    worktree_path="${repository_root}/${worktree_path}"
fi
worktree_path="$(cd "${worktree_path}" && pwd -P)"

prompt_file="$2"
if [[ "${prompt_file}" != /* ]]; then
    prompt_file="${repository_root}/${prompt_file}"
fi
if [[ ! -r "${prompt_file}" ]]; then
    echo "Cannot read prompt file: ${prompt_file}" >&2
    exit 2
fi

if [[ "$(git -C "${worktree_path}" rev-parse --show-toplevel)" != "${worktree_path}" ]]; then
    echo "The workspace must be the root of a Git worktree: ${worktree_path}" >&2
    exit 2
fi

api_key="$(jq --exit-status --raw-output \
    '.apikey | select(type == "string" and length > 0)' "${BOB_KEY_FILE}")"

bob_arguments=(
    bob run
    --format stream-json
    --workspace "${worktree_path}"
    --mode agent
    --max-turns "${BOB_MAX_TURNS:-20}"
    --max-cost "${BOB_MAX_COST:-2}"
    --disable-mcp
    --disable-subagents
    --trust
)

if [[ -n "${BOB_TEAM_ID:-}" ]]; then
    bob_arguments+=(--team-id "${BOB_TEAM_ID}")
fi

BOB_API_KEY="${api_key}" QUARKUS_HTTP_TEST_PORT="${test_port}" podman exec --interactive \
    --workdir "${worktree_path}" \
    --env BOB_API_KEY \
    --env QUARKUS_HTTP_TEST_PORT \
    "${container_name}" \
    "${bob_arguments[@]}" < "${prompt_file}"
