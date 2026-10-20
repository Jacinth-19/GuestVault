#!/usr/bin/env bash
# Runs the instrumented walkthrough on the already-booted emulator and pulls
# the screenshots + screen recording back to the runner.
#
# Every command is logged to stdout; the workflow redirects this script's
# output to emulator.log so failures can be reported even when CI logs are not
# reachable.

set -uo pipefail

APK="app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
PACKAGE="com.guestvault"
RUNNER="${PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner"

echo "== device =="
adb devices -l || true
adb shell getprop ro.build.version.sdk || true
adb shell wm size || true
adb shell wm density || true

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

echo "== start screen recording =="
# Started device-side (not as a local background adb client) so the CI action's
# cleanup never trips over a lingering process on the runner.
adb shell "screenrecord --size 720x1600 --bit-rate 4000000 --time-limit 240 \
  /sdcard/Download/walkthrough.mp4 >/dev/null 2>&1 &" || true
sleep 4

echo "== run instrumentation =="
timeout 900 adb shell am instrument -w -r \
  -e class com.guestvault.GuestVaultFlowTest \
  "$RUNNER"
TEST_STATUS=$?
echo "instrumentation exit code: $TEST_STATUS"

echo "== stop recording =="
adb shell 'pidof screenrecord | xargs -r kill -2' || true
sleep 8
adb shell 'pidof screenrecord' || true

echo "== collect artifacts =="
adb pull /sdcard/Download/walkthrough.mp4 walkthrough.mp4 || true
adb pull /sdcard/Pictures/GuestVault shots || true
adb shell rm -rf /sdcard/Pictures/GuestVault || true
adb shell rm -f /sdcard/Download/walkthrough.mp4 || true

echo "== app logcat =="
adb logcat -d -v brief -s GuestVaultApp:V AndroidRuntime:E ActivityTaskManager:W \
  GuestSessionService:V GuestHomeActivity:V > logcat-app.txt 2>&1 || true
adb logcat -d -v brief > logcat.txt 2>&1 || true
tail -n 80 logcat-app.txt || true

echo "== screenshots =="
ls -la shots 2>/dev/null || echo "no screenshots were pulled"

exit "$TEST_STATUS"
