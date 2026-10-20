#!/usr/bin/env bash
# Creates the Google Play upload keystore and stores it as repository secrets.
#
# Run this once, in a GitHub Codespace (a terminal that already has the `gh`
# CLI logged in as the repository owner) or on any machine with JDK 17 + `gh`:
#
#   bash scripts/play/create-upload-key.sh
#
# It is interactive on purpose: the passwords never appear in shell history and
# the keystore never leaves your machine except as an encrypted GitHub secret.
#
# What it does NOT do: commit anything. The .jks stays local — back it up.

set -euo pipefail

REPO="${GH_REPO:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"
KEYSTORE="${UPLOAD_KEYSTORE_FILE:-$HOME/upload-keystore.jks}"
ALIAS="${UPLOAD_KEY_ALIAS:-upload}"

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m!\033[0m  %s\n' "$*"; }

say "GuestVault upload key for $REPO"

command -v keytool >/dev/null || { echo "keytool not found — install a JDK 17 first."; exit 1; }
command -v gh >/dev/null || { echo "gh CLI not found."; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "Run 'gh auth login' first."; exit 1; }

if [ -f "$KEYSTORE" ]; then
  warn "$KEYSTORE already exists — reusing it."
  warn "Only create a NEW key if Play has never accepted this one; changing it later"
  warn "requires a Play App Signing key reset."
else
  say "Choose two passwords (store password and key password)"
  echo "   Use different values if you like; 16+ random characters is ideal."
  echo "   They are never written to disk by this script."

  read -r -s -p "Keystore password: " STORE_PASS; echo
  read -r -s -p "Repeat: " STORE_PASS2; echo
  [ "$STORE_PASS" = "$STORE_PASS2" ] || { echo "Passwords do not match."; exit 1; }
  [ ${#STORE_PASS} -ge 6 ] || { echo "Google requires at least 6 characters."; exit 1; }

  read -r -s -p "Key password (press Enter to reuse the keystore password): " KEY_PASS; echo
  [ -n "${KEY_PASS:-}" ] || KEY_PASS="$STORE_PASS"

  read -r -p "Your name (or company), shown on the certificate: " DNAME
  DNAME="${DNAME:-GuestVault}"

  say "Generating $KEYSTORE with alias '$ALIAS'"
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$STORE_PASS" \
    -keypass "$KEY_PASS" \
    -dname "CN=$DNAME, OU=Mobile, O=GuestVault, L=Accra, S=Greater Accra, C=GH"
fi

if [ -z "${STORE_PASS:-}" ]; then
  read -r -s -p "Keystore password (for the existing file): " STORE_PASS; echo
fi
if [ -z "${KEY_PASS:-}" ]; then
  read -r -s -p "Key password (Enter to reuse): " KEY_PASS; echo
  [ -n "${KEY_PASS:-}" ] || KEY_PASS="$STORE_PASS"
fi

say "Checking the key opens"
keytool -list -keystore "$KEYSTORE" -storepass "$STORE_PASS" -alias "$ALIAS" >/dev/null
echo "   OK"

say "Storing four repository secrets"
printf '%s' "$STORE_PASS" | gh secret set UPLOAD_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$KEY_PASS"   | gh secret set UPLOAD_KEY_PASSWORD      --repo "$REPO"
printf '%s' "$ALIAS"      | gh secret set UPLOAD_KEY_ALIAS         --repo "$REPO"
base64 < "$KEYSTORE" | tr -d '\n' | gh secret set UPLOAD_KEYSTORE_BASE64 --repo "$REPO"

say "Secrets in place"
gh secret list --repo "$REPO" | grep -E '^UPLOAD_' || true

cat <<EOF

------------------------------------------------------------
Done. Two things left, both on you:

1. BACK UP $KEYSTORE somewhere safe (password manager / encrypted drive).
   If you lose it, Play App Signing lets you request an upload-key reset.
   If it leaks, anyone can publish updates as you until you reset it.

2. Cut a release so the workflow rebuilds with the real key:

     git tag -a v1.1.1 -m "GuestVault 1.1.1"
     git push origin v1.1.1

   The workflow prints the signing certificate and fails loudly if the
   bundle is still debug-signed.
------------------------------------------------------------
EOF
