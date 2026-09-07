#!/usr/bin/env python3
"""Download the exact TAP projection used by the application. Requires Python 3."""

import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data"
query = (DATA / "query.sql").read_text().strip()
url = "https://exoplanetarchive.ipac.caltech.edu/TAP/sync?" + urlencode({"query": query, "format": "json"})
request = Request(url, headers={"Accept": "application/json", "User-Agent": "TheMainThread-ExoplanetTutorial/1.0"})
with urlopen(request, timeout=60) as response:
    planets = json.load(response)
fields = {"pl_name", "discoverymethod", "disc_year", "pl_orbper", "pl_orbperlim", "pl_rade", "pl_radelim"}
if not isinstance(planets, list) or not planets:
    raise SystemExit("Expected a non-empty JSON array; the existing snapshot is unchanged.")
if any(not isinstance(row, dict) or not fields.issubset(row) for row in planets):
    raise SystemExit("The archive response is missing required fields; the existing snapshot is unchanged.")
names = [row["pl_name"] for row in planets]
if any(not isinstance(name, str) or not name.strip() for name in names) or len(set(names)) != len(names):
    raise SystemExit("Expected one named row per planet; the existing snapshot is unchanged.")
snapshot = {"retrievedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            "query": query, "planets": planets}
target = DATA / "snapshot.json"
temporary = target.with_suffix(".json.tmp")
temporary.write_text(json.dumps(snapshot, indent=2, ensure_ascii=False) + "\n")
temporary.replace(target)
print(f"Saved {len(planets)} planets to {target}")
print(f"Retrieved: {snapshot['retrievedAt']}")
print(f"SHA-256: {hashlib.sha256(target.read_bytes()).hexdigest()}")
