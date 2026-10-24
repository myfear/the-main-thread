#!/bin/sh
set -eu
podman stop --ignore main-thread-rag-docling main-thread-rag-pgvector
