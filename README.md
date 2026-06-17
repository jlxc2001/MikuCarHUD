# MikuCarHudReceiver - Audi HUD Clean UI v9

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
