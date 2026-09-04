#!/usr/bin/env python3
"""Inspect the graph facts behind the benchmark's failure analysis."""

from __future__ import annotations

import json
import sqlite3
import sys
from pathlib import Path
from typing import Any


FEATURE_FILE = (
    "dev/com.ibm.ws.kernel.feature.core/src/com/ibm/ws/kernel/feature/"
    "internal/FeatureManager.java"
)
ENV_CHECK_FILE = (
    "dev/com.ibm.ws.kernel.boot.cmdline/src/com/ibm/ws/kernel/boot/"
    "cmdline/EnvCheck.java"
)
REQUIRED_LINES = (1582, 1640, 1666, 1694, 1711)


def source_name(root: Path, relative_file: str, symbol: str) -> str:
    return f"{(root / relative_file).as_posix()}::{symbol}"


def dict_rows(rows: list[sqlite3.Row]) -> list[dict[str, Any]]:
    return [dict(row) for row in rows]


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: inspect_graph.py OPEN_LIBERTY_CHECKOUT")

    root = Path(sys.argv[1]).resolve()
    database = root / ".code-review-graph" / "graph.db"
    if not database.is_file():
        raise SystemExit(f"graph database not found: {database}")

    uri = f"{database.as_uri()}?mode=ro&immutable=1"
    connection = sqlite3.connect(uri, uri=True)
    connection.row_factory = sqlite3.Row
    try:
        feature_source = source_name(root, FEATURE_FILE, "FeatureManager.updateFeatures")
        feature_edges = connection.execute(
            """
            SELECT line, target_qualified, json_extract(extra, '$.receiver') AS receiver
            FROM edges
            WHERE source_qualified = ? AND kind = 'CALLS'
            ORDER BY line, target_qualified
            """,
            (feature_source,),
        ).fetchall()
        unique_targets = {row["target_qualified"] for row in feature_edges}

        env_source = source_name(root, ENV_CHECK_FILE, "EnvCheck.main")
        env_nodes = connection.execute(
            """
            SELECT line_start, line_end, params
            FROM nodes
            WHERE qualified_name = ?
            """,
            (env_source,),
        ).fetchall()
        env_targets = connection.execute(
            """
            SELECT DISTINCT target_qualified
            FROM edges
            WHERE source_qualified = ? AND kind = 'CALLS'
            """,
            (env_source,),
        ).fetchall()

        payload = {
            "feature_manager_update_features": {
                "call_edges": len(feature_edges),
                "distinct_callee_names": len(unique_targets),
                "qualified_unique_targets": sum("::" in value for value in unique_targets),
                "bare_unique_targets": sum("::" not in value for value in unique_targets),
                "required_edges": dict_rows(
                    [row for row in feature_edges if row["line"] in REQUIRED_LINES]
                ),
            },
            "env_check_main": {
                "node_count": len(env_nodes),
                "nodes": dict_rows(env_nodes),
                "distinct_callee_names": len(env_targets),
            },
        }
        print(json.dumps(payload, indent=2, sort_keys=True))
    finally:
        connection.close()


if __name__ == "__main__":
    main()
