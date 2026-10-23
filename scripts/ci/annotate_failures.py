#!/usr/bin/env python3
"""Turns failed plan steps into GitHub check annotations.

Job logs and artifacts are served from a storage host that is not reachable
from every environment, and a `push` run has no pull request to comment on. The
check-run annotations, however, come back through the ordinary REST API — so a
red "Manual Test Plan" gate can still name the exact step that failed instead of
saying "see the verdict comment" when there is no comment to see.

Reads `build/step-results.jsonl` (the merged verdict file) and prints one
`::error::` line per step that did not pass. Anything that did not pass is a
failure here, including a step with no verdict at all — the same rule
`scripts/ci/step_report.py` grades by.
"""

from __future__ import annotations

import json
import pathlib
import sys

RESULTS = pathlib.Path("build/step-results.jsonl")
MANIFEST = pathlib.Path("scripts/ci/step-plan.json")
MAX_ANNOTATIONS = 20


def expected_steps() -> list[tuple[str, str]]:
    """Every step the plan says must run, in plan order."""
    try:
        manifest = json.loads(MANIFEST.read_text())
    except (OSError, ValueError):
        return []
    steps: list[tuple[str, str]] = []
    for phase in manifest.get("passes", []):
        for step in phase.get("steps", []):
            if step.get("manual"):
                continue
            steps.append((str(step["id"]), str(step.get("expect", ""))))
    return steps


def verdicts() -> dict[str, dict]:
    seen: dict[str, dict] = {}
    if not RESULTS.exists():
        return seen
    for line in RESULTS.read_text().splitlines():
        line = line.strip()
        if not line.startswith("{"):
            continue
        try:
            record = json.loads(line)
        except ValueError:
            continue
        step_id = str(record.get("id", "")).strip()
        if not step_id:
            continue
        # A rerun of the same step appends a second line; the last one wins,
        # matching how step_report.py grades.
        seen[step_id] = record
    return seen


def main() -> int:
    seen = verdicts()
    plan = expected_steps()
    problems: list[tuple[str, str]] = []

    for step_id, expect in plan:
        record = seen.get(step_id)
        if record is None:
            problems.append((step_id, f"no verdict recorded — expected: {expect}"))
            continue
        status = str(record.get("status", "")).upper()
        if status != "PASS":
            note = str(record.get("note", "")).strip() or f"expected: {expect}"
            problems.append((step_id, note))

    if not problems:
        print("No failed steps to annotate.")
        return 0

    # Everything past the cap would be identical noise; the count still matters.
    for step_id, message in problems[:MAX_ANNOTATIONS]:
        safe = message.replace("\n", " ").replace("::", ":")
        print(f"::error title=Step {step_id} failed::{step_id} — {safe}")

    if len(problems) > MAX_ANNOTATIONS:
        print(f"::error title=More failures::…and {len(problems) - MAX_ANNOTATIONS} more.")

    failed = ", ".join(step_id for step_id, _ in problems)
    print(f"Failed or missing: {failed}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
