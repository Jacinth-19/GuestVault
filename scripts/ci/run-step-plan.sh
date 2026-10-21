#!/usr/bin/env bash
# Runs the numbered manual test plan one pass at a time.
#
# Each pass is a separate `am instrument` invocation preceded by `pm clear`, so
# a pass starts from a fresh install exactly like the plan says ("first run").
# That also means a crash in one pass cannot poison the next one.
#
# Verdicts are written by the test to files/step-results.jsonl and pulled with
# `run-as` (a debug build is debuggable). `step_report.py` then grades every
# numbered step and fails the job if one that should have run did not pass.

set -uo pipefail

APK="app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
PACKAGE="com.guestvault"
RUNNER="${PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner"
MANIFEST="${MANIFEST:-scripts/ci/step-plan.json}"
RAW="build/step-results.jsonl"
PER_PASS_TIMEOUT="${PER_PASS_TIMEOUT:-420}"

mkdir -p build build/logcat
: > "$RAW"

echo "== device =="
adb devices -l || true
adb shell getprop ro.build.version.sdk || true
adb shell wm size || true

echo "== wait for the system server =="
SYSTEM_READY=0
for _ in $(seq 1 60); do
  if adb shell pm path android >/dev/null 2>&1; then
    SYSTEM_READY=1
    break
  fi
  sleep 2
done
if [ "$SYSTEM_READY" -ne 1 ]; then
  echo "EMULATOR UNHEALTHY: the Android system server never started."
  exit 1
fi

echo "== system settings =="
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell svc power stayon true
adb shell wm dismiss-keyguard
adb shell input keyevent KEYCODE_WAKEUP

echo "== install =="
adb install -r -t "$APK" || exit 1
adb install -r -t "$TEST_APK" || exit 1

TESTS="$(python3 scripts/ci/step_report.py --manifest "$MANIFEST" --list-tests)"
if [ -z "$TESTS" ]; then
  echo "::warning::No device-executable passes in $MANIFEST"
fi

for TEST in $TESTS; do
  echo "== pass: $TEST =="
  adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
  adb shell pm clear "$PACKAGE" >/dev/null 2>&1 || true
  # A cleared package loses runtime grants; re-grant the only one the app asks for.
  adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  adb logcat -c || true

  timeout "$PER_PASS_TIMEOUT" adb shell am instrument -w -r \
    -e class "${PACKAGE}.plan.StepPlanTest#${TEST}" \
    "$RUNNER"
  INSTR_STATUS=$?
  echo "instrumentation exit code for $TEST: $INSTR_STATUS"

  # Pull the verdicts before the next pass clears app data.
  adb shell run-as "$PACKAGE" cat files/step-results.jsonl 2>/dev/null >> "$RAW" || true
  adb shell "cat /sdcard/Android/data/${PACKAGE}/files/step-results.jsonl" 2>/dev/null >> "$RAW" || true

  # One logcat per pass: the only way to see *why* a step failed.
  adb logcat -d -v brief > "build/logcat/${TEST}.txt" 2>/dev/null || true

  if [ "$INSTR_STATUS" -ne 0 ]; then
    echo "::warning::instrumentation for $TEST exited with $INSTR_STATUS"
  fi
done

echo "== verdicts =="
cat "$RAW" || true

echo "== report =="
python3 scripts/ci/step_report.py \
  --manifest "$MANIFEST" \
  --results "$RAW" \
  --out step-report.md
STATUS=$?

# Keep the table next to the workflow logs even when the run is red.
cat step-report.md 2>/dev/null || true

if [ "$STATUS" -ne 0 ]; then
  echo "plan-failed" > plan-failed.txt
fi
exit "$STATUS"
