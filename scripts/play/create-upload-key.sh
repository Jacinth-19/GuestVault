#!/usr/bin/env bash
# Creates the Google Play upload keystore and stores it as repository secrets.
#
# Run this once, in a GitHub Codespace (it also works on any machine with a
# JDK 17 and the `gh` CLI), from the repository root:
#
#   bash scripts/play/create-upload-key.sh
#
# Design notes:
#   * The password is generated for you by default — you can just press Enter,
#     which matters when the only "keyboard" is a phone.
#   * You sign in with your own GitHub account through a device code, because a
#     Codespace's built-in token cannot write repository secrets. That is one
#     `gh auth login`, not a personal access token, and nothing is typed here.
#   * The keystore never leaves the machine except as an encrypted GitHub
#     secret, and nothing is ever committed (see .gitignore).

set -euo pipefail

REPO="${GH_REPO:-$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || echo Jacinth-19/GuestVault)}"
KEYSTORE="${UPLOAD_KEYSTORE_FILE:-$HOME/upload-keystore.jks}"
CREDENTIALS="$KEYSTORE.credentials.txt"
ALIAS="${UPLOAD_KEY_ALIAS:-upload}"
SECRETS_URL="https://github.com/$REPO/settings/secrets/actions"

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
ok() { printf '    \033[1;32m✓\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!\033[0m  %s\n' "$*"; }
err() { printf '\033[1;31m✗\033[0m  %s\n' "$*" >&2; }

gen_password() {
  # 32 URL-safe characters, ~190 bits — no quoting surprises in keytool/gh.
  # Do NOT use `tr </dev/urandom | head -c 32`: head closes the pipe, tr dies of
  # SIGPIPE, and `set -o pipefail` turns that into a silent exit 141.
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 33 | LC_ALL=C tr -dc 'A-Za-z0-9' | cut -c1-32
  else
    od -An -N32 -tx1 /dev/urandom | tr -d ' \n' | cut -c1-32
  fi
}

# --------------------------------------------------------------------------
# 0. Sanity: interactive terminal + a JDK that ships keytool.
# --------------------------------------------------------------------------
if [ ! -t 0 ]; then
  err "This script is interactive. Run it in a terminal (Codespaces: Terminal → New Terminal)."
  exit 1
fi

