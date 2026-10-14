# Returns scenario contract

The generated fixture comes from the earlier Excel Context X-Ray article.
`Transactions!D2:D4` contains 10, 20, 50. `Dashboard!B3` stores the formula
`SUM('Transactions'!D2:D4)` with an intentionally stale cached value of 60.
The current input cells total 80. The source also includes one hidden sheet,
one name, and one image; the Java preparation verifies those features.

Create one sheet called `Report` with these rows:

- `A1:C1`: Item, Baseline, Scenario.
- `A2:C2`: First return, 10, 10.
- `A3:C3`: Second return, 20, 20.
- `A4:C4`: Third return, 50, 70.
- `A5:C5`: Total, `=SUM(B2:B4)`, `=SUM(C2:C4)`.
- Row 6: blank.
- `A7`: Difference; `C7`: `=C5-B5`.
- `A8`: Source stored total; `B8`: 60.
- `A9`: Source recalculated total; `B9`: 80.
- `A10`: Source range; `B10`: `Transactions!D2:D4`.
- `A11`: Source SHA-256; `B11`: the exact hash from `build/report.jsonl`.

Format `A1:C1` and `A5:C5` in bold. Use the custom number format
`£#,##0.00` on `B2:C9`, set columns A:C to 26 characters, and freeze the first
row. Wrap the hash in `B11` and set row 11 to 54 points. The chart reads
`A1:C4`, uses type `column`, has two series, and is
titled `Return scenario`. Place it at E2.

For `home.columnWidth`, use `{"cols":"A:C","chars":26}`. Its `width`
parameter is measured in points and would make these columns too narrow.
For `home.rowHeight`, use `{"rows":"11:11","height":54}`; the `rows`
parameter requires a range. The source hash proves file identity; the
POI cell read, not the hash, establishes the source's stored value of 60.
GridCraft measures row height in screen points; 54 is stored as 40.5
typographic points in XLSX. These are different units.

£70 is a hypothetical replacement for the third return. Do not write it
back into the source or label the £100 total as an observed source result.
No external workbook links are needed: copy the approved inputs into the
report, and use formulas within that report.
