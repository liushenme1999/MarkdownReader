#!/usr/bin/env python3
"""通过 adb 安装 APK，并自动确认 MIUI 的「USB 安装提示」弹窗。

MIUI 在安装/覆盖安装时会弹一个约 10 秒的确认框：
    正在通过 USB 安装此应用，是否继续？  [继续安装] [拒绝（n）]
adb install 会一直挂起等待，超时后报 INSTALL_FAILED_USER_RESTRICTED。
本脚本并行执行 adb install 与界面轮询，发现「继续安装」按钮立即点击。

用法：
    python3 scripts/adb_install_confirm.py [APK路径]

不带参数时使用 app/release/ 下最新的 *release*.apk。
退出码：0 安装成功；1 安装失败。
"""

from __future__ import annotations

import html
import re
import subprocess
import sys
import time
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
CONFIRM_LABELS = {"继续安装", "繼續安裝", "继续", "繼續", "安装", "安裝", "确定", "確定"}


def adb_path() -> str:
    candidate = Path.home() / "Library/Android/sdk/platform-tools/adb"
    return str(candidate) if candidate.exists() else "adb"


ADB = adb_path()


def adb(args: list[str], timeout: int = 30) -> subprocess.CompletedProcess[str]:
    return subprocess.run([ADB, *args], capture_output=True, text=True, timeout=timeout)


def default_apk() -> Path:
    apks = sorted(
        (REPO_ROOT / "app" / "release").glob("*release*.apk"),
        key=lambda p: p.stat().st_mtime,
        reverse=True,
    )
    if not apks:
        raise SystemExit("app/release/ 下没有 release APK，请先 assembleRelease")
    return apks[0]


def dump_ui() -> str:
    adb(["shell", "uiautomator", "dump", "/sdcard/_install_confirm.xml"], timeout=20)
    return adb(["shell", "cat", "/sdcard/_install_confirm.xml"], timeout=20).stdout


def find_confirm_button(xml: str) -> tuple[str, int, int] | None:
    for node in re.finditer(r"<node[^>]*>", xml):
        tag = node.group(0)
        text = re.search(r' text="([^"]*)"', tag)
        bounds = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
        if not text or not bounds:
            continue
        label = html.unescape(text.group(1)).strip()
        if label in CONFIRM_LABELS:
            x1, y1, x2, y2 = map(int, bounds.groups())
            return label, (x1 + x2) // 2, (y1 + y2) // 2
    return None


def main() -> int:
    apk = Path(sys.argv[1]) if len(sys.argv) > 1 else default_apk()
    if not apk.is_file():
        raise SystemExit(f"APK 不存在: {apk}")

    adb(["shell", "input", "keyevent", "KEYCODE_WAKEUP"])
    adb(["shell", "wm", "dismiss-keyguard"])
    adb(["shell", "input", "keyevent", "KEYCODE_HOME"])
    time.sleep(0.4)

    print(f"安装 {apk.name} …", flush=True)
    install = subprocess.Popen(
        [ADB, "install", "-r", str(apk)],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )

    clicks = 0
    deadline = time.time() + 60
    while time.time() < deadline:
        if install.poll() is not None:
            break
        hit = find_confirm_button(dump_ui())
        if hit:
            label, x, y = hit
            adb(["shell", "input", "tap", str(x), str(y)])
            clicks += 1
            print(f"已点击「{label}」({x},{y})", flush=True)
            time.sleep(1.5)
            if clicks >= 3:
                break
        else:
            time.sleep(0.25)

    output, _ = install.communicate(timeout=180)
    print((output or "").strip()[-400:])
    if install.returncode != 0 or "Failure" in (output or ""):
        return 1

    check = adb(["shell", "dumpsys", "package", "space.liushenme.markdownreader"])
    match = re.search(r"versionName=(\S+)", check.stdout)
    print(f"安装成功，当前版本 {match.group(1) if match else '?'}；确认点击 {clicks} 次")
    return 0


if __name__ == "__main__":
    sys.exit(main())
