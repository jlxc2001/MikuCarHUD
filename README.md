# MikuCarHudReceiver

安卓 HUD 接收端 Demo，用于接收车机端 `MikuCarLauncher` 在局域网内广播的车辆 Hook 数据。

当前版本重点是 **UDP 数据接收跑通**，界面只是黑底调试 HUD，不做最终视觉设计。

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
│     │  ├─ HudDebugView.java
│     │  ├─ VehicleData.java
│     │  └─ AppPrefs.java
│     └─ res/
├─ .github/workflows/android.yml
├─ docs/sample_packet.json
└─ tools/send_test_packet.py
```

## 本地测试 UDP 接收

1. 手机和电脑连接同一个热点或同一个局域网。
2. 手机安装 APK 并打开 `Miku HUD接收端`。
3. 确认主界面右上角显示 `UDP:36970`。
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

如果手机画面中的 `packets` 增长，并且车速/RPM 在变化，说明 UDP 接收链路已跑通。

## GitHub Actions 编译

把整个项目上传到 GitHub 后，进入仓库的 `Actions` 页面，手动运行 `Android APK Build`。

编译完成后在 Artifacts 里下载：

```text
MikuCarHudReceiver-debug-apk
```

## 后续可以继续加的内容

- 最终版 HUD 厂字型转速表 UI
- 更完整的转向、远光、雷达图标
- 断联后更漂亮的黑屏待机模式
- 数据包日志导出
- 自动发现车机端广播源 IP
- 与 MikuCarLauncher 的同屏调试页面联动
