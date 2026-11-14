#!/usr/bin/env bash
set -uo pipefail
adb shell settings put system screen_off_timeout 2147483647
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell input keyevent 82
# Pixel Launcher's observed ANR steals focus from the instrumented app.
# Isolate only that unrelated HOME package in this disposable CI emulator.
launcher_pkg=com.google.android.apps.nexuslauncher
adb shell am force-stop "$launcher_pkg" || true
adb shell pm disable-user --user 0 "$launcher_pkg" || true
test_status=0
gradle --no-daemon :app:connectedDebugAndroidTest || test_status=$?
adb pull /sdcard/Pictures/Phoenix/dashboard.png dashboard.png || true
adb pull /sdcard/Pictures/Phoenix/ar-hud.png ar-hud.png || true
adb pull /sdcard/Pictures/Phoenix/ar-grid.png ar-grid.png || true
adb pull /sdcard/Pictures/Phoenix/focus-failure.png focus-failure.png || true
adb shell pm enable "$launcher_pkg" || true
exit "$test_status"
