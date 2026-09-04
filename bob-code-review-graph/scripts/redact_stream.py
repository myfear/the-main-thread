#!/usr/bin/env python3
"""Redact the Bob API key from a streamed BobShell transcript."""

from __future__ import annotations

import os
import sys


secret = os.environ.get("BOB_REDACTION_SECRET", "")
for line in sys.stdin:
    sys.stdout.write(line.replace(secret, "[REDACTED_BOB_API_KEY]") if secret else line)

