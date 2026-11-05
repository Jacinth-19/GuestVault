#!/usr/bin/env python3
"""Picks the emulator groups a run should execute.

`workflow_dispatch` can name a subset ("7,8" is the two paid-build groups), and
the workflow turns this script's output straight into `strategy.matrix.include`.

Why a job and a script rather than a condition on the matrix job: the `matrix`
context is **not** available in `jobs.<job_id>.if` — that condition is evaluated
before the matrix is expanded. Writing the filter there makes the whole file
invalid, which GitHub reports as a workflow that parses into zero jobs, so every
run "fails" in a second with no log to read.

The group table itself lives in `scripts/ci/plan-groups.json`, so this script,
the workflow matrix and the report all read one list.

Usage:
    select_groups.py --wanted all        # every group
    select_groups.py --wanted 7,8        # just the paid build
"""

from __future__ import annotations

import argparse
import json
import pathlib
import sys

DEFAULT_TABLE = pathlib.Path(__file__).with_name("plan-groups.json")


def wanted_groups(value: str, known: set[int]) -> list[int]:
    text = (value or "").strip().lower()
    if text in ("", "all"):
        return sorted(known)
    picked: set[int] = set()
    for piece in text.replace(" ", "").split(","):
        if not piece:
            continue
        if not piece.isdigit():
            raise SystemExit(
                f"--wanted: {piece!r} is not a group number; known groups: {sorted(known)}"
            )
        number = int(piece)
        if number not in known:
            raise SystemExit(f"--wanted: there is no group {number}; known groups: {sorted(known)}")
        picked.add(number)
    if not picked:
        raise SystemExit(f"--wanted: {value!r} named no groups")
    return sorted(picked)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--wanted", default="all", help='"all", or a comma list such as "7,8"')
    parser.add_argument("--table", type=pathlib.Path, default=DEFAULT_TABLE)
    args = parser.parse_args()

    table = json.loads(args.table.read_text(encoding="utf-8"))
    known = {int(entry["group"]) for entry in table}
    chosen = wanted_groups(args.wanted, known)
    include = [entry for entry in table if int(entry["group"]) in chosen]

    # One line, no newline inside the value: this is pasted into $GITHUB_OUTPUT.
    print("include=" + json.dumps(include, ensure_ascii=False, separators=(",", ":")))
    print(f"selected group(s) {chosen} of {sorted(known)}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
