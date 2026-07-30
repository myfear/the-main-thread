#!/usr/bin/env bash
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
readonly BUILD_DIR="${PROJECT_DIR}/build"
readonly FIXTURE="${BUILD_DIR}/visual-fixture.xlsx"
readonly EXTRACTED_IMAGE="${BUILD_DIR}/extracted-evidence.png"
readonly DEFAULT_WORKBOOK="${PROJECT_DIR}/data/online_retail_II.xlsx"
readonly EXPECTED_AUDIT="${PROJECT_DIR}/verification/expected-audit.json"
readonly PYTHON_BIN="${PYTHON_BIN:-python3}"

mkdir -p "${BUILD_DIR}"

jbang --offline "${SCRIPT_DIR}/CreateFixture.java" "${FIXTURE}" >/dev/null

inventory_json="$(
    jbang --offline "${SCRIPT_DIR}/ExcelXray.java" inventory "${FIXTURE}" \
        --sample-rows 2 \
        --max-output-chars 30000
)"
jq -e '
    .summary.sheetCount == 3
    and .summary.formulaCellCount == 1
    and .summary.imageCount == 1
    and any(.sheets[]; .name == "Rules" and .visibility == "hidden")
' <<<"${inventory_json}" >/dev/null

slice_json="$(
    jbang --offline "${SCRIPT_DIR}/ExcelXray.java" slice "${FIXTURE}" \
        --sheet Dashboard \
        --range A1:D5 \
        --include-formulas
)"
jq -e '
    any(
        .rows[].cells[];
        .cell == "B3"
        and .value == "£60.00"
        and .formula == "SUM('\''Transactions'\''!D2:D4)"
    )
' <<<"${slice_json}" >/dev/null

image_json="$(jbang --offline "${SCRIPT_DIR}/ExcelXray.java" images "${FIXTURE}")"
jq -e '
    .imageCount == 1
    and .images[0].sheet == "Dashboard"
    and .images[0].contentType == "image/png"
' <<<"${image_json}" >/dev/null

jbang --offline "${SCRIPT_DIR}/ExcelXray.java" extract-image "${FIXTURE}" \
    --index 1 \
    --output "${EXTRACTED_IMAGE}" >/dev/null
test -s "${EXTRACTED_IMAGE}"

workbook="${1:-${DEFAULT_WORKBOOK}}"
if [[ ! -f "${workbook}" ]]; then
    echo "Fixture checks passed."
    echo "Run ./scripts/download-data.sh, then rerun this script for the full dataset audit."
    exit 0
fi

"${PYTHON_BIN}" "${PROJECT_DIR}/verification/crosscheck.py" \
    "${workbook}" \
    --expect "${EXPECTED_AUDIT}" >/dev/null

audit_json="$(
    jbang --offline "${SCRIPT_DIR}/ExcelXray.java" audit-retail "${workbook}" \
        --evidence-lines 3 \
        --max-output-chars 12000
)"
jq -e '
    .scan.dataRows == 1067371
    and .answer.topNonUkCustomer.customerId == "14646"
    and .answer.topNonUkCustomer.netRevenueGbp == 523342.07
    and .answer.largestReturnedProduct.stockCode == "23843"
    and .answer.largestReturnedProduct.returnedValueGbp == 168469.60
    and .answer.dataQualityTrap.naiveLargestReturnCode == "M"
    and .contextBudget.rowsReturnedAsEvidence == 4
' <<<"${audit_json}" >/dev/null

echo "Fixture and full-workbook checks passed."
