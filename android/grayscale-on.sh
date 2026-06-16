#!/bin/bash
# grayscale-on.sh — 开启手机灰度(模拟单色)。需先连好 adb。
# vivo OriginOS 键名/取值可能不同，要真机实测；不行就试 §README 的备选。
set -euo pipefail
adb shell settings put secure accessibility_display_daltonizer_enabled 1
adb shell settings put secure accessibility_display_daltonizer 0
echo "已开启灰度。"
