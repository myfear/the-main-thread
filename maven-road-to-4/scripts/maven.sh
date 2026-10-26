#!/bin/sh
set -eu

lab_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
exec "$lab_root/scripts/lab.sh" maven "$@"
