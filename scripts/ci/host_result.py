#!/usr/bin/env python3
"""Records a verdict for a step that only the host can grade.

Some steps destroy the thing that would report them: 14.6 uninstalls the app,
which kills the instrumentation process. The runner grades those from the
outside and appends the verdict here in the same JSONL format the device uses.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True)
    parser.add_argument("--id", required=True)
    parser.add_argument("--status", required=True, choices=["PASS", "FAIL", "SKIP"])
    parser.add_argument("--note", default="")
    args = parser.parse_args()

    entry = {
        "id": args.id,
        "expect": "",
        "status": args.status,
        "note": args.note.replace("\n", " ")[:600],
        "ms": 0,
    }
    with Path(args.out).open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(entry) + "\n")
    print(f"{args.id}: {args.status} {args.note}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
