#!/usr/bin/env bash
set -euo pipefail

module_dir=$(cd -- "$(dirname -- "$0")/.." && pwd)
recordings_dir=${ATEM_RECORDINGS_DIR:-"$module_dir/recordings"}
mkdir -p -- "$recordings_dir"
recordings_dir=$(cd -- "$recordings_dir" && pwd)

exec podman run --rm --name "${ATEM_CONTAINER_NAME:-atem-mediamtx}" \
    -p "${ATEM_RTMP_BIND:-127.0.0.1}:${ATEM_RTMP_PORT:-1935}:1935" \
    -p "127.0.0.1:${ATEM_PLAYBACK_PORT:-9996}:9996" \
    -p "127.0.0.1:${ATEM_CONTROL_PORT:-9997}:9997" \
    -v "$module_dir/mediamtx.yml:/mediamtx.yml:ro" \
    -v "$recordings_dir:/recordings" \
    -e "MTX_PATHDEFAULTS_RECORDSEGMENTDURATION=${ATEM_SEGMENT_DURATION:-10m}" \
    docker.io/bluenviron/mediamtx:1.21.3
