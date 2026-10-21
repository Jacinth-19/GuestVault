#!/usr/bin/env python3
"""Flattens a pass's `run` list into a TSV the shell runner can loop over.

Columns: type, name, clear, grant, value

  test	pass07a	1	1
  host	force-stop
  test	pass07b	0	0
"""

from __future__ import annotations

import json
import sys
from pathlib import Path


def main(argv: list[str]) -> int:
    manifest = json.loads(Path(argv[1]).read_text(encoding="utf-8"))
    for block in manifest.get("passes", []):
        for entry in block.get("run", []):
            if "test" in entry:
                print(
                    "\t".join(
                        [
                            "test",
                            entry["test"],
                            "1" if entry.get("clear") else "0",
                            "1" if entry.get("grant", True) else "0",
                            "",
                        ]
                    )
                )
            elif "host" in entry:
                print(
                    "\t".join(
                        [
                            "host",
                            entry["host"],
                            "",
                            "1" if entry.get("grant", True) else "0",
                            str(entry.get("value", "")),
                        ]
                    )
                )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
