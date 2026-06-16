#!/bin/bash
# color-break.sh [分钟] — 可控缝隙：临时恢复彩色 N 分钟，到点自动切回黑白。
# 默认 10 分钟。手机要全程连着 adb（否则倒计时结束切不回）。
set -euo pipefail
MIN="${1:-10}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

bash "$HERE/grayscale-off.sh"
echo "彩色 ${MIN} 分钟，到点自动变回黑白。(Ctrl-C 取消会停在彩色)"
trap 'bash "$HERE/grayscale-on.sh"; echo "已切回黑白。"; exit 0' INT
for ((i=MIN*60;i>0;i--)); do printf "\r  剩 %3ds " "$i"; sleep 1; done
printf "\n"
bash "$HERE/grayscale-on.sh"
