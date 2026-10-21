#!/usr/bin/env bash
# Runs the numbered manual test plan, including the steps that need the host.
#
# The manifest's `run` list interleaves instrumented phases with host actions
# (`pm clear`, force-stop, reboot, revoke a permission, uninstall). That is the
# only way to grade steps like "force-stop during the cool-down" or "reboot the
# phone": the instrumentation shares the app's process, so it cannot kill it
# itself.
#
# Verdicts are written by the test to files/step-results.jsonl and pulled with
# `run-as` (the debug build is debuggable). `step_report.py` then grades every
# numbered step and fails the job if one that should have run did not pass.

set -uo pipefail

APK="app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
PACKAGE="com.guestvault"
RUNNER="${PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner"
MANIFEST="${MANIFEST:-scripts/ci/step-plan.json}"
PLAN_GROUP="${PLAN_GROUP:-}"
RAW="build/step-results.jsonl"
PER_PASS_TIMEOUT="${PER_PASS_TIMEOUT:-900}"

mkdir -p build build/logcat
: > "$RAW"
rm -f plan-failed.txt
PHASES="build/phase-log.md"
printf '| Phase | Exit | Device | Foreground window | Crash / error |\n| --- | --- | --- | --- | --- |\n' > "$PHASES"

run_phase() {
  local name="$1" out status device summary
  device="$(adb get-state 2>&1 | tr -d '\r')"
  # `< /dev/null` matters: `adb shell` otherwise drains the stdin of the
  # enclosing `while read` loop and every phase after the first is skipped.
  out="$(timeout "$PER_PASS_TIMEOUT" adb shell am instrument -w -r \
    -e class "${PACKAGE}.plan.StepPlanTest#${name}" \
    "$RUNNER" < /dev/null 2>&1)"
  status=$?
  echo "$out" > "build/logcat/${name}.txt"
  adb shell logcat -d -t 400 2>/dev/null | tr -d '\r' > "build/logcat/${name}.logcat.txt" || true
  summary="$(echo "$out" | grep -E "INSTRUMENTATION_FAILED|INSTRUMENTATION_STATUS_CODE: -[0-9]|Error|Exception|crashed|offline|not found" \
    | tail -n 2 | tr '\n' ' ' | cut -c1-260)"
  local focus crash
  focus="$(adb shell dumpsys window 2>/dev/null | grep -m1 -E "mCurrentFocus|mFocusedApp" | tr -d '\r' | cut -c1-110)"
  # A crash line on its own is useless: keep the exception and its first frame.
  crash="$(adb shell logcat -d -b crash -t 120 2>/dev/null \
    | awk '/FATAL EXCEPTION/{flag=1; count=0} flag{print; count++} count>3{flag=0}' \
    | tr -d '\r' | tr '\n' ' ' | cut -c1-320)"
  printf '| %s | %s | %s | %s | %s |\n' "$name" "$status" "$device" "${focus:-?}" "${summary:-${crash:-no error line}}" >> "$PHASES"
  echo "$out" | tail -n 10
  # Pull before the next host action can wipe the app's data directory.
  pull_results
}

pull_results() {
  adb shell run-as "$PACKAGE" cat files/step-results.jsonl 2>/dev/null >> "$RAW" || true
  adb shell "cat /sdcard/Android/data/${PACKAGE}/files/step-results.jsonl" 2>/dev/null >> "$RAW" || true
}

grant_notifications() {
  if [ "${1:-1}" = "1" ]; then
    adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  else
    adb shell pm revoke "$PACKAGE" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  fi
}

wait_for_boot() {
  adb wait-for-device
  for _ in $(seq 1 150); do
    if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
      break
    fi
    sleep 2
  done
  # The launcher needs a moment after boot_completed before it accepts input.
  sleep 8
  adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb shell svc power stayon true >/dev/null 2>&1 || true
}

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

