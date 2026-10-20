#!/usr/bin/env python3
"""Fails the Play Readiness job when release lint reports errors.

The build file keeps `abortOnError = false` so a lint warning never blocks a
hotfix build; this script is the explicit gate that still refuses *errors*.

Usage: python3 scripts/ci/check_lint.py [path/to/lint-results-release.xml]
"""

from __future__ import annotations

import os
import sys
import xml.etree.ElementTree as ET

DEFAULT = "app/build/reports/lint-results-release.xml"


def main(path: str) -> int:
    if not os.path.exists(path):
        print(f"lint report not found at {path}; skipping the gate")
        return 0

    root = ET.parse(path).getroot()
    issues = root.findall("issue")
    errors = [i for i in issues if (i.get("severity") or "").lower() in ("error", "fatal")]

    print(f"lint: {len(issues)} issue(s), {len(errors)} error(s)")
    by_id: dict[str, int] = {}
    for issue in issues:
        issue_id = issue.get("id") or "unknown"
        by_id[issue_id] = by_id.get(issue_id, 0) + 1
    for issue_id, count in sorted(by_id.items(), key=lambda kv: (-kv[1], kv[0])):
        print(f"  {count:3d}x {issue_id}")
    for issue in errors:
        location = issue.find("location")
        where = ""
        if location is not None:
            where = f"{location.get('file')}:{location.get('line')}"
        print(f"  ERROR {issue.get('id')} — {issue.get('message')}")
        if where:
            print(f"        {where}")

    if errors:
        print("\nRelease lint reported errors. Fix them or add a justified suppression.")
        return 1

    print("Release lint: no errors.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else DEFAULT))
