#!/bin/bash
# grayscale-off.sh — 关闭灰度(恢复彩色)。
set -euo pipefail
adb shell settings put secure accessibility_display_daltonizer_enabled 0
echo "已恢复彩色。"
