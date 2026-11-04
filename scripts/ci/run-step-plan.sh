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

# Which build is being graded.
#
# The free build carries passes 1-14: it is the listing that already ships, and
# those steps must stay meaningful whatever the paid build grows. The paid build
# gets its own emulator and its own pass (15), because a Pro-only feature cannot
# be graded through a screen that is deliberately locked on the free build.
VARIANT="${PLAN_VARIANT:-free}"
case "$VARIANT" in
  pro)
    APK="app/build/outputs/apk/pro/debug/app-pro-debug.apk"
    TEST_APK="app/build/outputs/apk/androidTest/pro/debug/app-pro-debug-androidTest.apk"
    PACKAGE="com.guestvault.pro"
    PLAN_CLASS="${PLAN_CLASS:-ProPlanTest}"
    ;;
  *)
    APK="app/build/outputs/apk/free/debug/app-free-debug.apk"
    TEST_APK="app/build/outputs/apk/androidTest/free/debug/app-free-debug-androidTest.apk"
    PACKAGE="com.guestvault"
    PLAN_CLASS="${PLAN_CLASS:-StepPlanTest}"
    ;;
esac
RUNNER="${PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner"
# The Kotlin package of the plan classes, which is not the applicationId: the
# paid build installs as com.guestvault.pro but its tests still live in
# com.guestvault.plan. Getting this wrong is an INSTRUMENTATION_FAILED with
# "class not found" and no verdicts.
PLAN_PACKAGE="com.guestvault.plan"
MANIFEST="${MANIFEST:-scripts/ci/step-plan.json}"
PLAN_GROUP="${PLAN_GROUP:-}"
RAW="build/step-results.jsonl"
PER_PASS_TIMEOUT="${PER_PASS_TIMEOUT:-900}"

mkdir -p build build/logcat
: > "$RAW"
rm -f plan-failed.txt
PHASES="build/phase-log.md"
printf '| Phase | Exit | Device | Foreground window | Crash / error |\n| --- | --- | --- | --- | --- |\n' > "$PHASES"

# A failure before the first phase used to end the script silently: the group
# uploaded an empty phase table and nobody could see why (the job log is not
# reachable from every environment). Record it as a phase row instead.
fail_setup() {
  local reason="$1"
  printf '| SETUP | 1 | %s | - | %s |\n' \
    "$(adb get-state 2>&1 | tr -d '\r' | head -c 40)" "$(echo "$reason" | tr '\n' ' ' | cut -c1-300)" \
    >> "$PHASES"
  echo "FATAL: $reason"
  exit 2
}

keep_awake() {
  # The emulator still dozes off mid-run: `svc power stayon` only covers the
  # "plugged in" case and a reboot resets it. A sleeping emulator shows its
  # keyguard clock instead of the app, and every tap after that lands on the
  # lock screen - which is what "the row is not in Settings (text=9:22)" meant.
  adb shell settings put system screen_off_timeout -1 >/dev/null 2>&1 || true
  # The key behind the developer option "Stay awake": AC | USB | Wireless.
  # `svc power stayon true` writes the same thing but is reset by some images.
  adb shell settings put global stay_on_while_plugged_in 7 >/dev/null 2>&1 || true
  adb shell svc power stayon true >/dev/null 2>&1 || true
  # Turn the keyguard off altogether. Waking the device is not enough: on these
  # images the lock screen comes back between phases and then every tap lands on
  # its clock, which is how steps failed with "text=4:00" and as "the row is not
  # in Settings". The AVD has no device PIN, so this is safe.
  adb shell locksettings set-disabled true >/dev/null 2>&1 || true
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
}

