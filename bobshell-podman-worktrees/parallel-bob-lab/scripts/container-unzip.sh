#!/bin/sh

set -eu

if [ "${1:-}" = "-q" ]; then
    shift
fi

archive="${1:-}"
shift

if [ "${1:-}" != "-d" ] || [ -z "${2:-}" ]; then
    echo "Usage: unzip [-q] <archive> -d <directory>" >&2
    exit 2
fi

destination="$2"
mkdir -p "${destination}"
cd "${destination}"
jar xf "${archive}"

for executable in "${destination}"/apache-maven-*/bin/*; do
    if [ -f "${executable}" ]; then
        chmod +x "${executable}"
    fi
done
