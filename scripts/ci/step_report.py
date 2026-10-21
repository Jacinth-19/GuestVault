#!/usr/bin/env python3
"""Turn the numbered manual plan into a per-step pass/fail table.

The plan is data (`scripts/ci/step-plan.json`), not prose: every numbered row
carries the *action* the tester performs and the *expected* result, so the
verdict can never drift away from what was asked for.

The device writes one JSON line per executed check to its own files dir:

    {"id": "1.1", "status": "pass", "expect": "...", "note": "...", "ms": 812}

This script joins those results with the manifest and renders a markdown
table. A step that the manifest describes but no test reported is a failure
("not run") — silence is never allowed to look like success.

Usage:
    step_report.py --manifest scripts/ci/step-plan.json \
                   --results build/step-results.jsonl \
                   --out step-report.md
    step_report.py --manifest scripts/ci/step-plan.json --list-tests
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

PASS = "pass"
FAIL = "fail"
SKIP = "skip"

STATUS_ICON = {
    PASS: "✅ pass",
    FAIL: "❌ failed",
    SKIP: "⚠️ not automated",
}


def load_manifest(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    if "passes" not in data or not data["passes"]:
        raise SystemExit(f"{path}: no passes[] found")
    return data


def load_results(path: Path) -> dict[str, dict]:
    """Last result wins, so a retried step reports its final outcome."""
    results: dict[str, dict] = {}
    if not path.exists():
        return results
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = line.strip()
        if not line.startswith("{"):
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError:
            continue
        step_id = str(row.get("id", "")).strip()
        if step_id:
            results[step_id] = row
    return results


def cell(text: str) -> str:
    text = " ".join(str(text or "").split())
    return text.replace("|", "\\|")


def verdict(step: dict, result: dict | None) -> tuple[str, str]:
    manual = step.get("mode") == "manual"
    if result is None:
        if manual:
            return SKIP, step.get("why", "needs a physical device")
        return FAIL, "the test never reported a result for this step"

    status = str(result.get("status", "")).lower()
    note = str(result.get("note", "")).strip()
    if status == PASS:
        return PASS, note
    if status == SKIP:
        return SKIP, note or "the test skipped this step on this device"
    return FAIL, note or "failed"


def render(manifest: dict, results: dict[str, dict]) -> tuple[str, int, int]:
    lines: list[str] = []
    total = passed = failed = skipped = 0
    groups: list[str] = []

    for block in manifest["passes"]:
        pid = str(block.get("pass", block.get("id", ""))).strip()
        title = block.get("title", "")
        rows = block.get("steps", [])
        counts = {PASS: 0, FAIL: 0, SKIP: 0}
        body: list[str] = []

        for step in rows:
            step_id = str(step.get("id", "")).strip()
            state, note = verdict(step, results.get(step_id))
            counts[state] += 1
            total += 1
            body.append(
                "| {id} | {action} | {expect} | {icon} | {note} |".format(
                    id=cell(step_id),
                    action=cell(step.get("action", "")),
                    expect=cell(step.get("expect", "")),
                    icon=STATUS_ICON[state],
                    note=cell(note),
                )
            )

        passed += counts[PASS]
        failed += counts[FAIL]
        skipped += counts[SKIP]
        mark = "✅" if counts[FAIL] == 0 else "❌"
        groups.append(
            f"- {mark} **Pass {pid} — {title}**: "
            f"{counts[PASS]} passed, {counts[FAIL]} failed, {counts[SKIP]} not automated"
        )
        lines.append("")
        lines.append(f"### Pass {pid} — {title}")
        lines.append("")
        lines.append("| Step | Action | Expected | Result | Evidence / note |")
        lines.append("| --- | --- | --- | --- | --- |")
        lines.extend(body)

    executable = total - skipped
    header = [
        "## Summary",
        "",
        f"- **{passed} of {executable}** executable steps passed, **{failed} failed**, "
        f"**{skipped} not automated** (of {total} plan steps).",
        "- A step with no recorded result counts as failed: the harness did not "
        "verify it, so it is not allowed to look green.",
        "",
    ]
    header.extend(groups)
    return "\n".join(header + lines) + "\n", failed, skipped


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--manifest", required=True, type=Path)
    ap.add_argument("--results", type=Path, default=Path("build/step-results.jsonl"))
    ap.add_argument("--out", type=Path, default=Path("step-report.md"))
    ap.add_argument("--phase-log", type=Path, default=None, help="per-phase table from the device")
    ap.add_argument("--list-tests", action="store_true", help="print test methods to run, one per line")
    args = ap.parse_args()

    manifest = load_manifest(args.manifest)

    if args.list_tests:
        for block in manifest["passes"]:
            test = block.get("test")
            if test:
                print(test)
        return 0

    results = load_results(args.results)
    markdown, failed, skipped = render(manifest, results)

    # The phase table is raw evidence: which instrumentation phase ran, how it
    # exited, and which window had focus when it finished.
    if args.phase_log and args.phase_log.exists():
        phases = args.phase_log.read_text(encoding="utf-8", errors="replace").strip()
        if phases:
            markdown += "\n## Device phases\n\n" + phases + "\n"

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(markdown, encoding="utf-8")
    print(markdown)

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as handle:
            handle.write(markdown)

    if failed:
        print(f"\n{failed} step(s) failed or never ran.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
