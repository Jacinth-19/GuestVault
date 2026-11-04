#!/usr/bin/env python3
"""Posts one group's plan verdicts as a commit comment.

Why a comment and not the artifact: the plan works out to eight emulator jobs
whose results have to come back together, and `actions/download-artifact` has
twice delivered only some of them — silently, with `continue-on-error` on the
download step. The merge then graded a whole pass as "never reported" while
every group job was green, and which group vanished changed from run to run.

Commit comments go through the ordinary REST API, which has been dependable
here all along: `report_failure.py` already uses them for build logs precisely
because the workflow-log host is not reachable from every environment.

The body is a marker plus one compact JSON line, with the two files base64
encoded — no Markdown escaping to get wrong, and a small size:

    <!-- plan-verdicts:{"group":3} -->
    {"group":3,"results":"<b64>","phases":"<b64>"}

Usage: python3 scripts/ci/post_verdicts.py --group 3
Needs GH_TOKEN (or GITHUB_TOKEN) and GITHUB_REPOSITORY / GITHUB_SHA.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import pathlib
import sys
import urllib.error
import urllib.request

MARKER_PREFIX = "<!-- plan-verdicts:"


def read(path: str) -> str:
    p = pathlib.Path(path)
    return p.read_text() if p.exists() else ""


def post(repo: str, sha: str, token: str, body: str) -> None:
    url = f"https://api.github.com/repos/{repo}/commits/{sha}/comments"
    request = urllib.request.Request(
        url,
        data=json.dumps({"body": body}).encode(),
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        if response.status >= 300:
            raise RuntimeError(f"GitHub answered {response.status}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--group", required=True)
    parser.add_argument("--results", default="build/step-results.jsonl")
    parser.add_argument("--phases", default="build/phase-log.md")
    args = parser.parse_args()

    repo = os.environ.get("GITHUB_REPOSITORY", "")
    # On a pull_request run GITHUB_SHA is the synthetic merge commit, which
    # does not exist as an object and cannot be commented on; the workflow
    # passes the PR head in PLAN_SHA.
    sha = os.environ.get("PLAN_SHA") or os.environ.get("GITHUB_SHA", "")
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN") or ""
    if not (repo and sha and token):
        print("post_verdicts: no GITHUB_REPOSITORY / GITHUB_SHA / token; skipping")
        return 0

    results = read(args.results)
    phases = read(args.phases)
    if not results.strip() and not phases.strip():
        print("post_verdicts: nothing to post")
        return 0

    payload = {
        "group": int(args.group),
        "results": base64.b64encode(results.encode()).decode(),
        "phases": base64.b64encode(phases.encode()).decode(),
    }
    body = (
        f"{MARKER_PREFIX}{json.dumps({'group': int(args.group)})} -->\n"
        f"```json\n{json.dumps(payload)}\n```"
    )

    try:
        post(repo, sha, token, body)
        lines = len([l for l in results.splitlines() if l.startswith("{")])
        print(f"posted {lines} verdict(s) for group {args.group}")
    except (urllib.error.URLError, RuntimeError, OSError) as error:
        # The artifact is still uploaded, so this is a redundancy that failed,
        # not the run: say so and let the report job fall back.
        print(f"::warning::could not post group {args.group} verdicts: {error}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
