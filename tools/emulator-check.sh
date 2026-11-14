#!/usr/bin/env bash
set -uo pipefail
test_status=0
gradle --no-daemon :app:connectedDebugAndroidTest || test_status=$?
adb pull /sdcard/Pictures/Phoenix/dashboard.png dashboard.png || true
exit "$test_status"
