#!/usr/bin/env python3
"""Posts CI diagnostics as a commit comment.

GitHub's workflow logs live on a storage host that is not reachable from every
environment. A commit comment goes through the regular GitHub API, so build and
emulator failures stay reviewable from anywhere.
"""

from __future__ import annotations

import json
import os
import pathlib
import subprocess
import sys

MAX_PER_FILE = 18_000
MAX_TOTAL = 60_000

LOG_FILES = [
    "build.log",
    "media-build.log",
    "play-build.log",
    "permissions.txt",
    "emulator.log",
    "instrumentation.log",
    "logcat-app.txt",
]


# Kotlin/Gradle emit the actionable line as `e: file:///path/File.kt:42:5 text`.
# The tail of a build log is 90% stack frames, and the truncation below keeps the
# tail — so the one line that explains the failure is exactly what gets cut.
ERROR_PATTERNS = (
    "e: file://",
    "e: ",
    "error:",
    "FAILURE: Build failed",
    "Execution failed for task",
    "Unresolved reference",
    "Type mismatch",
)


def extract_errors(text: str, limit: int = 40) -> list[str]:
    hits: list[str] = []
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or len(stripped) > 400:
            continue
        if any(pattern in stripped for pattern in ERROR_PATTERNS):
            # Skip pure stack frames that happen to mention "error".
            if stripped.startswith("at ") or stripped.startswith("Caused by: org.gradle"):
                continue
            hits.append(stripped)
        if len(hits) >= limit:
            break
    return hits


def main() -> int:
    sections: list[str] = []

    for name in LOG_FILES:
        path = pathlib.Path(name)
        if not path.exists() or path.stat().st_size == 0:
            continue

        text = path.read_text(errors="ignore")
        errors = extract_errors(text)
        if errors:
            sections.append(
                f"### `{name}` — first errors\n\n```text\n" + "\n".join(errors) + "\n```"
            )
        if len(text) > MAX_PER_FILE:
            text = "…(truncated)…\n" + text[-MAX_PER_FILE:]

        sections.append(f"### `{name}`\n\n```text\n{text}\n```")

    if not sections:
        body = "### CI failure\n\nNo log files were produced. Open the failing step in GitHub Actions for details."
    else:
        body = "### CI failure\n\n" + "\n\n".join(sections)

    body = body[:MAX_TOTAL]
    pathlib.Path("comment.md").write_text(body)

    repo = os.environ.get("GITHUB_REPOSITORY", "")
    sha = os.environ.get("GITHUB_SHA", "")
    if not repo or not sha:
        print("Missing GITHUB_REPOSITORY/GITHUB_SHA; printing instead.")
        print(body)
        return 0

    # On a pull request GITHUB_SHA points at the synthetic merge commit, which
    # nobody watches; comment on the PR instead.
    pr_number = None
    event_path = os.environ.get("GITHUB_EVENT_PATH")
    if os.environ.get("GITHUB_EVENT_NAME") == "pull_request" and event_path:
        try:
            payload = json.loads(pathlib.Path(event_path).read_text())
            pr_number = (payload.get("pull_request") or {}).get("number") or payload.get("number")
        except (OSError, ValueError):
            pr_number = None

    if pr_number:
        command = ["gh", "pr", "comment", str(pr_number), "--body-file", "comment.md"]
    else:
        command = [
            "gh",
            "api",
            "-X",
            "POST",
            f"repos/{repo}/commits/{sha}/comments",
            "--input",
            "comment.json",
        ]
        pathlib.Path("comment.json").write_text(json.dumps({"body": body}))

    # `gh` reads GH_TOKEN / GITHUB_TOKEN from the environment.
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode != 0:
        print("Could not post the commit comment:", result.stderr, file=sys.stderr)
        print(body)
        return 0

    print("Posted CI failure diagnostics as a commit comment.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
