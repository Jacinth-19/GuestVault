#!/usr/bin/env python3
"""Grade the numbered manual test plan, one verdict per step.

The plan lives in ``scripts/ci/step-plan.json``: every numbered step carries the
verbatim "Expect" text from the plan, which is the acceptance criterion. Device
results come from ``step-results.jsonl`` (written by the instrumented tests and
pulled with ``run-as``), so a step that never ran is visibly *not run* instead
of silently green.

Usage:
  step_report.py --manifest M --results R --out step-report.md
  step_report.py --manifest M --list-tests

Exit code is 1 when a step that should have run on the device is missing or
failed. Steps marked ``"mode": "manual"`` (hardware, wall-clock, real
fingerprint) are reported as not-automated and never fail the build.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

PASS = "PASS"
FAIL = "FAIL"
SKIP = "SKIP"

BADGE = {
    PASS: "✅ pass",
    FAIL: "❌ failed",
    SKIP: "⚠️ not automated",
    "MISSING": "❌ not run",
}


def load_manifest(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def load_results(path: Path) -> dict[str, dict]:
    results: dict[str, dict] = {}
    if not path.exists():
        return results
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = line.strip().lstrip("\ufeff")
        if not line.startswith("{"):
            continue
        try:
            entry = json.loads(line)
        except json.JSONDecodeError:
            continue
        step_id = str(entry.get("id", "")).strip()
        if step_id:
            # A rerun supersedes the earlier verdict for the same step.
            results[step_id] = entry
    return results


def cell(text: str) -> str:
    return str(text).replace("|", "\\|").replace("\n", " ").strip()


def iter_steps(manifest: dict):
    for order, block in enumerate(manifest.get("passes", []), start=1):
        yield order, block, block.get("steps", [])


def list_tests(manifest: dict) -> int:
    """Every instrumented phase, in manifest order.

    Derived from `run`, not from `steps`: a phase may exist only to set up the
    next one (pass10a creates the PIN that pass10b then needs).
    """
    for block in manifest.get("passes", []):
        for entry in block.get("run", []):
            if "test" in entry:
                print(entry["test"])
    return 0


def build_report(manifest: dict, results: dict[str, dict]) -> tuple[str, dict[str, int]]:
    counts = {PASS: 0, FAIL: 0, SKIP: 0, "MISSING": 0}
    lines: list[str] = []
    lines.append("# GuestVault — manual test plan verdicts\n")
    lines.append(
        "Every numbered step below is graded against the **Expect** text you supplied. "
        "A step is only green when the device produced a PASS for that exact ID.\n"
    )

    for order, block, steps in iter_steps(manifest):
        number = block.get("pass", order)
        title = block.get("title", f"Pass {number}")
        lines.append(f"\n## Pass {number} — {title}\n")
        lines.append("| Step | Action | Expect (acceptance criterion) | Verdict | Evidence |")
        lines.append("| --- | --- | --- | --- | --- |")

        for step in steps:
            step_id = str(step.get("id", "?"))
            mode = step.get("mode", "device")
            result = results.get(step_id)

            if result is not None:
                status = result.get("status", FAIL)
                if status not in counts:
                    status = FAIL
                note = result.get("note", "")
            elif mode == "manual":
                status = SKIP
                note = step.get("why", "needs hardware or wall-clock timing")
            else:
                status = "MISSING"
                note = "the instrumented run produced no verdict for this step"

            counts[status] += 1
            lines.append(
                "| **{id}** | {action} | {expect} | {badge} | {note} |".format(
                    id=cell(step_id),
                    action=cell(step.get("action", "")),
                    expect=cell(step.get("expect", "")),
                    badge=BADGE.get(status, status),
                    note=cell(note)[:300],
                )
            )

    total = sum(counts.values())
    graded = counts[PASS] + counts[FAIL] + counts["MISSING"]
    lines.append("\n## Summary\n")
    lines.append(
        f"- **{counts[PASS]} of {graded}** executable steps passed"
        + (f", **{counts[FAIL]} failed**." if counts[FAIL] else ".")
    )
    if counts["MISSING"]:
        lines.append(f"- **{counts['MISSING']} step(s) never ran** — treated as failures.")
    if counts[SKIP]:
        lines.append(
            f"- **{counts[SKIP]} step(s)** need physical hardware or wall-clock timing "
            "and are marked not-automated, with the reason."
        )
    lines.append(f"- {total} steps in the plan.\n")
    return "\n".join(lines) + "\n", counts


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--results")
    parser.add_argument("--out", default="step-report.md")
    parser.add_argument("--list-tests", action="store_true")
    args = parser.parse_args(argv[1:])

    manifest = load_manifest(Path(args.manifest))
    if args.list_tests:
        return list_tests(manifest)

    if not args.results:
        parser.error("--results is required unless --list-tests is used")

    results = load_results(Path(args.results))
    report, counts = build_report(manifest, results)

    out = Path(args.out)
    out.write_text(report, encoding="utf-8")
    print(report)

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as handle:
            handle.write(report)

    failed = counts[FAIL] + counts["MISSING"]
    if failed:
        print(f"::error::{failed} plan step(s) failed or never ran")
        return 1
    print(f"All {counts[PASS]} executable plan steps passed")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
