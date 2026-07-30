# Excel Context X-Ray

This demo gives IBM Bob a repo-local skill that analyzes a 44 MB Excel workbook without putting the binary—or a full text dump—into the agent context.

It uses:

- JBang to run Java source files directly
- Apache POI's event model to stream `.xlsx` sheet XML
- A bounded JSON interface for workbook inventory, domain aggregation, cell slices, and images
- UCI Online Retail II as the full-size dataset
- A generated workbook fixture for formula-cache, hidden-sheet, and embedded-image checks

## Prerequisites

- Java 21 or newer
- [JBang](https://www.jbang.dev/)
- `curl`, `unzip`, and `jq`
- Python 3.11 or newer for the independent XML-only cross-check
- IBM Bob for the skill walkthrough

## Run It

Download and verify the dataset:

```bash
cd excel-context-xray
./scripts/download-data.sh
```

Create a bounded workbook inventory:

```bash
jbang scripts/ExcelXray.java inventory data/online_retail_II.xlsx \
  --sample-rows 0 \
  --max-output-chars 12000
```

Run the retail audit:

```bash
jbang --offline scripts/ExcelXray.java audit-retail data/online_retail_II.xlsx \
  --evidence-lines 3 \
  --max-output-chars 12000
```

Generate and inspect the complex-workbook fixture:

```bash
jbang --offline scripts/CreateFixture.java
jbang --offline scripts/ExcelXray.java inventory build/visual-fixture.xlsx
jbang --offline scripts/ExcelXray.java slice build/visual-fixture.xlsx \
  --sheet Dashboard \
  --range A1:D5 \
  --include-formulas
```

Run all repeatable checks:

```bash
./scripts/verify.sh
```

## What The Audit Proves

Across 1,067,371 transaction rows, the POI analyzer and an independent standard-library XML parser agree on the winners and totals.

The useful trap is the returns ranking. Ranking every stock code names `M` / `Manual`, an adjustment code, as the largest returned “product.” Applying the documented merchandise rule—five digits followed by an optional letter—identifies stock code `23843`, `PAPER CRAFT , LITTLE BIRDIE`, instead.

## Project Layout

```text
.bob/skills/excel-xray/       IBM Bob workflow and retail audit contract
scripts/ExcelXray.java        Streaming analyzer and bounded commands
scripts/CreateFixture.java    Generated formula/image/hidden-sheet workbook
scripts/download-data.sh      Source download with pinned checksums
scripts/verify.sh             Fixture and full-dataset checks
verification/crosscheck.py    Independent XLSX XML audit
verification/expected-audit.json
article.md                    The Main Thread tutorial
```

The downloaded workbook and generated build artifacts are ignored by Git.
