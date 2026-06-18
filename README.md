# 车速HUD显示表 Audi Vector Tach v13

本版本从 v12 备份版分支生成，开始执行“方案 B”：HUD 上半部分完全程序矢量化。

## v13 关键变化

- 已全盘保留 v12 作为回溯备份。
- 不再绘制 `hud_tach_bg.png` 位图背景。
- 厂字型转速表外框、刻度、0-8 数字、红区、`x1000 r/min` 全部由 `AudiHudView` 的 Canvas 矢量绘制。
- 动态转速进度条和静态刻度共用同一套 `RPM_0 / RPM_3 / RPM_8` 几何参数，理论上不会再出现背景和进度条不匹配的问题。
- 继续保留 UDP 接收、ADB 模拟、HUD 镜像、调试模式开关、左上角时间、车速、RPM、续航、转向、车门警告。
- 字体策略继续保持：数字 `0-9` 优先加载 `app/src/main/assets/fonts/hud_oem.ttf`，中文/英文/单位继续使用系统 HUD 字体。

> 注意：项目包内不附带字体文件。需要使用你自己的数字字体时，把字体文件放到 `app/src/main/assets/fonts/hud_oem.ttf` 后再编译。

---

# 车速HUD显示表 - Audi HUD Clean UI v9

安卓 HUD 接收端，用于接收车机端 MikuCarLauncher 在局域网内广播的 UDP JSON 车辆 Hook 数据。

## v9 更新

- 左上角新增时间显示，格式 `HH:mm`。
- 删除 RANGE 下方的 FUEL 百分比显示。
- 设置页新增“调试模式”开关。
- 调试模式开启：显示 UDP 状态、等待车机数据、ODO、底部 packets/sender/seq/debug 等调试信息。
- 调试模式关闭：HUD 只保留核心驾驶显示：背景、厂字型转速进度条、车速、左右转向、开门/后备箱/机盖警告、RPM、剩余续航里程、时间。
- 继续保留 ADB 模拟数据接口。
- 字体渲染为混合字体：数字使用 `assets/fonts/hud_oem.ttf`，中文/英文/单位/符号使用原来的 HUD 字体。

## 字体文件

请将自备数字字体放到：

```text
app/src/main/assets/fonts/hud_oem.ttf
```

如果没有放入该文件，App 会回退到系统字体。

## ADB 模拟示例

```bash
adb shell am start -n com.jlxc.mikucarhudreceiver/.MainActivity -a com.jlxc.mikucarhudreceiver.DEBUG_DATA --ei speedKmh 60 --ei rpm 3000 --ei rangeKm 540
```

模拟左前门打开：

```bash
adb shell am broadcast -a com.jlxc.mikucarhudreceiver.DEBUG_DATA --ez frontLeft true
```

模拟双闪：

```bash
adb shell am broadcast -a com.jlxc.mikucarhudreceiver.DEBUG_DATA --ez hazard true
```

## 构建

上传到 GitHub 后，Actions 会自动执行：

```bash
./gradlew assembleDebug
```

最低支持：Android 6.0 / API 23。

## v29 Launcher / 桌面模式

本版本已在 `AndroidManifest.xml` 中为 `MainActivity` 增加：

- `android.intent.category.HOME`
- `android.intent.category.DEFAULT`

因此系统可以把本 App 作为默认桌面 / Launcher 候选项。

设置方法：
1. 打开 App，长按进入设置页。
2. 点击“设置为默认桌面 / Launcher”。
3. 在系统默认桌面设置中选择“车速HUD显示表”。

注意：Android 不允许普通 App 直接强制把自己设为默认桌面，必须由用户在系统界面里确认。
