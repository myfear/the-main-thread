# Online Retail II Audit Contract

Use this reference only for `data/online_retail_II.xlsx`.

## Source

- Dataset: UCI Machine Learning Repository, Online Retail II
- Landing page: https://archive.ics.uci.edu/dataset/502/online%2Bretail%2Bii
- DOI: https://doi.org/10.24432/C5CG6D
- License: Creative Commons Attribution 4.0 International
- Expected workbook SHA-256: `bcbe73b35f5b7babf197fb0cb983a11f5d9ff929078d4aa53d171b1f2df2e980`

## Workbook Schema

Both year sheets use:

1. `Invoice`
2. `StockCode`
3. `Description`
4. `Quantity`
5. `InvoiceDate`
6. `Price`
7. `Customer ID`
8. `Country`

## Audit Question

Across both workbook years:

1. Which customer outside the United Kingdom produced the most net revenue?
2. Which merchandise product produced the largest value of returned goods?
3. Why does a naive ranking of all stock codes return the wrong product?

## Calculation Rules

- Line amount is `Quantity * Price`.
- Net customer revenue includes positive and negative line amounts.
- Rows without `Customer ID` contribute to workbook totals but not to the customer ranking.
- Returned-goods value includes rows with negative `Quantity` and positive `Price`, using the absolute line amount.
- A merchandise stock code matches `[0-9]{5}[A-Z]?`.
- Codes outside that shape can represent adjustments, postage, discounts, or other non-merchandise lines. Report the naive winner before excluding it.
- Rank customers only where `Country` is not `United Kingdom`.
- Use both sheets. Do not silently analyze only the first year.

## Evidence Contract

Return no more than three high-value transaction rows for each winner. Each evidence item must include the sheet, exact `A:H` row range, invoice, stock code, description, quantity, price, line amount, and invoice date.

