#!/usr/bin/env bash
set -euo pipefail

duration_args=()
if (( $# > 0 )); then
    duration_args=(-t "$1")
fi

exec ffmpeg -hide_banner -re \
    -f lavfi -i 'testsrc2=size=1280x720:rate=30' \
    -f lavfi -i 'sine=frequency=440:sample_rate=48000' \
    -c:v libx264 -preset ultrafast -tune zerolatency -pix_fmt yuv420p -g 60 \
    -c:a aac -b:a 96k \
    "${duration_args[@]}" -f flv "${ATEM_RTMP_URL:-rtmp://127.0.0.1:1935/live/atem}"
