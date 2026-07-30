#!/usr/bin/env bash
set -euo pipefail

readonly DATASET_URL="https://archive.ics.uci.edu/static/public/502/online+retail+ii.zip"
readonly ZIP_SHA256="572e36277c2390fbfde10664750731e0a86f55e33470d91919085f0408e67bfb"
readonly XLSX_SHA256="bcbe73b35f5b7babf197fb0cb983a11f5d9ff929078d4aa53d171b1f2df2e980"
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
readonly DATA_DIR="${PROJECT_DIR}/data"
readonly ZIP_FILE="${DATA_DIR}/online-retail-ii.zip"
readonly XLSX_FILE="${DATA_DIR}/online_retail_II.xlsx"

sha256() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | cut -d' ' -f1
    else
        shasum -a 256 "$1" | cut -d' ' -f1
    fi
}

verify() {
    local expected="$1"
    local file="$2"
    local actual
    actual="$(sha256 "${file}")"
    if [[ "${actual}" != "${expected}" ]]; then
        echo "Checksum mismatch for ${file}" >&2
        echo "Expected: ${expected}" >&2
        echo "Actual:   ${actual}" >&2
        exit 1
    fi
}

mkdir -p "${DATA_DIR}"

if [[ -f "${XLSX_FILE}" ]]; then
    verify "${XLSX_SHA256}" "${XLSX_FILE}"
    echo "${XLSX_FILE}"
    exit 0
fi

curl --fail --location "${DATASET_URL}" --output "${ZIP_FILE}"
verify "${ZIP_SHA256}" "${ZIP_FILE}"
unzip -j -o "${ZIP_FILE}" online_retail_II.xlsx -d "${DATA_DIR}" >/dev/null
verify "${XLSX_SHA256}" "${XLSX_FILE}"

echo "${XLSX_FILE}"

