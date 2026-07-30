# Dataset

This demo uses [Online Retail II](https://archive.ics.uci.edu/dataset/502/online%2Bretail%2Bii) from the UCI Machine Learning Repository.

- DOI: [10.24432/C5CG6D](https://doi.org/10.24432/C5CG6D)
- License: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)
- Downloaded archive SHA-256: `572e36277c2390fbfde10664750731e0a86f55e33470d91919085f0408e67bfb`
- Extracted workbook SHA-256: `bcbe73b35f5b7babf197fb0cb983a11f5d9ff929078d4aa53d171b1f2df2e980`
- Workbook file size: 45,622,278 bytes
- Workbook rows excluding headers: 1,067,371

The workbook is not committed. Run `./scripts/download-data.sh` to fetch it and verify both checksums.

The source describes two years of transactions from a UK-based non-store online retailer. The workbook has two sheets with invoice, stock code, description, quantity, invoice date, price, customer ID, and country fields. It contains missing customer IDs and non-merchandise stock codes, which makes it useful for a data-quality audit rather than a simple row-count demo.

