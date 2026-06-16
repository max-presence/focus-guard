# Focus Guard — Android 黑白守护 APK

真灰度（系统 `accessibility_display_daltonizer`）= 强制黑白，**被关自动拉回**，并提供「彩色 N 分钟自动收回」的可控缝隙。日常**不用 adb**——adb 只在安装那一次出现。

## 为什么需要一次性 adb

系统灰度开关受 `WRITE_SECURE_SETTINGS` 保护（signature 级），任何在手机设置里能授的权限都碰不到它。所以装好后**用电脑连一次**授权，之后这个 app 自己长期使用，永不再连。
（纯靠悬浮窗做的"灰度"app 是障眼法——叠加层只能压暗/染色，无法真去色。）

## 构建

需要 Android Studio（或装了 Android SDK 的命令行）。本工程不含 gradle wrapper jar，Android Studio 打开会自动补；命令行可先 `gradle wrapper`。

```bash
# Android Studio: Open 这个 android-app 目录，等同步完点 Run
# 或命令行（已装 SDK + 生成 wrapper 后）:
./gradlew assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

## 安装与一次性授权

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
# 关键一次性授权（之后永不再用 adb）:
adb shell pm grant app.focusguard android.permission.WRITE_SECURE_SETTINGS
```

打开 app：顶部显示「✅ 权限已授予」即成功。点「开启黑白守护」。

## 用法

- **开启黑白守护**：常驻前台服务，开机自启，被关自动拉回。
- **彩色 10 分钟**：通知栏或主界面一键；到点自动变回黑白（缝隙）。
- **下拉磁贴**：系统快捷设置里添加「黑白守护」磁贴，一键开关。

## vivo / OriginOS 注意

1. **后台杀进程很猛**：要让守护活着，去「设置→电池/应用管理」给 Focus Guard 开**自启动**、**允许后台运行**、**不优化电池**。否则服务被杀，看门狗就停了。
2. **灰度键值可能不同**：`GrayscaleController.kt` 用 `accessibility_display_daltonizer=0`（MONOCHROMACY）。若你的 OriginOS 全灰效果不对，真机用 `adb shell settings get secure accessibility_display_daltonizer` 看系统自带灰度开启后的取值，再改常量。
3. 型号/系统版本先告诉我，我可以预调。

## 文件

```
app/src/main/
  AndroidManifest.xml
  java/app/focusguard/
    GrayscaleController.kt   # 读写系统灰度 secure setting
    GuardService.kt          # 前台服务: 看门狗 + 彩色缝隙
    MainActivity.kt          # 状态 + 授权指引 + 三个按钮
    BootReceiver.kt          # 开机自启
    GrayscaleTileService.kt  # 下拉快捷磁贴
  res/...
```
