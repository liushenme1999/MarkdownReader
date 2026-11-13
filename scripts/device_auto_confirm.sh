#!/usr/bin/env bash

# Auto-confirm MIUI's package-installer and "start app" dialogs while running
# device acceptance tests. This handles both the app APK and the test APK
# (space.liushenme.markdownreader.test). Only system installer/security windows
# are touched.
#
# Usage:
#   scripts/device_auto_confirm.sh [serial] [timeout-seconds]

set -euo pipefail

SERIAL="${1:-${ANDROID_SERIAL:-}}"
TIMEOUT_SECONDS="${2:-300}"
POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS:-0.25}"
CLICK_COOLDOWN_SECONDS="${CLICK_COOLDOWN_SECONDS:-0.75}"

ADB=(adb)
if [[ -n "$SERIAL" ]]; then
    ADB+=(-s "$SERIAL")
fi

deadline=$((SECONDS + TIMEOUT_SECONDS))
last_click_at=0

log() {
    printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"
}

find_button() {
    local xml="$1"
    XML="$xml" python3 - <<'PY'
import os
import re
import sys
import xml.etree.ElementTree as ET

xml = os.environ["XML"]
# `uiautomator dump /dev/tty` appends the "UI hierchary dumped to" status line
# after the XML document. Trim it before parsing.
xml_end = xml.find("</hierarchy>")
if xml_end >= 0:
    xml = xml[: xml_end + len("</hierarchy>")]
try:
    root = ET.fromstring(xml)
except ET.ParseError:
    sys.exit(1)

preferred = (
    "继续安装",
    "确认安装",
    "允许安装",
    "始终允许",
    "仅此一次",
    "本次允许",
    "安装",
    "确认",
    "确定",
    "继续",
    "允许",
    "Install",
    "Continue",
    "Allow",
    "OK",
)

installer_packages = {
    "com.miui.packageinstaller",
    "com.android.packageinstaller",
    "com.google.android.packageinstaller",
    "com.miui.securitycenter",
    "com.lbe.security.miui",
    "com.android.permissioncontroller",
}

installer_nodes = [
    node
    for node in root.iter("node")
    if (node.get("package") or "") in installer_packages
]
if not installer_nodes:
    sys.exit(1)

# MIUI's USB-install prompt is rendered by Security Center. Require one of its
# known dialogs so a generic Security Center toast/button is never tapped.
all_text = "\n".join(
    " ".join(
        (
            (node.get("text") or "").strip(),
            (node.get("content-desc") or "").strip(),
        )
    )
    for node in installer_nodes
)
dialog_markers = (
    "USB安装",
    "USB 安装",
    "正在通过USB安装",
    "正在通过 USB 安装",
    "应用安装",
    "软件包安装",
    "启动应用",
    "启动此应用",
    "Install",
)
if not any(marker in all_text for marker in dialog_markers):
    sys.exit(1)

by_text = {}
for node in root.iter("node"):
    text = (node.get("text") or "").strip()
    desc = (node.get("content-desc") or "").strip()
    for label in (text, desc):
        for index, preferred_label in enumerate(preferred):
            normalized = re.sub(r"\s*[（(]\d+[）)]\s*$", "", label).strip()
            if normalized == preferred_label or label == preferred_label:
                bounds = re.findall(r"\d+", node.get("bounds") or "")
                if len(bounds) != 4:
                    continue
                x1, y1, x2, y2 = map(int, bounds)
                by_text.setdefault(index, (label, (x1 + x2) // 2, (y1 + y2) // 2))

if not by_text:
    sys.exit(1)

label, x, y = by_text[min(by_text)]
print(f"{label}\t{x}\t{y}")
PY
}

log "Watching for MIUI confirmation dialogs for ${TIMEOUT_SECONDS}s"
while (( SECONDS < deadline )); do
    # Read the tree directly from `/dev/tty`. Writing to a file and then reading
    # it back could return the previous window (often the launcher) while MIUI's
    # short USB-install countdown was already on screen.
    xml="$("${ADB[@]}" exec-out uiautomator dump --compressed /dev/tty 2>/dev/null || true)"
    button="$(find_button "$xml" || true)"
    if [[ -z "$button" ]]; then
        sleep "$POLL_INTERVAL_SECONDS"
        continue
    fi

    IFS=$'\t' read -r label x y <<< "$button"
    now=$(date +%s)
    if (( now == last_click_at )); then
        sleep "$POLL_INTERVAL_SECONDS"
        continue
    fi

    log "Clicking '$label' at ($x, $y)"
    "${ADB[@]}" shell input tap "$x" "$y" >/dev/null
    last_click_at="$now"
    # MIUI uses two or more installer windows that can expose the same button
    # label and coordinates. Debounce by time, not by the button identity, so
    # the next confirmation stage is never mistaken for an already-clicked one.
    sleep "$CLICK_COOLDOWN_SECONDS"
done

log "Watcher stopped"
