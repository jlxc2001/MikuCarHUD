# MikuCarHudReceiver

安卓 HUD 接收端，用于接收车机端 `MikuCarLauncher` 在局域网内广播的车辆 Hook 数据。

当前版本已经从纯调试界面升级为 **奥迪厂字型 HUD UI V1**：使用用户提供的厂字型转速表图片作为背景，并在其上叠加实时车辆数据。

## 已实现

- 横屏全屏运行
- 默认监听 UDP `36970`
- 接收 UTF-8 JSON
- 校验协议字段：
  - `protocol = "MikuCarHUD"`
  - `version = 1`
  - `source = "MikuCarLauncher"`
- 解析并显示：
  - `speedKmh`
  - `rpm`
  - `rangeKm`
  - `fuelLevel`
  - `totalMileageKm`
  - `driverSeatbelt` / `passengerSeatbelt`
  - `doors.frontLeft / frontRight / rearLeft / rearRight / trunk / hood`
  - `leftTurn / rightTurn / hazard / highBeam`
  - `frontRadar / rearRadar`
  - `dataSource / debugText`
- 奥迪厂字型转速表背景
- 根据 `rpm` 在厂字型路径上绘制动态转速进度
  - 0-3：左侧斜坡段
  - 3-8：顶部横向段
  - 6500rpm 之后进入红色进度
- 中央大号显示车速 `speedKmh`
- 显示 RPM、续航、油量、总里程
- 左右转向闪烁箭头
- 双闪时左右箭头同时闪烁
- 车门、后备箱、机盖、安全带、远光灯警告文字
- 超过 2 秒未收到数据时显示“等待车机数据”，保留最后一次车速
- 长按主界面进入设置页
- 设置页支持：
  - 修改 UDP 监听端口
  - 开关 HUD 水平镜像
  - 调节字体大小
  - 调节屏幕亮度
- GitHub Actions 自动编译 debug APK

## 项目结构

```text
MikuCarHudReceiver/
├─ app/
│  ├─ build.gradle
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/com/jlxc/mikucarhudreceiver/
│     │  ├─ MainActivity.java
│     │  ├─ SettingsActivity.java
│     │  ├─ HudUdpReceiver.java
│     │  ├─ AudiHudView.java
│     │  ├─ HudDebugView.java
│     │  ├─ VehicleData.java
│     │  └─ AppPrefs.java
│     └─ res/
│        └─ drawable-nodpi/hud_tach_bg.png
├─ .github/workflows/android.yml
├─ docs/sample_packet.json
└─ tools/send_test_packet.py
```

## 本地测试 UDP 接收

1. 手机和电脑连接同一个热点或同一个局域网。
2. 手机安装 APK 并打开 `Miku HUD接收端`。
3. 确认主界面显示正在监听 UDP 端口。
4. 在电脑上执行：

```bash
python tools/send_test_packet.py 手机IP 36970
```

例如：

```bash
python tools/send_test_packet.py 192.168.100.127 36970
```

广播测试：

```bash
python tools/send_test_packet.py 255.255.255.255 36970 --broadcast
```

如果手机画面中的 `packets` 增长，并且车速/RPM 在变化，说明 UDP 接收链路正常。

## GitHub Actions 编译

把整个项目上传到 GitHub 后，进入仓库的 `Actions` 页面，手动运行 `Android APK Build`。

编译完成后在 Artifacts 里下载：

```text
MikuCarHudReceiver-debug-apk
```

## 调整厂字型转速条位置

动态转速进度的路径点在：

```text
app/src/main/java/com/jlxc/mikucarhudreceiver/AudiHudView.java
```

修改这三个坐标即可：

```java
private static final PointF RPM_0 = new PointF(150f, 672f);
private static final PointF RPM_3 = new PointF(488f, 344f);
private static final PointF RPM_8 = new PointF(1576f, 345f);
```

坐标基于背景图原始尺寸 `1672 × 941`。

## 后续可以继续加的内容

- 更接近奥迪原厂风格的车速字体
- 转速条按刻度逐格点亮，而不是整条线点亮
- 雷达距离条可视化
- 车门开启图标化
- 设置页里加入“隐藏底部调试栏”
- 夜间亮度自动降低

