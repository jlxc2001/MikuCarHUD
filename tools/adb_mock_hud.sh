#!/usr/bin/env bash
# MikuCar HUD 接收端 ADB 模拟数据示例
# 用法：bash tools/adb_mock_hud.sh

adb shell am start -n com.jlxc.mikucarhudreceiver/.MainActivity -a com.jlxc.mikucarhudreceiver.DEBUG_DATA \
  --ei speedKmh 68 \
  --ei rpm 3200 \
  --ei rangeKm 540 \
  --ei fuelLevel 72 \
  --el totalMileageKm 123456 \
  --ez driverSeatbelt true \
  --ez passengerSeatbelt true \
  --ez leftTurn false \
  --ez rightTurn false \
  --ez hazard false \
  --ez highBeam false \
  --ez frontLeft false \
  --ez frontRight false \
  --ez rearLeft false \
  --ez rearRight false \
  --ez trunk false \
  --ez hood false \
  --eia frontRadar 80,120,255,255 \
  --eia rearRadar 60,90,255,255 \
  --es debugText "ADB mock from shell"
