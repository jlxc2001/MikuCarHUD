@echo off
REM MikuCar HUD 接收端 ADB 模拟数据示例
REM 用法：双击或在 cmd 中运行。手机需要已连接 ADB，并安装/打开 HUD 接收端。

adb shell am start -n com.jlxc.mikucarhudreceiver/.MainActivity -a com.jlxc.mikucarhudreceiver.DEBUG_DATA ^
  --ei speedKmh 68 ^
  --ei rpm 3200 ^
  --ei rangeKm 540 ^
  --ei fuelLevel 72 ^
  --el totalMileageKm 123456 ^
  --ez driverSeatbelt true ^
  --ez passengerSeatbelt true ^
  --ez leftTurn false ^
  --ez rightTurn false ^
  --ez hazard false ^
  --ez highBeam false ^
  --ez frontLeft false ^
  --ez frontRight false ^
  --ez rearLeft false ^
  --ez rearRight false ^
  --ez trunk false ^
  --ez hood false ^
  --eia frontRadar 80,120,255,255 ^
  --eia rearRadar 60,90,255,255 ^
  --es debugText "ADB mock from bat"
