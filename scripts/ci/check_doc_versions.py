#!/usr/bin/env python3
"""Fails when the docs name a release file that no longer exists.

The Play runbook has drifted twice: 1.1.0 -> 1.5.0 -> 1.5.2, each time leaving
behind an upload instruction pointing at a bundle that was two releases old.
That is the worst kind of stale doc, because following it by hand uploads the
wrong file and Play rejects it on the versionCode.

So the version is read from `app/build.gradle.kts` — the one place it is
authoritative — and every `GuestVault-<version>.aab/.apk` in the docs has to
match it. Prose that mentions an older release as history ("since 1.5.0 the paid
build declares CAMERA") is left alone: only filenames are checked, because a
filename is a thing a reader will copy.

Usage:
    check_doc_versions.py            # check the tracked docs
    check_doc_versions.py --list     # show what it found
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
GRADLE = ROOT / "app" / "build.gradle.kts"

# GuestVault-1.5.2.aab, GuestVault-Pro-1.5.2.apk, GuestVault-1.5.2-debug.apk
FILE_RE = re.compile(r"GuestVault(?:-Pro)?-(?P<version>\d+\.\d+\.\d+)(?:-debug)?\.(?:aab|apk)")

DOC_GLOBS = ["docs/*.md", "README.md"]


def current_version() -> str:
    text = GRADLE.read_text(encoding="utf-8")
    match = re.search(r'^\s*versionName\s*=\s*"([^"]+)"', text, re.MULTILINE)
    if not match:
        raise SystemExit(f"{GRADLE}: no versionName found")
    return match.group(1)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--list", action="store_true", help="print every reference found")
    args = parser.parse_args()

    expected = current_version()
    seen = checked = 0
    stale: list[str] = []

    for pattern in DOC_GLOBS:
        for path in sorted(ROOT.glob(pattern)):
            rel = path.relative_to(ROOT)
            for number, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
                for match in FILE_RE.finditer(line):
                    seen += 1
                    version = match.group("version")
                    if args.list:
                        print(f"{rel}:{number}: {match.group(0)}")
                    if version != expected:
                        checked += 1
                        stale.append(f"{rel}:{number}: {match.group(0)} (app is {expected})")

    if stale:
        print(f"Release filenames in docs do not match versionName {expected}:", file=sys.stderr)
        for line in stale:
            print(f"  {line}", file=sys.stderr)
        print(
            "\nUpdate the docs to name the current bundle. If the release is imminent, "
            "bump app/build.gradle.kts instead — release.yml refuses a tag that disagrees "
            "with versionName.",
            file=sys.stderr,
        )
        return 1

    print(f"{seen} release filename(s) in docs, all naming {expected}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
