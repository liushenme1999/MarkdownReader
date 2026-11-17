#!/usr/bin/env python3
"""发版冒烟：在真机上依次打开各格式样本书，检查崩溃 / ANR。

用法：
  python3 scripts/release_smoke.py                # 默认 7 本样本
  python3 scripts/release_smoke.py --books 01,02  # 只跑指定前缀
  python3 scripts/release_smoke.py --no-launch    # 不启动应用（已在阅读页时）

退出码：0 全部通过；1 有失败（崩溃/ANR/书找不到）。
"""
import argparse
import re
import subprocess
import sys
import time

PKG = "space.liushenme.markdownreader"
DEFAULT_BOOKS = ["01", "02", "03", "04", "05", "15", "20"]


def adb(*args, timeout=40):
    return subprocess.run(
        ["adb", *args],
        capture_output=True,
        text=True,
        errors="replace",
        timeout=timeout,
    )


def dump_ui(tag="smoke"):
    adb("shell", "uiautomator", "dump", f"/sdcard/{tag}.xml")
    return adb("shell", "cat", f"/sdcard/{tag}.xml").stdout


def find_bounds(xml, predicate):
    for match in re.finditer(r"<node[^>]*>", xml):
        node = match.group(0)
        text = re.search(r' text="([^"]*)"', node)
        desc = re.search(r'content-desc="([^"]*)"', node)
        bounds = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if not bounds:
            continue
        label = (desc.group(1) if desc else "") or (text.group(1) if text else "")
        if predicate(label.strip()):
            x1, y1, x2, y2 = map(int, bounds.groups())
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def tap(x, y, wait=0.8):
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)


def swipe(x1, y1, x2, y2, ms=300, wait=0.6):
    adb("shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(ms))
    time.sleep(wait)


def open_book(prefix):
    """通过书架搜索（ASCII 前缀）打开一本书。返回是否成功。"""
    # 切到书架
    for _ in range(3):
        xml = dump_ui("smoke_shelf")
        if find_bounds(xml, lambda s: s == "书架"):
            break
        tap(590, 2550, 0.8)
    xml = dump_ui("smoke_shelf2")
    search = find_bounds(
        xml,
        lambda s: s == "搜索",
    ) or find_bounds(xml, lambda s: "搜索书名" in s)
    if not search:
        return False
    tap(*search, wait=1.2)
    adb("shell", "input", "text", prefix)
    time.sleep(1.8)
    xml = dump_ui("smoke_result")
    card = find_bounds(xml, lambda s: s.startswith(prefix + "-") or s.startswith(prefix))
    if not card:
        return False
    tap(*card, wait=6.0)
    return True


def leave_reader():
    xml = dump_ui("smoke_reader")
    back = find_bounds(xml, lambda s: s == "返回") or find_bounds(xml, lambda s: s == "0")
    if back:
        tap(*back, wait=2.0)
    else:
        adb("shell", "input", "keyevent", "4")
        time.sleep(2.0)


def crash_report():
    """只看本包的崩溃/ANR 日志。

    crash buffer 里也会混入 uiautomator 等系统/测试进程的 FATAL，
    按「FATAL EXCEPTION 块 + 本包名」过滤，避免误报。
    """
    crash = adb("logcat", "-d", "-b", "crash", timeout=60).stdout
    fatal = []
    for block in re.split(r"(?=FATAL EXCEPTION)", crash):
        if "FATAL EXCEPTION" in block and PKG in block:
            fatal.extend(block.strip().splitlines()[:20])
    anr = adb("logcat", "-d", timeout=60).stdout
    anr_lines = [line for line in anr.splitlines() if ("ANR in " + PKG) in line]
    return fatal, anr_lines


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--books", default=",".join(DEFAULT_BOOKS))
    parser.add_argument("--no-launch", action="store_true")
    args = parser.parse_args()
    books = [b.strip() for b in args.books.split(",") if b.strip()]

    if adb("devices").stdout.count("device") < 2:
        print("没有已连接的设备")
        return 1
    adb("logcat", "-c")
    if not args.no_launch:
        adb("shell", "am", "force-stop", PKG)
        adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
        time.sleep(4)

    results = []
    for prefix in books:
        opened = open_book(prefix)
        if opened:
            # 每种格式都做一次滚动/翻页与一次返回，覆盖手势路径
            swipe(600, 2000, 600, 900, 300)
            swipe(600, 900, 600, 2000, 300)
            swipe(1000, 1300, 250, 1300, 250)
            leave_reader()
        results.append((prefix, opened))
        print(f"{prefix}: {'OK' if opened else 'NOT FOUND'}")

    fatal, anr = crash_report()
    print("\n== 结果 ==")
    for prefix, opened in results:
        print(f"  {prefix}: {'通过' if opened else '未找到书籍'}")
    if fatal:
        print("\n崩溃日志：")
        print("\n".join(fatal[:20]))
    if anr:
        print("\nANR 日志：")
        print("\n".join(anr[:10]))
    ok = all(opened for _, opened in results) and not fatal and not anr
    print("\n冒烟结果:", "PASS" if ok else "FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
