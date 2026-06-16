# Focus Guard

个人专注 / 反 doom-scroll 工具。

三个模块：A) Mac 域名屏蔽　B) 焦躁急救出口　C) 手机黑白强制。

核心原则：不是把多巴胺清零，而是把「无限下拉的消费」换成「会自然结束、最好能产出东西」的出口；纪律要可持续，所以故意留可控的缝隙。

## 安装

```bash
# 把 bin 加进 PATH（写到 ~/.zshrc）
echo 'export PATH="$HOME/Documents/Project/focus-guard/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

## A. Mac 域名屏蔽（hosts）

```bash
sudo focus-block      # 开启屏蔽（读 blocklist.txt 写入 /etc/hosts）
focus-status          # 看当前状态（只读，免 sudo）
sudo focus-unblock    # 解除（有 60s 冷静等待）
sudo focus-unblock --now   # 跳过等待
```

- 改屏蔽名单：编辑 `blocklist.txt`（每行一个根域名，自动加 www./m. 变体），重跑 `sudo focus-block`。
- 首次会把原 `/etc/hosts` 备份到 `/etc/hosts.focusguard-bak`。
- 想要"启动后无法中途解除"的更狠版本，直接装 SelfControl，二选一。

### 自动按时段开启（可选）

`launchd/com.max.focusguard.plist` = 工作日 09:00 自动开启屏蔽（故意不自动解除，保留摩擦）。安装见该文件头部注释。

## B. 焦躁急救出口

```bash
focus-pick       # 随机给一个会结束/产出型的出口（围棋/书法/三国剪辑/速记…）
focus-pick -l    # 列出全部
```

- 编辑 `alternatives.txt` 增删条目（`标签 | 命令`，命令留空=线下动作）。⭐ 是你的偏好，多列几行即加权。
- **待你填**：把"三国剪辑"那行的 `REPLACE_WITH_YOUR_PLAYLIST_URL` 换成你自己的收藏夹/playlist，避免点进算法推荐流。
- 建议绑个全局快捷键（Raycast/Hammerspoon Script Command → `focus-pick`），焦躁时一键唤起。

## C. 手机黑白强制（vivo / Android，需 adb）

```bash
brew install android-platform-tools   # 先装 adb（当前机器还没有）
# 手机开"USB/无线调试"，adb devices 能看到设备后：
bash android/grayscale-on.sh          # 开灰度
bash android/grayscale-off.sh         # 恢复彩色
bash android/color-break.sh 10        # 缝隙：彩色 10 分钟后自动切回黑白
```

- 底层用 `accessibility_display_daltonizer`（=0 多为全灰单色）。**vivo OriginOS 键名/取值可能不同，要真机实测**；不行就改用系统自带「灰度/单色」+ 数字健康，或后续做 APK 自动巡检。
- `color-break.sh` 期间手机要全程连着 adb，否则倒计时结束切不回。

## 路线 / 待办

- [ ] B: 填三国剪辑 playlist URL + 绑 Raycast 快捷键
- [ ] C: 真机确认 vivo daltonizer 键名（型号/系统版本？）
- [ ] C(可选): 写 APK 做"自动巡检灰度 + 倒计时缝隙"，免每次连电脑
- [ ] A(可选): 评估是否换 SelfControl（无法中途解除）
