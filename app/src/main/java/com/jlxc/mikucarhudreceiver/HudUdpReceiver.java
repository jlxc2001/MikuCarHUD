package com.jlxc.mikucarhudreceiver;

import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class HudUdpReceiver {
    public interface Listener {
        void onVehicleData(VehicleData data, String senderAddress);
        void onStatus(String message);
        void onInvalidPacket(String reason, String payloadPreview);
    }

    private final Listener listener;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private DatagramSocket socket;
    private Thread workerThread;
    private int port;

    public HudUdpReceiver(int port, Listener listener) {
        this.port = port;
        this.listener = listener;
    }

    public synchronized void start() {
        if (running.get()) {
            return;
        }
        running.set(true);
        workerThread = new Thread(this::loop, "MikuCarHUD-UDP-Receiver");
        workerThread.start();
    }

    public synchronized void stop() {
        running.set(false);
        if (socket != null) {
            socket.close();
            socket = null;
        }
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
    }

    public int getPort() {
        return port;
    }

    private void loop() {
        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.setBroadcast(true);
            socket.bind(new InetSocketAddress("0.0.0.0", port));
            socket.setSoTimeout(500);
            safeStatus("正在监听 UDP " + port);

            byte[] buffer = new byte[64 * 1024];
            while (running.get()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                    handlePacket(packet);
                } catch (java.net.SocketTimeoutException ignored) {
                    // 用超时让线程可以及时响应 stop()。
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                safeStatus("UDP监听失败: " + e.getMessage());
            }
        } finally {
            if (socket != null) {
                socket.close();
                socket = null;
            }
            safeStatus("UDP监听已停止");
        }
    }

    private void handlePacket(DatagramPacket packet) {
        String payload = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
        if (payload.isEmpty()) {
            safeInvalid("空数据包", "");
            return;
        }
        try {
            JSONObject json = new JSONObject(payload);
            VehicleData data = VehicleData.fromJson(json);
            if (!data.isProtocolAccepted()) {
                safeInvalid("协议字段不匹配", preview(payload));
                return;
            }
            data.rawJson = payload;
            String sender = packet.getAddress() == null ? "unknown" : packet.getAddress().getHostAddress();
            if (listener != null) {
                listener.onVehicleData(data, sender + ":" + packet.getPort());
            }
        } catch (Exception e) {
            safeInvalid("JSON解析失败: " + e.getMessage(), preview(payload));
        }
    }

    private void safeStatus(String message) {
        if (listener != null) {
            listener.onStatus(message);
        }
    }

    private void safeInvalid(String reason, String preview) {
        if (listener != null) {
            listener.onInvalidPacket(reason, preview);
        }
    }

    private static String preview(String value) {
        if (value == null) return "";
        String oneLine = value.replace('\n', ' ').replace('\r', ' ');
        if (oneLine.length() <= 180) return oneLine;
        return oneLine.substring(0, 180) + "...";
    }
}
