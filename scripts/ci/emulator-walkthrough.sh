#!/usr/bin/env bash
# Runs the instrumented walkthrough on the already-booted emulator and pulls
# the screenshots + screen recording back to the runner.
#
# Every command is logged to stdout; the workflow redirects this script's
# output to emulator.log so failures can be reported even when CI logs are not
# reachable.

set -uo pipefail

# The Play-readiness matrix only needs the instrumented flow; recording a video
# and pulling screenshots for every API level would triple the job time.
SKIP_MEDIA_CAPTURE="${SKIP_MEDIA_CAPTURE:-0}"

APK="app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
PACKAGE="com.guestvault"
RUNNER="${PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner"

echo "== device =="
adb devices -l || true
adb shell getprop ro.build.version.sdk || true
adb shell wm size || true
adb shell wm density || true

# The Android system server occasionally OOMs on the lighter system images and
# never publishes `package`/`window`/`input`, which makes every later adb command
# fail with a misleading error. Wait for it explicitly so the log says what broke.
echo "== wait for the system server =="
SYSTEM_READY=0
for i in $(seq 1 60); do
  if adb shell pm path android >/dev/null 2>&1; then
    SYSTEM_READY=1
    echo "package service ready after $((i * 2))s"
    break
  fi
  sleep 2
done
if [ "$SYSTEM_READY" -ne 1 ]; then
  echo "EMULATOR UNHEALTHY: the Android system server never started (out of memory?)."
  adb logcat -d -b crash -v brief 2>/dev/null | tail -40 || true
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
adb install -r -t "$APK"
adb install -r -t "$TEST_APK"
adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS || true
adb shell am force-stop "$PACKAGE" || true
adb shell rm -rf /sdcard/Pictures/GuestVault
adb shell rm -f /sdcard/Download/walkthrough.mp4

adb logcat -c || true

if [ "$SKIP_MEDIA_CAPTURE" = "1" ]; then
  echo "== screen recording skipped (SKIP_MEDIA_CAPTURE=1) =="
else
echo "== start screen recording =="
# Started device-side (not as a local background adb client) so the CI action's
# cleanup never trips over a lingering process on the runner.
adb shell "screenrecord --size 720x1600 --bit-rate 4000000 --time-limit 240 \
  /sdcard/Download/walkthrough.mp4 >/dev/null 2>&1 &" || true
sleep 4
fi

echo "== run instrumentation =="
timeout 900 adb shell am instrument -w -r \
  -e class com.guestvault.GuestVaultFlowTest \
  "$RUNNER"
TEST_STATUS=$?
echo "instrumentation exit code: $TEST_STATUS"

if [ "$SKIP_MEDIA_CAPTURE" = "1" ]; then
  mkdir -p shots
else
echo "== stop recording =="
adb shell 'pidof screenrecord | xargs -r kill -2' || true
sleep 8
adb shell 'pidof screenrecord' || true

echo "== collect artifacts =="
adb pull /sdcard/Download/walkthrough.mp4 walkthrough.mp4 || true
adb pull /sdcard/Pictures/GuestVault shots || true
mkdir -p shots
fi

# The splash screen only lives for ~1.1s. It is captured here, outside the
# instrumentation process, because a cold start requires force-stopping the app
# (which would also kill the running test process).
if [ "$SKIP_MEDIA_CAPTURE" = "1" ]; then
  echo "== splash capture skipped (SKIP_MEDIA_CAPTURE=1) =="
  adb shell rm -rf /sdcard/Pictures/GuestVault || true
  adb shell rm -f /sdcard/Download/walkthrough.mp4 || true
else
echo "== cold-start splash screenshot =="
adb shell am force-stop com.guestvault || true
sleep 1
adb shell am force-stop com.guestvault || true
adb shell am start -n com.guestvault/.MainActivity >/dev/null || true
# Grab frames while the Compose splash (logo + name + tagline) is on screen.
sleep 0.60; adb exec-out screencap -p > shots/splash-frame-a.png || true
sleep 0.25; adb exec-out screencap -p > shots/splash-frame-b.png || true
sleep 0.25; adb exec-out screencap -p > shots/splash-frame-c.png || true
sleep 0.25; adb exec-out screencap -p > shots/splash-frame-d.png || true
sleep 0.25; adb exec-out screencap -p > shots/splash-frame-e.png || true

# Pick the frame that shows the indigo splash with the title faded in.
python3 scripts/ci/pick_splash.py || cp shots/splash-frame-c.png shots/00-splash.png 2>/dev/null || true
ls -la shots/00-splash.png 2>/dev/null || echo "splash capture failed"
# Never let intermediate candidate frames reach the repository.
rm -f shots/splash-frame-*.png

adb shell rm -rf /sdcard/Pictures/GuestVault || true
adb shell rm -f /sdcard/Download/walkthrough.mp4 || true
fi

# ---------------------------------------------------------------------------
# Optional: prove the *release* APK (R8-minified) also works on device.
# Screenshots from the debug run are already pulled, so this pass is not
# allowed to overwrite them.
# ---------------------------------------------------------------------------
if [ -n "${VERIFY_RELEASE_APK:-}" ] && [ -f "$VERIFY_RELEASE_APK" ]; then
  echo "== verify release APK =="
  adb uninstall "$PACKAGE" >/dev/null 2>&1 || true
  adb install -r -t "$VERIFY_RELEASE_APK"
  adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS || true
  adb shell am force-stop "$PACKAGE" || true

  timeout 900 adb shell am instrument -w -r \
    -e class com.guestvault.GuestVaultFlowTest \
    "$RUNNER" > instrumentation-release.log 2>&1
  RELEASE_STATUS=$?
  tail -n 40 instrumentation-release.log || true
  echo "release instrumentation exit code: $RELEASE_STATUS"
  if [ "$RELEASE_STATUS" -ne 0 ]; then
    echo "RELEASE APK FAILED THE WALKTHROUGH"
    TEST_STATUS=1
  fi
  adb shell rm -rf /sdcard/Pictures/GuestVault || true
fi

echo "== app logcat =="
adb logcat -d -v brief -s GuestVaultApp:V AndroidRuntime:E ActivityTaskManager:W \
  GuestSessionService:V GuestHomeActivity:V > logcat-app.txt 2>&1 || true
adb logcat -d -v brief > logcat.txt 2>&1 || true
tail -n 80 logcat-app.txt || true

echo "== screenshots =="
ls -la shots 2>/dev/null || echo "no screenshots were pulled"

exit "$TEST_STATUS"
