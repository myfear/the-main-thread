#!/usr/bin/env bash
set -euo pipefail
mkdir -p tokenizers
curl -fsSL -o tokenizers/qwen3-tokenizer.json \
  https://huggingface.co/Qwen/Qwen3-4B/resolve/main/tokenizer.json