## ADB 模拟 HUD 数据

本版本预留了 ADB 调试模拟接口，方便在 UI 阶段不依赖车机 UDP 广播也能直接改车速、转速、油量、车门、转向灯等数据。

调试 Action：

```text
com.jlxc.mikucarhudreceiver.DEBUG_DATA
```

### 方式一：直接启动/拉起 App 并写入模拟数据

```bash
adb shell am start -n com.jlxc.mikucarhudreceiver/.MainActivity -a com.jlxc.mikucarhudreceiver.DEBUG_DATA \
  --ei speedKmh 68 \
  --ei rpm 3200 \
  --ei rangeKm 540 \
  --ei fuelLevel 72 \
  --el totalMileageKm 123456 \
  --ez leftTurn false \
  --ez rightTurn false \
  --ez hazard false \
  --ez highBeam false \
  --ez driverSeatbelt true \
  --ez passengerSeatbelt true \
  --ez frontLeft false \
  --ez frontRight false \
  --ez rearLeft false \
  --ez rearRight false \
  --ez trunk false \
  --ez hood false \
  --eia frontRadar 80,120,255,255 \
  --eia rearRadar 60,90,255,255 \
  --es debugText "ADB mock"
```

Windows CMD 可以直接运行：

```bat
工具目录：tools\adb_mock_hud.bat
```

macOS / Linux 可以运行：

```bash
bash tools/adb_mock_hud.sh
```

### 方式二：App 已经打开时，用 broadcast 直接刷新数据

```bash
adb shell am broadcast -a com.jlxc.mikucarhudreceiver.DEBUG_DATA --ei rpm 5000 --ei speedKmh 88 --ei fuelLevel 60
```

这种方式不会重启界面，更适合连续调 UI。

### 常用模拟命令

模拟 3000 转、60km/h：

```bash
adb shell am broadcast -a com.jlxc.mikucarhudreceiver.DEBUG_DATA --ei speedKmh 60 --ei rpm 3000
```

模拟红区转速：

```bash
adb shell am broadcast -a com.jlxc.mikucarhudreceiver.DEBUG_DATA --ei rpm 7000
```

模拟左转向：

```bash
adb shell am broadcast -a com.jlxc.mikucarhudreceiver.DEBUG_DATA --ez leftTurn true --ez rightTurn false --ez hazard false
```

关闭转向灯：

```bash
adb shell am broadcast -a com.jlxc.mikucarhudreceiver.DEBUG_DATA --ez leftTurn false --ez rightTurn false --ez hazard false
```

模拟双闪：

```bash
adb shell am broadcast -a com.jlxc.mikucarhudreceiver.DEBUG_DATA --ez hazard true
```

模拟左前门打开：

```bash
adb shell am broadcast -a com.jlxc.mikucarhudreceiver.DEBUG_DATA --ez frontLeft true
```

关闭所有车门/后备箱/机盖警告：

```bash
adb shell am broadcast -a com.jlxc.mikucarhudreceiver.DEBUG_DATA --ez frontLeft false --ez frontRight false --ez rearLeft false --ez rearRight false --ez trunk false --ez hood false
```

模拟主驾未系安全带：

```bash
adb shell am broadcast -a com.jlxc.mikucarhudreceiver.DEBUG_DATA --ez driverSeatbelt false
```

### 支持的 ADB extra 字段

- 整数：`speedKmh`、`rpm`、`rangeKm`、`fuelLevel`
- 长整数：`totalMileageKm`、`seq`
- 布尔：`driverSeatbelt`、`passengerSeatbelt`、`leftTurn`、`rightTurn`、`highBeam`、`hazard`
- 车门：`frontLeft`、`frontRight`、`rearLeft`、`rearRight`、`trunk`、`hood`
- 车门兼容 key：`doors.frontLeft`、`doors.frontRight`、`doors.rearLeft`、`doors.rearRight`、`doors.trunk`、`doors.hood`
- 整数数组：`frontRadar`、`rearRadar`
- 字符串数组：`rawBaseInfo`
- 字符串：`dataSource`、`debugText`
- 完整 JSON：`json`

如果只传某几个字段，其他字段会沿用上一次数据，适合一点点微调 UI。
