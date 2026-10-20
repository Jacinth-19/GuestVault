#!/usr/bin/env python3
"""Fail when a workflow can be silenced by a commit-message skip marker.

GitHub suppresses *every* workflow for a commit carrying ``[skip ci]`` (or one
of its aliases), and that includes a ``v*`` tag pushed onto that commit. The
release then never runs and nothing anywhere turns red -- the tag points at a
commit that produced no build at all.

That is exactly how ``v1.1.1`` was lost: the git tag landed on the media bot's
docs commit, which ends with ``[skip ci]``, so ``release.yml`` was skipped in
silence. The fix is not to remember; it is to make the marker impossible to
reintroduce.

Usage: check_workflow_hygiene.py [.github/workflows]

A line may opt out with a trailing ``# allow-skip-marker: <reason>`` comment,
which keeps the rare legitimate case (a deliberately inert workflow) explicit.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

# GitHub accepts all of these spellings, case-insensitively.
MARKERS = ("[skip ci]", "[ci skip]", "[no ci]", "[skip actions]", "[actions skip]")
ALLOW = "allow-skip-marker"

# Only the commit message a workflow *writes* can silence a later tag push on
# that commit. A `contains(github.event.head_commit.message, ...)` guard is a
# different thing and must not be flagged, so match the quoting a `git commit -m`
# or a commit message literal uses.
COMMIT_LINE = re.compile(r"(git\s+commit|commit-message|commit_message|message\s*:)", re.I)


def offending_lines(path: Path) -> list[tuple[int, str]]:
    hits: list[tuple[int, str]] = []
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        lowered = line.lower()
        if ALLOW in lowered:
            continue
        if not any(marker in lowered for marker in MARKERS):
            continue
        # A guard that *reads* a commit message is fine; only a message that is
        # produced (or hard-coded as an output) can suppress a run.
        if line.lstrip().startswith("#"):
            continue
        if COMMIT_LINE.search(line) or re.search(r"^\s*-\s*['\"]", line):
            hits.append((number, line.strip()))
    return hits


def main(argv: list[str]) -> int:
    root = Path(argv[1] if len(argv) > 1 else ".github/workflows")
    if not root.is_dir():
        print(f"::error::No workflow directory at {root}")
        return 1

    files = sorted(list(root.glob("*.yml")) + list(root.glob("*.yaml")))
    if not files:
        print(f"::error::No workflow files under {root}")
        return 1

    failures: list[str] = []
    for path in files:
        for number, line in offending_lines(path):
            failures.append(f"{path}:{number}: {line}")

    if failures:
        print("::error::A commit message in a workflow carries a CI-skip marker.")
        print("::error::A v* tag pushed onto such a commit produces no release run at all.")
        for item in failures:
            print(f"  {item}")
        print()
        print("Use a neutral marker (for example [media-bot]) plus a job-level")
        print("`if:` guard instead -- it stops the loop without silencing other runs.")
        return 1

    print(f"No CI-skip markers in {len(files)} workflow files.")
    for path in files:
        # Cheap proof the scan actually parsed something: every workflow that
        # commits must be visible to us.
        if "git commit" in path.read_text(encoding="utf-8"):
            print(f"  scanned commit path: {path.name}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
