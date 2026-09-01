#!/usr/bin/env bash

set -euo pipefail

container_name="${BOB_CONTAINER_NAME:-bob-worktree-lab}"
image_name="${BOB_IMAGE_NAME:-localhost/bob-worktree-lab:2.0.2}"
bob_state_volume="${BOB_STATE_VOLUME:-bob-worktree-lab-state}"
maven_cache_volume="${BOB_MAVEN_VOLUME:-bob-worktree-lab-maven}"
repository_root="$(git rev-parse --show-toplevel)"
repository_root="$(cd "${repository_root}" && pwd -P)"

if podman container exists "${container_name}"; then
    echo "Container ${container_name} already exists." >&2
    echo "Run scripts/stop-sandbox.sh before starting it again." >&2
    exit 1
fi

if ! podman volume exists "${bob_state_volume}"; then
    podman volume create "${bob_state_volume}" >/dev/null
fi
if ! podman volume exists "${maven_cache_volume}"; then
    podman volume create "${maven_cache_volume}" >/dev/null
fi

podman run --detach \
    --name "${container_name}" \
    --read-only \
    --cap-drop ALL \
    --security-opt no-new-privileges \
    --pids-limit 512 \
    --memory 4g \
    --cpus 4 \
    --tmpfs /tmp:rw,nosuid,nodev,size=1g \
    --volume "${repository_root}:${repository_root}:rw,Z" \
    --volume "${bob_state_volume}:/root/.bob:rw,Z" \
    --volume "${maven_cache_volume}:/root/.m2:rw,Z" \
    --workdir "${repository_root}" \
    "${image_name}"

echo "Started ${container_name} with ${repository_root} mounted at the same absolute path."

# Download the Maven distribution and project dependencies once. Two first-run
# wrappers sharing one cache can otherwise race while validating the same ZIP.
podman exec \
    --workdir "${repository_root}" \
    "${container_name}" \
    ./mvnw --batch-mode --no-transfer-progress test

echo "Primed the Maven cache and verified the baseline tests."
