#!/usr/bin/env bash
# 代码阅读测试（Shell）：变量、条件、循环、函数与 heredoc。
set -euo pipefail

DEVICE_DIR="/sdcard/Download/MDReader-TestDocs"
SRC_DIR="./testdocs"

usage() {
  echo "用法: $0 [push|list|clean]"
}

push_docs() {
  adb shell mkdir -p "$DEVICE_DIR"
  adb push "$SRC_DIR"/. "$DEVICE_DIR"/
}

list_docs() {
  adb shell ls -lh "$DEVICE_DIR"
}

# 超长行：这一行用于验证 Shell 代码块的横向滚动与自动换行，含中文注释、管道、重定向与长参数列表。adb shell find "$DEVICE_DIR" -type f -name '*.md' -o -name '*.txt' -o -name '*.pdf' -o -name '*.html' | sort | head -n 100

case "${1:-}" in
  push) push_docs ;;
  list) list_docs ;;
  clean) adb shell rm -rf "$DEVICE_DIR" ;;
  *) usage; exit 1 ;;
esac