phase_once() {
  local name="$1" out status device summary
  keep_awake
  device="$(adb get-state 2>&1 | tr -d '\r')"
  # `< /dev/null` matters: `adb shell` otherwise drains the stdin of the
  # enclosing `while read` loop and every phase after the first is skipped.
  out="$(timeout "$PER_PASS_TIMEOUT" adb shell am instrument -w -r \
    -e class "${PLAN_PACKAGE}.${PLAN_CLASS}#${name}" \
    "$RUNNER" < /dev/null 2>&1)"
  status=$?
  echo "$out" > "build/logcat/${name}.txt"
  adb shell logcat -d -t 400 2>/dev/null | tr -d '\r' > "build/logcat/${name}.logcat.txt" || true
  summary="$(echo "$out" | grep -E "INSTRUMENTATION_FAILED|INSTRUMENTATION_STATUS_CODE: -[0-9]|Error|Exception|crashed|offline|not found" \
    | tail -n 2 | tr '\n' ' ' | cut -c1-260)"
  local focus crash
  focus="$(adb shell dumpsys window 2>/dev/null | grep -m1 -E "mCurrentFocus|mFocusedApp" | tr -d '\r' | cut -c1-110)"
  # A bare "Process crashed" says nothing. Keep the runner's own stack lines so
  # the reason survives even when the crash buffer is empty.
  if echo "$out" | grep -q "Process crashed"; then
    local detail
    detail="$(echo "$out" | grep -E "stack=|Exception|Error|Caused by" | tail -n 3 | tr '\n' ' ' | cut -c1-300)"
    summary="${summary} :: ${detail}"
  fi
  # A crash line on its own is useless: keep the exception and its first frame.
  crash="$(adb shell logcat -d -b crash -t 120 2>/dev/null \
    | awk '/FATAL EXCEPTION/{flag=1; count=0} flag{print; count++} count>3{flag=0}' \
    | tr -d '\r' | tr '\n' ' ' | cut -c1-320)"
  # A retried phase adds a second row; label it, or the table reads as if the
  # same phase simply ran twice.
  printf '| %s | %s | %s | %s | %s |\n' "${PHASE_LABEL:-$name}" "$status" "$device" \
    "${focus:-?}" "${summary:-${crash:-no error line}}" >> "$PHASES"
  echo "$out" | tail -n 10
  # Pull before the next host action can wipe the app's data directory.
  pull_results
}

pull_results() {
  adb shell run-as "$PACKAGE" cat files/step-results.jsonl 2>/dev/null >> "$RAW" || true
  adb shell "cat /sdcard/Android/data/${PACKAGE}/files/step-results.jsonl" 2>/dev/null >> "$RAW" || true
}

# Verdict lines in the merged file, as a plain integer.
#
# `grep -c` prints 0 *and* exits 1 when nothing matches, so `$(grep -c ... || echo 0)`
# produced the two-line string "0\n0" and every arithmetic test against it failed.
verdict_lines() {
  local n
  n="$(grep -c '^{' "$RAW" 2>/dev/null || true)"
  printf '%s' "${n:-0}"
}

