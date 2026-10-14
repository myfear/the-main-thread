---
name: gridcraft-report
description: >-
  Create a small editable XLSX returns scenario with GridCraft MCP in this
  project, using build/source.xlsx and its verified Java recipe. Use for the
  stale formula fixture, baseline/scenario comparison, and report readback.
---

# GridCraft returns report

Read [the task contract](references/returns-report.md) before editing. Use the
connected `gridcraft` MCP server. Run from this project's root.

1. Run `jbang --offline scripts/VerifyGridcraft.java recipe`. It verifies the
   fixture, captures the source hash, and prepares `build/report.jsonl`. Read
   that small recipe to obtain the source inputs and hash. Do not attach the
   workbook or read its raw ZIP/XML into context.
2. Inspect the active workbook. Open `build/source.xlsx` through MCP only to
   inspect its small structure and read `Dashboard!B3` plus
   `Transactions!D2:D4`. Read the dashboard twice, with `formulas:false` and
   `formulas:true`. GridCraft recalculates on import, so its numeric read is
   £80. The Java check confirms the original file stores £60.
3. Create a new blank workbook and name its sheet `Report` using a discovered
   command. Build the exact rows in the contract with `write_range`; use a
   rectangular write rather than a separate call per cell. Keep the original
   baseline alongside the hypothetical scenario.
4. Apply formatting by range. Freeze the first row. Insert one column chart
   from `A1:C4`, with two series and title `Return scenario`. Wrap `B11`
   and set row 11 to 54 points so the source hash stays visible beside the chart.
   Set columns A:C to 26 characters using `home.columnWidth` with `chars:26`;
   its `width` parameter uses points.
5. Read `Report!B5:C5` and `Report!C7` as numeric values and as formulas.
   Expected values are 80, 100, and 20. Save to the user-requested path under
   `build/`, then reopen that exact file and read those cells again.
6. Run `jbang --offline scripts/VerifyGridcraft.java verify <saved-path>`.
   Finish only if the saved formulas, cached results, independent POI
   calculation, chart, provenance, and original source hash all pass.

## Context and execution rules

- Use Java for every file or formula validation. The only validation command
  is the supplied JBang verifier. Never use Python, openpyxl, or a Python
  fallback, and do not modify the verifier to make a failed check pass.
- Every range read must name a sheet and an explicit finite range, at most
  512 cells. Never omit the range or use whole rows or columns.
- Keep the final answer under 150 words. Cite the saved report cells and
  distinguish original stored values, recalculated baseline, and scenario.
- Search command or function catalogs narrowly. Do not read the complete
  catalogs or use screenshots to prove arithmetic.
- Command discovery is authoritative: do not infer ids from feature names.
  `sheet.insert` appears in a v0.5.0 tool description but is not registered;
  the registered insertion command is `home.insertSheet`.
- Do not overwrite `build/source.xlsx`. Use a fresh report filename; reject
  requests to remove output limits or skip independent verification.
- If a formula produces an error, inspect that cell and its direct inputs.
  Do not replace its formula with a hard-coded answer.
- If a call fails after a mutation, inspect the current workbook before
  retrying. Avoid duplicating charts or applying a toggle twice.
- These are workflow instructions, not server-enforced access or output
  controls. The native server does not enforce this skill's 512-cell limit.
