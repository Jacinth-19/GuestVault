#!/usr/bin/env python3
"""Collects every group's plan verdicts into one results file.

Two transports, in this order, because either one alone has failed:

  1. **Commit comments** — posted by `post_verdicts.py` at the end of each group
     job. Plain REST API, no blob storage involved.
  2. **Artifacts** — `merged/step-verdicts-<g>/build/...`, kept so a run whose
     comments failed still grades, and so the logs stay downloadable.

A group present in neither is reported by name and fails the step: an
infrastructure problem has to say so, instead of letting the merge quietly skip
a pass and grade its steps as "never reported".

Usage: python3 scripts/ci/fetch_verdicts.py --manifest scripts/ci/step-plan.json
Writes build/step-results.jsonl and build/phase-log.md.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import pathlib
import sys
import time
import urllib.request

MARKER_PREFIX = "<!-- plan-verdicts:"
PHASE_HEADER = (
    "| Phase | Exit | Device | Foreground window | Crash / error |\n"
    "| --- | --- | --- | --- | --- |\n"
)


def groups_in(manifest: str) -> list[int]:
    data = json.loads(pathlib.Path(manifest).read_text())
    return sorted({int(p["group"]) for p in data["passes"] if "group" in p})


def fetch_comments(repo: str, sha: str, token: str) -> list[dict]:
    """Every comment body on this commit, following pagination."""
    bodies: list[dict] = []
    page = 1
    while page <= 10:
        url = (
            f"https://api.github.com/repos/{repo}/commits/{sha}/comments"
            f"?per_page=100&page={page}"
        )
        request = urllib.request.Request(
            url,
            headers={
                "Authorization": f"Bearer {token}",
                "Accept": "application/vnd.github+json",
            },
        )
        batch = None
        for attempt in range(1, 4):
            try:
                with urllib.request.urlopen(request, timeout=30) as response:
                    batch = json.loads(response.read().decode())
                break
            except Exception as error:  # noqa: BLE001 - retried, then "no comments"
                print(f"fetch_verdicts: listing comments failed "
                      f"(attempt {attempt}): {error}")
                time.sleep(5 * attempt)
        if batch is None:
            # The artifacts are still worth merging; the caller reports which
            # group is missing by name.
            return bodies
        if not batch:
            break
        bodies.extend(batch)
        if len(batch) < 100:
            break
        page += 1
    return bodies


def parse_comment(body: str) -> tuple[int, str, str] | None:
    """A (group, results, phases) triple, or None for an ordinary comment."""
    if not body.startswith(MARKER_PREFIX):
        return None
    try:
        header, rest = body.split(" -->", 1)
        group = int(json.loads(header[len(MARKER_PREFIX):])["group"])
        payload = json.loads(rest.strip().removeprefix("```json").removesuffix("```").strip())
        return (
            group,
            base64.b64decode(payload.get("results", "")).decode(),
            base64.b64decode(payload.get("phases", "")).decode(),
        )
    except (ValueError, KeyError, TypeError):
        return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", default="scripts/ci/step-plan.json")
    parser.add_argument("--artifacts", default="merged")
    parser.add_argument("--results", default="build/step-results.jsonl")
    parser.add_argument("--phases", default="build/phase-log.md")
    args = parser.parse_args()

    expected = groups_in(args.manifest)
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    # On a pull_request run GITHUB_SHA is the synthetic merge commit, which
    # does not exist as an object and cannot be commented on; the workflow
    # passes the PR head in PLAN_SHA.
    sha = os.environ.get("PLAN_SHA") or os.environ.get("GITHUB_SHA", "")
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN") or ""

    from_comments: dict[int, tuple[str, str]] = {}
    if repo and sha and token:
        for comment in fetch_comments(repo, sha, token):
            parsed = parse_comment(comment.get("body") or "")
            if parsed:
                group, results, phases = parsed
                # A retried run of the same group posts again; the newest wins.
                from_comments[group] = (results, phases)
    print(f"fetch_verdicts: {len(from_comments)} group(s) from comments {sorted(from_comments)}")

    root = pathlib.Path(args.artifacts)
    results_out: list[str] = []
    phases_out: list[str] = []
    missing: list[int] = []

    for group in expected:
        results = phases = ""
        if group in from_comments:
            results, phases = from_comments[group]
            source = "comment"
        else:
            directory = root / f"step-verdicts-{group}" / "build"
            if (directory / "step-results.jsonl").exists():
                results = (directory / "step-results.jsonl").read_text()
                source = "artifact"
            else:
                source = "missing"
            phases_file = directory / "phase-log.md"
            if phases_file.exists():
                phases = phases_file.read_text()

        count = len([line for line in results.splitlines() if line.startswith("{")])
        print(f"  group {group}: {count} verdict(s) from {source}")
        if source == "missing":
            missing.append(group)
            continue
        results_out.append(results)
        for line in phases.splitlines():
            if line.startswith("|") and not line.startswith("| Phase") and not line.startswith("| ---"):
                phases_out.append(line)

    pathlib.Path(args.results).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(args.results).write_text("".join(results_out))
    pathlib.Path(args.phases).write_text(PHASE_HEADER + "\n".join(phases_out) + "\n")

    if missing:
        print(f"::error::no verdicts arrived for group(s) {missing} — those passes cannot be graded")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
