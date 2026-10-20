#!/usr/bin/env bash
# Decodes the Play upload keystore from the repository secret into a temporary
# file and exports the four variables `app/build.gradle.kts` reads.
#
# Sourced by the release and Play Readiness workflows. Safe to run when the
# secret is absent: it exports SIGNED_FOR_PLAY=false and the caller decides
# whether that is a warning or an error.
#
# Never prints the password or the keystore contents.

set -uo pipefail

if [ -z "${UPLOAD_KEYSTORE_BASE64:-}" ]; then
  echo "SIGNED_FOR_PLAY=false" >> "${GITHUB_ENV:-/dev/null}"
  echo "::warning::UPLOAD_KEYSTORE_BASE64 is not set. This build will be signed with the debug key, which Google Play rejects. See docs/PLAY_CONSOLE.md."
  exit 0
fi

KEYSTORE_PATH="${RUNNER_TEMP:-/tmp}/upload-keystore.jks"

# base64 -d on GNU, -D on BSD; CI is Linux so either form works, but be explicit.
if ! printf '%s' "$UPLOAD_KEYSTORE_BASE64" | base64 --decode > "$KEYSTORE_PATH" 2>/dev/null; then
  echo "::error::UPLOAD_KEYSTORE_BASE64 could not be decoded. Re-create it with: base64 -w0 upload-keystore.jks"
  exit 1
fi

if [ ! -s "$KEYSTORE_PATH" ]; then
  echo "::error::UPLOAD_KEYSTORE_BASE64 decoded to an empty file."
  exit 1
fi

# Fail early and clearly if the key does not open, rather than deep inside Gradle.
if ! keytool -list -keystore "$KEYSTORE_PATH" \
      -storepass "${UPLOAD_KEYSTORE_PASSWORD:-}" \
      -alias "${UPLOAD_KEY_ALIAS:-}" >/dev/null 2>&1; then
  echo "::error::The upload keystore did not open. Check UPLOAD_KEYSTORE_PASSWORD and UPLOAD_KEY_ALIAS (alias is usually 'upload')."
  exit 1
fi

{
  echo "UPLOAD_KEYSTORE_PATH=$KEYSTORE_PATH"
  echo "SIGNED_FOR_PLAY=true"
} >> "${GITHUB_ENV:-/dev/null}"

echo "Upload keystore ready (alias: ${UPLOAD_KEY_ALIAS:-unset})."
