#!/usr/bin/env python3
"""Posts media-run diagnostics when the walkthrough produced fewer screenshots
than expected, or when the emulator step failed.

Normal runs stay silent: a commit comment is only created when something is
missing, so the history doesn't fill up with noise.
"""

from __future__ import annotations

import glob
import json
import os
import pathlib
import subprocess
import sys

EXPECTED = [
    "00-splash.png",
    "01-onboarding-welcome.png",
    "02-onboarding-apps.png",
    "03-onboarding-keys.png",
    "04-pin-create.png",
    "05-pin-confirm.png",
    "06-dashboard-light.png",
    "07-settings.png",
    "08-select-apps.png",
    "09-confirm-session.png",
    "10-confirm-options.png",
    "11-guest-home.png",
    "12-lock-screen.png",
    "13-lock-wrong-pin.png",
    "14-dashboard-history.png",
    "15-dashboard-dark.png",
    "16-about-dark.png",
    "17-permissions.png",
    "18-session-notification.png",
]

MAX_PER_FILE = 14_000


def tail(path: pathlib.Path, limit: int = MAX_PER_FILE) -> str:
    text = path.read_text(errors="ignore")
    return text if len(text) <= limit else "…(truncated)…\n" + text[-limit:]


def main() -> int:
    produced = {os.path.basename(p) for p in glob.glob("shots/*.png")}
    produced |= {os.path.basename(p) for p in glob.glob("docs/media/*.png")}
    missing = [name for name in EXPECTED if name not in produced]

    test_failed = os.environ.get("TEST_FAILED") == "true"
    if not missing and not test_failed:
        print(f"All {len(EXPECTED)} screenshots present.")
        return 0

    lines = ["### Media run diagnostics", ""]
    lines.append(f"Missing screenshots: `{', '.join(missing) or 'none'}`")
    lines.append(f"Emulator step failed: `{test_failed}`")
    lines.append("")

    listing = sorted(os.path.basename(p) for p in glob.glob("shots/*.png"))
    lines.append(f"Pulled files ({len(listing)}): `{', '.join(listing)}`")
    lines.append("")

    for name in ("emulator.log", "instrumentation.log", "logcat-app.txt"):
        path = pathlib.Path(name)
        if path.exists() and path.stat().st_size:
            lines.append(f"### `{name}`\n\n```text\n{tail(path)}\n```")

    body = "\n".join(lines)[:60_000]
    pathlib.Path("media-comment.json").write_text(json.dumps({"body": body}))

    repo = os.environ.get("GITHUB_REPOSITORY", "")
    sha = os.environ.get("GITHUB_SHA", "")
    if not repo or not sha:
        print(body)
        return 0

    result = subprocess.run(
        ["gh", "api", "-X", "POST", f"repos/{repo}/commits/{sha}/comments",
         "--input", "media-comment.json"],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        print("Could not post the comment:", result.stderr, file=sys.stderr)
        print(body)
    else:
        print("Posted media diagnostics as a commit comment.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
