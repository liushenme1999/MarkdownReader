#!/usr/bin/env bash

# Fresh-install the debug app and its instrumentation APK while auto-confirming
# MIUI's USB-install prompts for both packages.
#
# Usage:
#   scripts/install_debug_device.sh [serial]

set -euo pipefail

SERIAL="${1:-${ANDROID_SERIAL:-}}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_APK_DIR="$ROOT_DIR/app/build/outputs/apk/debug"
TEST_APK_DIR="$ROOT_DIR/app/build/outputs/apk/androidTest/debug"
APP_APK="${APP_APK:-$(ls -t "$APP_APK_DIR"/*.apk 2>/dev/null | head -1 || true)}"
TEST_APK="${TEST_APK:-$(ls -t "$TEST_APK_DIR"/*.apk 2>/dev/null | head -1 || true)}"
WATCH_SECONDS="${WATCH_SECONDS:-180}"
WATCHER_PID=""

ADB=(adb)
if [[ -n "$SERIAL" ]]; then
    ADB+=(-s "$SERIAL")
fi

if [[ ! -f "$APP_APK" || ! -f "$TEST_APK" ]]; then
    printf '缺少 Debug APK，请先运行 ./gradlew assembleDebug assembleDebugAndroidTest\n' >&2
    exit 1
fi

stop_watcher() {
    if [[ -n "${WATCHER_PID:-}" ]]; then
        kill "$WATCHER_PID" 2>/dev/null || true
        wait "$WATCHER_PID" 2>/dev/null || true
        WATCHER_PID=""
    fi
}

start_watcher() {
    "$ROOT_DIR/scripts/device_auto_confirm.sh" "$SERIAL" "$WATCH_SECONDS" &
    WATCHER_PID=$!
    sleep 0.5
}

trap stop_watcher EXIT

"${ADB[@]}" uninstall space.liushenme.markdownreader.test >/dev/null 2>&1 || true
"${ADB[@]}" uninstall space.liushenme.markdownreader >/dev/null 2>&1 || true

start_watcher
"${ADB[@]}" install -r -t "$APP_APK"
stop_watcher
sleep 1

# Use a fresh watcher for the test package. MIUI can briefly keep the previous
# install-result window visible, which otherwise prevents the next USB dialog
# from being observed before its short countdown expires.
start_watcher
"${ADB[@]}" install -r -t "$TEST_APK"
stop_watcher

"${ADB[@]}" shell pm path space.liushenme.markdownreader >/dev/null
"${ADB[@]}" shell pm path space.liushenme.markdownreader.test >/dev/null

printf '主包和测试包均已安装完成。\n'