# FD 3, not stdin: adb commands inside the loop would otherwise consume the
# phase list and silently end the plan after the first phase.
while IFS=$'\t' read -r TYPE NAME CLEAR GRANT VALUE <&3; do
  [ -z "${TYPE:-}" ] && continue

  if [ "$TYPE" = "test" ]; then
    echo "== phase: $NAME (clear=$CLEAR grant=${GRANT:-1}) =="
    if [ "$CLEAR" = "1" ]; then
      adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
      adb shell pm clear "$PACKAGE" >/dev/null 2>&1 || true
    fi
    # The phase's grant flag always applies: 5.6 and 10.2 exist to exercise
    # "the permission was denied", which needs a revoke on a warm install.
    grant_notifications "${GRANT:-1}"
    adb logcat -c || true

    run_phase "$NAME"

    adb logcat -d -v brief > "build/logcat/${NAME}-logcat.txt" 2>/dev/null || true
    if ! adb get-state >/dev/null 2>&1; then
      echo "::error::the emulator is gone after $NAME; stopping the plan"
      printf '| _device lost_ | - | missing | stopped after %s |\n' "$NAME" >> "$PHASES"
      break
    fi
    continue
  fi

  echo "== host: $NAME ${VALUE:-} =="
  case "$NAME" in
    clear)
      adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
      adb shell pm clear "$PACKAGE" >/dev/null 2>&1 || true
      grant_notifications "${GRANT:-1}"
      ;;
    force-stop)
      adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
      ;;
    revoke-notifications)
      adb shell pm revoke "$PACKAGE" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
      ;;
    reboot)
      adb reboot || true
      wait_for_boot
      ;;
    airplane-on)
      adb shell cmd connectivity airplane-mode enable >/dev/null 2>&1 \
        || adb shell "settings put global airplane_mode_on 1; am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true" >/dev/null 2>&1 || true
      ;;
    airplane-off)
      adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1 \
        || adb shell "settings put global airplane_mode_on 0; am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false" >/dev/null 2>&1 || true
      ;;
    font-scale)
      adb shell settings put system font_scale "${VALUE:-1.0}"
      ;;
    uninstall-check)
      UNINSTALL_OUT="$(adb uninstall "$PACKAGE" 2>&1)"
      STILL_INSTALLED="$(adb shell pm path "$PACKAGE" 2>/dev/null | tr -d '\r')"
      if adb shell run-as "$PACKAGE" ls files >/dev/null 2>&1; then
        DATA_LEFT="yes"
      else
        DATA_LEFT="no"
      fi
      if grep -q 'android:allowBackup="false"' app/src/main/AndroidManifest.xml; then
        BACKUP="android:allowBackup=\"false\" in the manifest"
      else
        BACKUP="allowBackup is NOT disabled"
      fi
      NOTES="uninstall said '${UNINSTALL_OUT}'; package path '${STILL_INSTALLED:-<empty>}'; app data left: ${DATA_LEFT}; ${BACKUP}"
      if echo "$UNINSTALL_OUT" | grep -q "Success" && [ -z "$STILL_INSTALLED" ] && [ "$DATA_LEFT" = "no" ] && grep -q 'android:allowBackup="false"' app/src/main/AndroidManifest.xml; then
        python3 scripts/ci/host_result.py --out "$RAW" --id 14.6 --status PASS --note "$NOTES"
      else
        python3 scripts/ci/host_result.py --out "$RAW" --id 14.6 --status FAIL --note "$NOTES"
      fi
      # Put the app back so a rerun on a warm emulator can install cleanly.
      adb install -r -t "$APK" >/dev/null 2>&1 || true
      ;;
    *)
      echo "::warning::unknown host action $NAME"
      ;;
  esac
done 3< <(python3 scripts/ci/plan_ops.py "$MANIFEST" ${PLAN_GROUP:+--group "$PLAN_GROUP"})

echo "== verdicts =="
cat "$RAW" || true
VERDICT_COUNT="$(grep -c '^{' "$RAW" 2>/dev/null || echo 0)"
echo "verdict lines: $VERDICT_COUNT"
printf '%s\n' "$VERDICT_COUNT" > build/verdict-count.txt
if [ "$VERDICT_COUNT" -eq 0 ]; then
  echo "::error::this group produced no verdicts; the emulator phase never ran"
fi

echo "== phases =="
cat "$PHASES" || true

# A group cannot grade the whole plan (the other groups' steps are legitimately
# missing here), so this is a per-group preview only. The report job merges every
# group's verdicts and is the gate.
echo "== group preview =="
python3 scripts/ci/step_report.py \
  --manifest "$MANIFEST" \
  --results "$RAW" \
  --phase-log "$PHASES" \
  --out step-report-group.md \
  || true

cat step-report-group.md 2>/dev/null || true
exit 0
