#!/usr/bin/env bash
set -euo pipefail

: "${HF_TOKEN:?Set HF_TOKEN after accepting the Shieldstral model terms on Hugging Face}"

podman run --rm \
    --device nvidia.com/gpu=all \
    --publish 8000:8000 \
    --env HUGGING_FACE_HUB_TOKEN="${HF_TOKEN}" \
    docker.io/vllm/vllm-openai:v0.26.0 \
    --model mistralai/Shieldstral-1.0-3B \
    --max-model-len 32768
