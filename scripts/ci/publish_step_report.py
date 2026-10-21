#!/usr/bin/env python3
"""Publishes the per-step verdict table where it can actually be read.

GitHub's workflow logs and artifacts live on a storage host that is not
reachable from every environment. A PR comment goes through the regular GitHub
API, so the verdict table stays reviewable from anywhere.

The comment is *sticky*: reruns update the existing one instead of stacking a
new table on every push.
"""

from __future__ import annotations

import json
import os
import pathlib
import subprocess
import sys

MARKER = "<!-- step-plan-report -->"
MAX_BODY = 60_000


def gh(*args: str, payload: dict | None = None) -> subprocess.CompletedProcess:
    command = ["gh", *args]
    stdin_text = None
    if payload is not None:
        payload_path = pathlib.Path("step-report-payload.json")
        payload_path.write_text(json.dumps(payload))
        command = ["gh", *args, "--input", str(payload_path)]
    # `gh` reads GH_TOKEN / GITHUB_TOKEN from the environment.
    stdin_text = None
    return subprocess.run(command, capture_output=True, text=True, input=stdin_text)


def pull_request_number() -> str | None:
    if os.environ.get("GITHUB_EVENT_NAME") != "pull_request":
        return None
    event_path = os.environ.get("GITHUB_EVENT_PATH")
    if not event_path:
        return None
    try:
        payload = json.loads(pathlib.Path(event_path).read_text())
    except (OSError, ValueError):
        return None
    number = (payload.get("pull_request") or {}).get("number") or payload.get("number")
    return str(number) if number else None


def existing_comment(repo: str, pr: str) -> int | None:
    result = gh(
        "api",
        "--paginate",
        f"repos/{repo}/issues/{pr}/comments?per_page=100",
        "-q",
        f'.[] | select(.body | contains("{MARKER}")) | .id',
    )
    if result.returncode != 0:
        return None
    first = result.stdout.strip().splitlines()
    return int(first[0]) if first and first[0].strip().isdigit() else None


def main() -> int:
    report = pathlib.Path("step-report.md")
    if not report.exists():
        print("No step-report.md; nothing to publish.")
        return 0

    body = (MARKER + "\n" + report.read_text(encoding="utf-8"))[:MAX_BODY]
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    sha = os.environ.get("GITHUB_SHA", "")
    if not repo:
        print("Missing GITHUB_REPOSITORY; printing instead.")
        print(body)
        return 0

    pr = pull_request_number()
    if pr:
        comment_id = existing_comment(repo, pr)
        if comment_id:
            result = gh(
                "api", "-X", "PATCH", f"repos/{repo}/issues/comments/{comment_id}",
                payload={"body": body},
            )
        else:
            result = gh(
                "api", "-X", "POST", f"repos/{repo}/issues/{pr}/comments",
                payload={"body": body},
            )
    elif sha:
        result = gh(
            "api", "-X", "POST", f"repos/{repo}/commits/{sha}/comments",
            payload={"body": body},
        )
    else:
        print("No PR and no SHA; printing instead.")
        print(body)
        return 0

    if result.returncode != 0:
        print("Could not publish the verdict table:", result.stderr, file=sys.stderr)
        print(body)
        return 0

    print("Published the per-step verdict table.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