# Runs a phase, and runs it once more if it failed *fast* without reporting.
#
# Two things went wrong in the same way: a phase that never started (a cold
# emulator refusing the instrumentation, an `adb` hiccup) produced no verdicts,
# and the group still exited 0, so the merged table graded the whole pass as
# "never reported".
#
# The retry is deliberately narrow. A phase that ran for a long time and
# reported nothing is wedged, not unlucky — retrying it doubles the group's
# runtime, and one group already hit the job's 45-minute cap doing exactly that.
# A minute is long enough to boot instrumentation and impossible to hit by a
# phase that is making progress.
FAST_FAILURE_LIMIT=180
run_phase() {
  local name="$1" before after started
  before="$(verdict_lines)"
  started=$SECONDS
  phase_once "$name"
  after="$(verdict_lines)"
  if [ "$after" -gt "$before" ]; then return 0; fi
  if [ $((SECONDS - started)) -ge "$FAST_FAILURE_LIMIT" ]; then
    echo "::warning::phase $name produced no verdicts after $((SECONDS - started))s; not retrying a slow phase"
    return 0
  fi
  echo "::warning::phase $name reported no verdicts in $((SECONDS - started))s; retrying once"
  keep_awake
  PHASE_LABEL="$name (retry)" phase_once "$name"
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
  fail_setup "EMULATOR UNHEALTHY: the Android system server never started"
fi

echo "== system settings =="
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell svc power stayon true
adb shell wm dismiss-keyguard
adb shell input keyevent KEYCODE_WAKEUP
adb shell settings put system screen_off_timeout 2147483647

echo "== install =="
# A cold emulator can still be finishing its first boot when adb says it is
# ready, and then `install` fails with a package-manager timeout. Retry, and
# fall back to a clean uninstall (a half-finished install poisons the next try).
install_apk() {
  local apk="$1" pkg="$2" attempt
  for attempt in 1 2 3; do
    # No pipe: `grep -q` closing the pipe early makes `tee` die of SIGPIPE, and
    # under `pipefail` that turned a successful install into a failure.
    if adb install -r -t "$apk" >> build/install.log 2>&1; then
      return 0
    fi
    # Already installed is good enough to run the plan.
    if adb shell pm path "$pkg" 2>/dev/null | grep -q package; then
      echo "$(basename "$apk"): install failed but $pkg is present" >> build/install.log
      return 0
    fi
    echo "install attempt $attempt failed for $(basename "$apk")" >> build/install.log
    sleep 5
  done
  return 1
}
install_apk "$APK" "$PACKAGE" || {
  adb uninstall "$PACKAGE" >/dev/null 2>&1 || true
  install_apk "$APK" "$PACKAGE" ||
    fail_setup "the app APK could not be installed: $(tail -n 3 build/install.log 2>/dev/null | tr '\n' ' ')"
}
install_apk "$TEST_APK" "${PACKAGE}.test" || {
  adb uninstall "${PACKAGE}.test" >/dev/null 2>&1 || true
  install_apk "$TEST_APK" "${PACKAGE}.test" ||
    fail_setup "the test APK could not be installed: $(tail -n 3 build/install.log 2>/dev/null | tr '\n' ' ')"
}

# The phase list is materialised first: if `plan_ops.py` ever fails, the loop
# below would simply have nothing to read and the group would "pass" with an
# empty plan.
python3 scripts/ci/plan_ops.py "$MANIFEST" ${PLAN_GROUP:+--group "$PLAN_GROUP"} \
  > build/phases.tsv 2>> build/install.log
if [ ! -s build/phases.tsv ]; then
  fail_setup "no phases were generated for group '${PLAN_GROUP:-all}' (manifest=$MANIFEST)"
fi
echo "== phases =="; cat build/phases.tsv

# FD 3, not stdin: adb commands inside the loop would otherwise consume the
# phase list and silently end the plan after the first phase.
PHASES_RUN=0
while IFS=$'\t' read -r TYPE NAME CLEAR GRANT VALUE <&3; do
  [ -z "${TYPE:-}" ] && continue
  PHASES_RUN=$((PHASES_RUN + 1))

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
      keep_awake
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
done 3< build/phases.tsv

# A file of blank lines is "non-empty" to `[ -s ]`, so the old guard passed
# while the loop above skipped every row: the group then reported only its
# pre-recorded manual verdicts, exited 0 and looked green. Count the phases the
# loop actually executed, and fail loudly at zero.
printf '%s\n' "$PHASES_RUN" > build/phase-count.txt
if [ "$PHASES_RUN" -eq 0 ]; then
  fail_setup "group '${PLAN_GROUP:-all}' executed 0 phases: build/phases.tsv is $(wc -c < build/phases.tsv) bytes with no phase rows"
fi

if [ ! -s "$RAW" ]; then
  fail_setup "the plan ran but wrote no verdicts (phases run: $PHASES_RUN)"
fi

echo "== verdicts =="
cat "$RAW" || true
VERDICT_COUNT="$(verdict_lines)"
echo "verdict lines: $VERDICT_COUNT"
printf '%s\n' "$VERDICT_COUNT" > build/verdict-count.txt
# A group that produced nothing must be *red*, not merely annotated. An
# `::error::` line does not fail a step, so a silent group used to upload an
# empty verdict file, exit 0 and only show up in the merged report as "never
# reported" — with no job to open and no log to read.
if [ "$VERDICT_COUNT" -eq 0 ]; then
  echo "::error::this group produced no verdicts; the emulator phase never ran"
  exit 1
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
