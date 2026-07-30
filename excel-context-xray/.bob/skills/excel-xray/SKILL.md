---
name: excel-xray
description: >
  Inspect large or complex XLSX workbooks with this repository's JBang and
  Apache POI analyzer before answering. Use for workbook structure, formulas,
  bounded cell evidence, the UCI Online Retail II audit, or embedded images
  when the binary must stay outside the agent context.
---

# Excel X-Ray

Keep the workbook on disk. Bring only a bounded inventory, aggregate, cell range, or selected image into context.

## Required Workflow

1. Confirm the input is an `.xlsx` file inside the workspace. In the command patterns below, replace `$WORKBOOK` with that exact path before running the command; do not run with an unset variable. Do not modify the input, encode it as text, or dump its ZIP/XML contents into the conversation.
2. From the repository root, inventory it before running any analysis command:

   ```bash
   jbang --offline scripts/ExcelXray.java inventory "$WORKBOOK" \
     --sample-rows 0 \
     --max-output-chars 12000
   ```

3. If the column schema is unknown, use `slice` for the header and at most one representative data row after the inventory has identified the sheet and dimensions. Do not dump sample rows from every sheet by default.
4. Turn the user's request into one narrow question. State the metric, filters, grouping, and treatment of missing values before calculating.
5. Use the smallest command that can answer it:
   - For the tutorial's retail question, read [the retail audit contract](references/retail-audit.md), then run `audit-retail`.
   - For exact cell evidence, use `slice` with one sheet and one explicit range.
   - For visuals, run `images` first. Use `extract-image` for one selected index only.
6. If a command reaches `--max-output-chars`, reduce the sample or range. Do not remove the cap just to make the command succeed.
7. Cite workbook evidence as `Sheet!A1:H20` ranges. Keep conclusions separate from assumptions and data-quality rules.

For `data/online_retail_II.xlsx`, the required order is exact:

1. Run the zero-sample `inventory` command above.
2. Read [the retail audit contract](references/retail-audit.md).
3. Run `audit-retail`.

Do not skip or reorder these steps even when the user prompt only asks for the answer.

## Command Patterns

```bash
jbang --offline scripts/ExcelXray.java audit-retail "$WORKBOOK" \
  --evidence-lines 3 \
  --max-output-chars 12000
```

```bash
jbang --offline scripts/ExcelXray.java slice "$WORKBOOK" \
  --sheet "Year 2010-2011" \
  --range "A540424:H540424" \
  --include-formulas \
  --max-output-chars 12000
```

```bash
jbang --offline scripts/ExcelXray.java images "$WORKBOOK"
jbang --offline scripts/ExcelXray.java extract-image "$WORKBOOK" \
  --index 1 \
  --output build/selected-image.png
```

## Accuracy Rules

- Treat formula results as cached workbook values unless a spreadsheet engine has recalculated them. Return both formula and cached value when they matter.
- Call a number a character count when the tool reports characters. Do not convert it to tokens without a named tokenizer and model.
- The analyzer reads the workbook from disk. Say that the binary stayed outside the model context; do not say that the workbook was never read.
- Count inventory samples, slices, audit evidence, and extracted images when describing what entered the model context. A zero-sample inventory does not add cell rows.
- Do not call every stock code a product. Apply documented business rules before ranking.
- Do not hide excluded or missing rows. Report the applied policy.
- Do not speculate about the identities or business meaning behind missing values.
- Do not claim that a workbook image proves a numeric result unless its visible content agrees with cell-level evidence.

## Final Answer Shape

Keep the final answer under 250 words unless the user asks for more detail. Use short paragraphs or bullets, not tables. Do not repeat the complete JSON response, scan statistics, cancellation counts, or unrelated workbook totals.

Return:

1. The direct answer.
2. The calculation scope and business rules.
3. A small set of sheet-and-range evidence.
4. Any formula-cache, missing-data, image, or file-format limitation that could change the conclusion.

For the retail audit, finish with one accurate context statement: the zero-sample inventory contributed workbook metadata but no cell rows; the audit contributed its returned evidence rows and aggregates; the analyzer read the binary from disk, but the binary did not enter model context.
