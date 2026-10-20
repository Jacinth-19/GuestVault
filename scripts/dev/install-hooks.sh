#!/usr/bin/env bash
# Installs the repository's git hooks (currently: reject CI-skip markers).
#
#   bash scripts/dev/install-hooks.sh
#
# Hooks are local and not committed (git does not track .git/hooks), so every
# clone runs this once. Nothing here needs a JDK, the Android SDK, or network.

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
HOOKS_DIR="$(git rev-parse --git-path hooks)"
mkdir -p "$HOOKS_DIR"

install_hook() {
  local name="$1"
  cp "$REPO_ROOT/scripts/dev/$name" "$HOOKS_DIR/$name"
  chmod +x "$HOOKS_DIR/$name"
  echo "installed $HOOKS_DIR/$name"
}

install_hook commit-msg

cat <<'EOF'

Done. The commit-msg hook now rejects commit messages carrying a CI-skip marker.

Why: a commit with such a marker produces no workflow runs at all, so a v* tag
pushed onto it publishes nothing and reports nothing. CI cannot catch this --
there is no run to fail -- so it has to be caught here.

EOF
