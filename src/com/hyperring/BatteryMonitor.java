package com.hyperring;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;

public class BatteryMonitor {

    public static void init(Context context) {
        queryBatteryHardware();
        registerReceiver(context);
    }

    public static void registerReceiver(Context context) {
        try {
            IntentFilter batFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent intent) {
                    if (intent == null) return;
                    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    if (level >= 0 && scale > 0) {
                        IslandState.batteryPct = Math.round((level / (float) scale) * 100f);
                    }
                    int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    boolean chargingNow = status == BatteryManager.BATTERY_STATUS_CHARGING
                            || status == BatteryManager.BATTERY_STATUS_FULL;

                    if (chargingNow && !IslandState.isCharging && IslandConfig.enableCharging) {
                        IslandState.isCharging = true;
                        readBatteryHardwareTelemetry();
                        HyperRingOverlay.triggerChargingEvent();
                    } else if (chargingNow && IslandState.isCharging) {
                        readBatteryHardwareTelemetry();
                        if (IslandState.currentIsland == IslandState.STATE_CHARGING) {
                            HyperRingOverlay.wakeEngineLoop();
                        }
                    } else if (!chargingNow) {
                        IslandState.isCharging = false;
                        if (IslandState.currentIsland == IslandState.STATE_CHARGING) {
                            HyperRingOverlay.startCollapse();
                        }
                    }
                }
            }, batFilter);
        } catch (Throwable ignored) {}
    }

    public static void queryBatteryHardware() {
        try {
            readBatteryHardwareTelemetry();
            File f = new File("/sys/class/power_supply/battery/status");
            if (f.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    String status = br.readLine();
                    if (status != null) {
                        status = status.trim();
                        boolean charging = "Charging".equalsIgnoreCase(status) || "Full".equalsIgnoreCase(status);
                        if (charging && !IslandState.isCharging && IslandConfig.enableCharging) {
                            IslandState.isCharging = true;
                            HyperRingOverlay.triggerChargingEvent();
                        } else if (!charging && IslandState.isCharging) {
                            IslandState.isCharging = false;
                            if (IslandState.currentIsland == IslandState.STATE_CHARGING) {
                                HyperRingOverlay.startCollapse();
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void readBatteryHardwareTelemetry() {
        try {
            long cap = readLongFromFile("/sys/class/power_supply/battery/capacity");
            if (cap > 0) IslandState.batteryPct = (int) cap;

            long currentUa = readLongFromFile("/sys/class/power_supply/battery/current_now");
            long voltUv = readLongFromFile("/sys/class/power_supply/battery/voltage_now");
            long tempRaw = readLongFromFile("/sys/class/power_supply/battery/temp");

            if (tempRaw > 0) {
                float tempC = tempRaw / 10.0f;
                IslandState.batteryTempStr = String.format(Locale.US, "%.1f°C", tempC);
            }

            if (currentUa != 0 && voltUv > 0) {
                double currentA = Math.abs(currentUa) / 1000000.0;
                double voltV = voltUv / 1000000.0;
                double watts = currentA * voltV;
                IslandState.chargeWattStr = String.format(Locale.US, "%.1fW", watts);
                IslandState.chargeCurrentStr = String.format(Locale.US, "%dmA", Math.round(currentA * 1000.0));
            }
        } catch (Throwable ignored) {}
    }

    private static long readLongFromFile(String path) {
        File f = new File(path);
        if (!f.exists()) return 0L;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String l = br.readLine();
            if (l != null) return Long.parseLong(l.trim());
        } catch (Throwable ignored) {}
        return 0L;
    }
}
