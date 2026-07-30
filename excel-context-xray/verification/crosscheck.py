#!/usr/bin/env python3
"""Independently cross-check the UCI Online Retail II audit using XLSX XML only."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
import zipfile
from collections import defaultdict
from decimal import Decimal
from pathlib import Path


MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
PACKAGE_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
MERCHANDISE_CODE = re.compile(r"[0-9]{5}[A-Z]?\Z")


def qname(namespace: str, local_name: str) -> str:
    return f"{{{namespace}}}{local_name}"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def shared_strings(archive: zipfile.ZipFile) -> list[str]:
    values: list[str] = []
    with archive.open("xl/sharedStrings.xml") as source:
        for _, element in ET.iterparse(source, events=("end",)):
            if element.tag == qname(MAIN_NS, "si"):
                values.append("".join(node.text or "" for node in element.iter(qname(MAIN_NS, "t"))))
                element.clear()
    return values


def worksheets(archive: zipfile.ZipFile) -> list[tuple[str, str]]:
    relationships = ET.fromstring(archive.read("xl/_rels/workbook.xml.rels"))
    targets = {
        relationship.attrib["Id"]: relationship.attrib["Target"].lstrip("/")
        for relationship in relationships.findall(qname(PACKAGE_REL_NS, "Relationship"))
    }
    workbook = ET.fromstring(archive.read("xl/workbook.xml"))
    result: list[tuple[str, str]] = []
    for sheet in workbook.findall(f".//{qname(MAIN_NS, 'sheet')}"):
        target = targets[sheet.attrib[qname(REL_NS, "id")]]
        if not target.startswith("xl/"):
            target = f"xl/{target}"
        result.append((sheet.attrib["name"], target))
    return result


def column_index(cell_reference: str) -> int:
    letters = re.match(r"[A-Z]+", cell_reference)
    if letters is None:
        raise ValueError(f"Invalid cell reference: {cell_reference}")
    index = 0
    for character in letters.group(0):
        index = index * 26 + ord(character) - ord("A") + 1
    return index - 1


def cell_text(cell: ET.Element, strings: list[str]) -> str | None:
    cell_type = cell.attrib.get("t")
    if cell_type == "inlineStr":
        value = "".join(node.text or "" for node in cell.iter(qname(MAIN_NS, "t")))
        return value or None
    value_element = cell.find(qname(MAIN_NS, "v"))
    if value_element is None or value_element.text is None:
        return None
    if cell_type == "s":
        return strings[int(value_element.text)]
    return value_element.text


def normalized_identifier(value: str | None) -> str | None:
    if value is None or value == "":
        return None
    number = Decimal(value)
    if number == number.to_integral_value():
        return format(number.quantize(Decimal("1")), "f")
    return format(number.normalize(), "f")


def money(value: Decimal) -> str:
    return format(value.quantize(Decimal("0.01")), "f")


def crosscheck(workbook: Path) -> dict[str, object]:
    customer_revenue: dict[str, Decimal] = defaultdict(Decimal)
    customer_country: dict[str, str] = {}
    merchandise_returns: dict[str, Decimal] = defaultdict(Decimal)
    all_code_returns: dict[str, Decimal] = defaultdict(Decimal)
    product_description: dict[str, str] = {}

    data_rows = 0
    valid_rows = 0
    invalid_rows = 0
    missing_customer_rows = 0
    cancellation_rows = 0
    gross_sales = Decimal()
    returned_goods = Decimal()
    net_revenue = Decimal()

    with zipfile.ZipFile(workbook) as archive:
        strings = shared_strings(archive)
        sheet_names: list[str] = []
        for sheet_name, sheet_path in worksheets(archive):
            sheet_names.append(sheet_name)
            with archive.open(sheet_path) as source:
                for _, row in ET.iterparse(source, events=("end",)):
                    if row.tag != qname(MAIN_NS, "row"):
                        continue
                    row_number = int(row.attrib["r"])
                    if row_number == 1:
                        row.clear()
                        continue

                    values: list[str | None] = [None] * 8
                    for cell in row.findall(qname(MAIN_NS, "c")):
                        index = column_index(cell.attrib["r"])
                        if index < len(values):
                            values[index] = cell_text(cell, strings)

                    data_rows += 1
                    invoice, stock_code, description, quantity_text, _, price_text, customer_text, country = values
                    if not invoice or not stock_code or not quantity_text or not price_text or not country:
                        invalid_rows += 1
                        row.clear()
                        continue

                    valid_rows += 1
                    quantity = Decimal(quantity_text)
                    price = Decimal(price_text)
                    amount = quantity * price
                    net_revenue += amount
                    if quantity > 0 and price > 0:
                        gross_sales += amount
                    if quantity < 0 and price > 0:
                        returned = abs(amount)
                        returned_goods += returned
                        all_code_returns[stock_code] += returned
                        if MERCHANDISE_CODE.fullmatch(stock_code):
                            merchandise_returns[stock_code] += returned
                        if description:
                            product_description.setdefault(stock_code, description)
                    if invoice.upper().startswith("C"):
                        cancellation_rows += 1

                    customer_id = normalized_identifier(customer_text)
                    if customer_id is None:
                        missing_customer_rows += 1
                    elif country.casefold() != "united kingdom":
                        customer_revenue[customer_id] += amount
                        customer_country.setdefault(customer_id, country)
                    row.clear()

    top_customer = max(customer_revenue, key=customer_revenue.__getitem__)
    top_merchandise = max(merchandise_returns, key=merchandise_returns.__getitem__)
    naive_top_code = max(all_code_returns, key=all_code_returns.__getitem__)
    return {
        "workbookSha256": sha256(workbook),
        "sheets": sheet_names,
        "dataRows": data_rows,
        "validRows": valid_rows,
        "invalidRows": invalid_rows,
        "missingCustomerRows": missing_customer_rows,
        "cancellationRows": cancellation_rows,
        "grossSalesGbp": money(gross_sales),
        "returnedGoodsGbp": money(returned_goods),
        "netRevenueGbp": money(net_revenue),
        "topNonUkCustomer": {
            "customerId": top_customer,
            "country": customer_country[top_customer],
            "netRevenueGbp": money(customer_revenue[top_customer]),
        },
        "topReturnedMerchandise": {
            "stockCode": top_merchandise,
            "description": product_description[top_merchandise],
            "returnedValueGbp": money(merchandise_returns[top_merchandise]),
        },
        "naiveTopReturnedCode": {
            "stockCode": naive_top_code,
            "description": product_description[naive_top_code],
            "returnedValueGbp": money(all_code_returns[naive_top_code]),
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("workbook", type=Path)
    parser.add_argument("--expect", type=Path)
    arguments = parser.parse_args()

    actual = crosscheck(arguments.workbook)
    print(json.dumps(actual, indent=2))
    if arguments.expect is None:
        return 0

    expected = json.loads(arguments.expect.read_text(encoding="utf-8"))
    if actual != expected:
        print("Cross-check did not match the committed expectation.", file=sys.stderr)
        return 1
    print(f"Cross-check matched {arguments.expect}.", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
