#!/usr/bin/env bash
set -uo pipefail
adb shell settings put system screen_off_timeout 2147483647
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell input keyevent 82
test_status=0
gradle --no-daemon :app:connectedDebugAndroidTest || test_status=$?
adb pull /sdcard/Pictures/Phoenix/dashboard.png dashboard.png || true
adb pull /sdcard/Pictures/Phoenix/focus-failure.png focus-failure.png || true
exit "$test_status"
