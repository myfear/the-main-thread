#!/usr/bin/env bash

set -euo pipefail

container_name="${BOB_CONTAINER_NAME:-bob-worktree-lab}"

if podman container exists "${container_name}"; then
    podman rm --force "${container_name}" >/dev/null
    echo "Removed ${container_name}."
else
    echo "Container ${container_name} does not exist."
fi
