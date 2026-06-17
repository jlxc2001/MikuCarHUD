#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
在电脑上向 HUD 接收端发送测试 UDP 包。

示例：
  python tools/send_test_packet.py 192.168.100.127 36970
  python tools/send_test_packet.py 255.255.255.255 36970 --broadcast
"""
import argparse
import json
import socket
import time


def build_packet(seq: int) -> dict:
    phase = seq % 12
    speed = (seq * 7) % 121
    rpm = 780 + (speed * 38)
    return {
        "protocol": "MikuCarHUD",
        "version": 1,
        "source": "MikuCarLauncher",
        "seq": seq,
        "timestampElapsedMs": int(time.monotonic() * 1000),
        "valid": True,
        "speedKmh": speed,
        "rpm": rpm,
        "rangeKm": max(0, 540 - seq),
        "fuelLevel": max(0, 90 - seq % 91),
        "totalMileageKm": 188888 + seq,
        "driverSeatbelt": phase != 9,
        "passengerSeatbelt": True,
        "doors": {
            "frontLeft": phase == 7,
            "frontRight": False,
            "rearLeft": False,
            "rearRight": False,
            "trunk": phase == 8,
            "hood": False,
        },
        "leftTurn": phase in (2, 3),
        "rightTurn": phase in (4, 5),
        "highBeam": phase == 10,
        "hazard": phase == 6,
        "frontRadar": [120, 90, 70, 120] if phase == 11 else [],
        "rearRadar": [60, 80, 80, 60] if phase == 1 else [],
        "rawBaseInfo": ["demo", seq, speed, rpm],
        "dataSource": "python-test-sender",
        "debugText": f"phase={phase}",
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("host", help="HUD 手机 IP，或 255.255.255.255 广播")
    parser.add_argument("port", nargs="?", type=int, default=36970)
    parser.add_argument("--broadcast", action="store_true", help="允许发送 UDP 广播")
    parser.add_argument("--interval", type=float, default=0.5, help="发送间隔，单位秒")
    args = parser.parse_args()

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    if args.broadcast:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

    seq = 1
    print(f"Sending UDP JSON to {args.host}:{args.port}, Ctrl+C to stop")
    try:
        while True:
            packet = build_packet(seq)
            payload = json.dumps(packet, ensure_ascii=False).encode("utf-8")
            sock.sendto(payload, (args.host, args.port))
            print(f"sent seq={seq} speed={packet['speedKmh']} rpm={packet['rpm']}")
            seq += 1
            time.sleep(args.interval)
    except KeyboardInterrupt:
        print("stopped")


if __name__ == "__main__":
    main()