ensure_keytool() {
  command -v keytool >/dev/null && return 0
  # The Codespaces universal image keeps JDKs behind SDKMAN; prefer adding the
  # binary directory to PATH over sourcing sdkman-init.sh (which is not -u safe).
  for dir in \
    "$HOME/.sdkman/candidates/java/current/bin" \
    /usr/local/sdkman/candidates/java/current/bin \
    /usr/lib/jvm/*/bin; do
    if [ -x "$dir/keytool" ]; then PATH="$dir:$PATH"; fi
  done
  command -v keytool >/dev/null && return 0

  if command -v apt-get >/dev/null && sudo -n true 2>/dev/null; then
    say "Installing OpenJDK (only needed for keytool)"
    sudo apt-get update -qq
    sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-17-jdk-headless >/dev/null
  fi
  command -v keytool >/dev/null || {
    err "No keytool found. Install a JDK 17 (macOS: brew install openjdk@17) and re-run."
    exit 1
  }
}

command -v gh >/dev/null || { err "The GitHub CLI (gh) is not installed."; exit 1; }
ensure_keytool

# --------------------------------------------------------------------------
# 1. Sign in as *you*. A Codespace exports GITHUB_TOKEN, which gh prefers over
#    stored credentials and which cannot write secrets; drop it for this shell.
# --------------------------------------------------------------------------
say "GitHub account for $REPO"
if [ -n "${GITHUB_TOKEN:-}${GH_TOKEN:-}" ]; then
  warn "Ignoring the Codespace's built-in GITHUB_TOKEN (it can only read)."
fi
unset GITHUB_TOKEN GH_TOKEN

if ! gh auth status --hostname github.com >/dev/null 2>&1; then
  echo "    Sign in once. gh prints an 8-character code — open"
  echo "    https://github.com/login/device on any browser (your phone is fine)"
  echo "    and enter it. Nothing secret is typed into this terminal."
  echo
  gh auth login --hostname github.com --git-protocol https --web --scopes repo
fi
ok "signed in as $(gh api user --jq .login 2>/dev/null || echo 'your account')"

# --------------------------------------------------------------------------
# 2. Create the key (or reuse one you already have).
# --------------------------------------------------------------------------
say "Upload key"
if [ -f "$KEYSTORE" ]; then
  warn "$KEYSTORE already exists — reusing it."
  warn "Only create a NEW key if Play has never accepted this one; replacing it"
  warn "later needs a Play App Signing upload-key reset."
  read -r -s -p "    Keystore password: " STORE_PASS; echo
  KEY_PASS="$STORE_PASS"
else
  read -r -s -p "    Password (press Enter to generate a strong one): " STORE_PASS; echo
  if [ -z "$STORE_PASS" ]; then
    STORE_PASS="$(gen_password)"
    ok "generated a 32-character password"
  fi
  [ ${#STORE_PASS} -ge 6 ] || { err "Google Play requires at least 6 characters."; exit 1; }
  KEY_PASS="$STORE_PASS"

  say "Generating $KEYSTORE"
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$STORE_PASS" -keypass "$KEY_PASS" \
    -dname "${UPLOAD_KEY_DNAME:-CN=GuestVault, OU=Mobile, O=GuestVault, C=GH}" >/dev/null
fi

keytool -list -keystore "$KEYSTORE" -storepass "$STORE_PASS" -alias "$ALIAS" >/dev/null 2>&1 || {
  err "That password does not open $KEYSTORE."
  exit 1
}
ok "key opens (alias: $ALIAS)"

# Save the credentials next to the keystore first, so a later network hiccup
# never leaves you with a key whose password exists nowhere.
umask 077
{
  echo "GuestVault upload key"
  echo "keystore: $KEYSTORE"
  echo "alias:    $ALIAS"
  echo "password: $STORE_PASS"
  echo "created:  $(date -u +%Y-%m-%dT%H:%M:%SZ)"
} >"$CREDENTIALS"
ok "credentials written to $CREDENTIALS (mode 600)"

# --------------------------------------------------------------------------
# 3. Store the four secrets. Probe first so we fail with instructions rather
#    than half-way through.
# --------------------------------------------------------------------------
say "Repository secrets"
if ! gh api "repos/$REPO/actions/secrets/public-key" --silent >/dev/null 2>&1; then
  err "This account cannot write secrets for $REPO."
  echo "    Your GitHub login needs admin on the repository. Sign in again with:"
  echo "      gh auth login --hostname github.com --git-protocol https --web --scopes repo"
  echo "    Then re-run this script — it will reuse the keystore you just made."
  echo
  echo "    Manual fallback: set the four UPLOAD_* secrets at"
  echo "      $SECRETS_URL"
  echo "    using  base64 -w0 $KEYSTORE  for UPLOAD_KEYSTORE_BASE64,"
  echo "    and the password/alias printed in $CREDENTIALS."
  exit 1
fi

printf '%s' "$STORE_PASS" | gh secret set UPLOAD_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$KEY_PASS"   | gh secret set UPLOAD_KEY_PASSWORD      --repo "$REPO"
printf '%s' "$ALIAS"      | gh secret set UPLOAD_KEY_ALIAS         --repo "$REPO"
base64 <"$KEYSTORE" | tr -d '\n' | gh secret set UPLOAD_KEYSTORE_BASE64 --repo "$REPO"
ok "four secrets stored"

if list=$(gh secret list --repo "$REPO" 2>/dev/null); then
  printf '%s\n' "$list" | grep -E '^UPLOAD_' | sed 's/^/    /' || true
else
  echo "    (gh could not list them here; check $SECRETS_URL)"
fi

cat <<EOF

------------------------------------------------------------
Keystore:    $KEYSTORE
Credentials: $CREDENTIALS

BACK BOTH UP NOW — password manager or encrypted drive, off this machine.
Lose the key and you can still request an upload-key reset in Play Console;
leak it and someone else can publish updates as you until you reset it.
Neither file is ever committed (see .gitignore).

Next (the agent does this for you): tag v1.1.1 so the release workflow
rebuilds with this key, then confirm the certificate is not "Android Debug".
------------------------------------------------------------
EOF
