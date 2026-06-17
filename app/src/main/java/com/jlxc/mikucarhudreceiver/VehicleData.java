package com.jlxc.mikucarhudreceiver;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VehicleData {
    public String protocol;
    public int version;
    public String source;
    public long seq;
    public long timestampElapsedMs;
    public boolean valid;

    public int speedKmh;
    public int rpm;
    public int rangeKm;
    public int fuelLevel;
    public long totalMileageKm;

    public boolean driverSeatbelt;
    public boolean passengerSeatbelt;

    public final Doors doors = new Doors();

    public boolean leftTurn;
    public boolean rightTurn;
    public boolean highBeam;
    public boolean hazard;

    public final List<Integer> frontRadar = new ArrayList<>();
    public final List<Integer> rearRadar = new ArrayList<>();
    public final List<String> rawBaseInfo = new ArrayList<>();

    public String dataSource = "";
    public String debugText = "";
    public String rawJson = "";

    public static VehicleData fromJson(JSONObject json) {
        VehicleData data = new VehicleData();
        data.protocol = json.optString("protocol", "");
        data.version = json.optInt("version", -1);
        data.source = json.optString("source", "");
        data.seq = json.optLong("seq", 0L);
        data.timestampElapsedMs = json.optLong("timestampElapsedMs", 0L);
        data.valid = json.optBoolean("valid", false);

        data.speedKmh = json.optInt("speedKmh", 0);
        data.rpm = json.optInt("rpm", 0);
        data.rangeKm = json.optInt("rangeKm", 0);
        data.fuelLevel = json.optInt("fuelLevel", 0);
        data.totalMileageKm = json.optLong("totalMileageKm", 0L);

        data.driverSeatbelt = json.optBoolean("driverSeatbelt", true);
        data.passengerSeatbelt = json.optBoolean("passengerSeatbelt", true);

        JSONObject doorsObj = json.optJSONObject("doors");
        if (doorsObj != null) {
            data.doors.frontLeft = doorsObj.optBoolean("frontLeft", false);
            data.doors.frontRight = doorsObj.optBoolean("frontRight", false);
            data.doors.rearLeft = doorsObj.optBoolean("rearLeft", false);
            data.doors.rearRight = doorsObj.optBoolean("rearRight", false);
            data.doors.trunk = doorsObj.optBoolean("trunk", false);
            data.doors.hood = doorsObj.optBoolean("hood", false);
        }

        data.leftTurn = json.optBoolean("leftTurn", false);
        data.rightTurn = json.optBoolean("rightTurn", false);
        data.highBeam = json.optBoolean("highBeam", false);
        data.hazard = json.optBoolean("hazard", false);

        copyIntArray(json.optJSONArray("frontRadar"), data.frontRadar);
        copyIntArray(json.optJSONArray("rearRadar"), data.rearRadar);
        copyStringArray(json.optJSONArray("rawBaseInfo"), data.rawBaseInfo);

        data.dataSource = json.optString("dataSource", "");
        data.debugText = json.optString("debugText", "");
        data.rawJson = json.toString();
        return data;
    }

    public boolean isProtocolAccepted() {
        return "MikuCarHUD".equals(protocol)
                && version == 1
                && "MikuCarLauncher".equals(source);
    }

    public boolean hasAnyWarning() {
        return doors.anyOpen() || !driverSeatbelt || !passengerSeatbelt || highBeam;
    }

    public String buildWarningText() {
        ArrayList<String> warnings = new ArrayList<>();
        if (doors.frontLeft) warnings.add("左前门打开");
        if (doors.frontRight) warnings.add("右前门打开");
        if (doors.rearLeft) warnings.add("左后门打开");
        if (doors.rearRight) warnings.add("右后门打开");
        if (doors.trunk) warnings.add("后备箱打开");
        if (doors.hood) warnings.add("机盖打开");
        if (!driverSeatbelt) warnings.add("主驾未系安全带");
        if (!passengerSeatbelt) warnings.add("副驾未系安全带");
        if (highBeam) warnings.add("远光灯开启");
        if (warnings.isEmpty()) {
            return "车辆状态正常";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < warnings.size(); i++) {
            if (i > 0) builder.append("  |  ");
            builder.append(warnings.get(i));
        }
        return builder.toString();
    }

    public String radarSummary() {
        return String.format(Locale.CHINA,
                "前雷达:%s  后雷达:%s",
                listToText(frontRadar), listToText(rearRadar));
    }

    private static String listToText(List<Integer> values) {
        if (values.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(values.get(i));
        }
        sb.append(']');
        return sb.toString();
    }

    private static void copyIntArray(JSONArray array, List<Integer> out) {
        out.clear();
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            out.add(array.optInt(i));
        }
    }

    private static void copyStringArray(JSONArray array, List<String> out) {
        out.clear();
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            Object value = array.opt(i);
            out.add(String.valueOf(value));
        }
    }

    public static class Doors {
        public boolean frontLeft;
        public boolean frontRight;
        public boolean rearLeft;
        public boolean rearRight;
        public boolean trunk;
        public boolean hood;

        public boolean anyOpen() {
            return frontLeft || frontRight || rearLeft || rearRight || trunk || hood;
        }
    }
}
