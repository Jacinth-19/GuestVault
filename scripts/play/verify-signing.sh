#!/usr/bin/env bash
# Asserts that the built App Bundle is signed with the upload key, not the
# Android debug key. Play rejects debug-signed bundles with
# "You uploaded an APK or Android App Bundle that was signed in debug mode."
#
# Usage: verify-signing.sh <path/to.aab|path/to.apk>
#
# Exits 1 when the artifact is debug-signed, so CI can never publish an
# unuploadable bundle.

set -uo pipefail

ARTIFACT="${1:-app/build/outputs/bundle/release/app-release.aab}"

if [ ! -f "$ARTIFACT" ]; then
  echo "::error::No artifact at $ARTIFACT"
  exit 1
fi

CERT=$(keytool -printcert -jarfile "$ARTIFACT" 2>&1)

if printf '%s' "$CERT" | grep -qi "Android Debug"; then
  echo "::error::$ARTIFACT is signed with the Android debug key. Google Play will reject it."
  printf '%s\n' "$CERT" | head -12
  exit 1
fi

if ! printf '%s' "$CERT" | grep -q "Owner:"; then
  echo "::error::Could not read a signing certificate from $ARTIFACT."
  printf '%s\n' "$CERT" | head -12
  exit 1
fi

echo "Signature OK — $ARTIFACT is release-signed."
printf '%s\n' "$CERT" | grep -E "Owner:|SHA256:|Valid from:" | head -4
