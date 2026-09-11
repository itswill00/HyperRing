package com.hyperring;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;

public class IslandConfig {
    public static String stateDir = "/data/adb/modules/hyperring/state";
    public static String configPath = stateDir + "/config.json";
    public static String statusPath = stateDir + "/status.json";
    public static String triggerPath = stateDir + "/trigger.cmd";

    public static int displayWidthPx = 1080;
    public static int displayHeightPx = 2400;
    public static float displayDensity = 2.75f;

    // Geometry configuration
    public static int cutoutCenterX = 0;
    public static int cutoutCenterY = 0;
    public static int cutoutRadius = 0;
    public static int xOffset = 0;
    public static int yOffset = 0;
    public static int customPillWidth = 0;
    public static int customPillHeight = 0;
    public static int customCardWidth = 0;
    public static int customCardHeight = 0;
    public static int cardRadius = 24;
    public static int cardYOffset = 0;
    public static String cardPositionMode = "below";
    public static String pillAlignment = "center";
    public static boolean notchMode = false;

    // Feature toggles
    public static boolean masterEnabled = true;
    public static boolean enableMedia = true;
    public static boolean mediaShowPillArt = true;
    public static String mediaArtStyle = "rounded";
    public static boolean mediaShowWaveform = true;
    public static String mediaPulseColor = "auto";
    public static boolean mediaAmbientGlow = true;
    public static int mediaGlowOpacity = 25;
    public static boolean mediaMarquee = true;
    public static boolean enableCharging = true;
    public static boolean enableVolume = true;
    public static boolean enableRinger = true;
    public static boolean enableNotifications = true;
    public static boolean enableProgress = true;
    public static boolean enableTorch = true;
    public static boolean enableHaptics = false;
    public static boolean stealthRingIdle = false;
    public static boolean hideInLandscape = true;

    // Animation physics
    public static float springStiffness = 380.0f;
    public static float springDamping = 0.78f;
    public static boolean autoExpandCharging = true;
    public static boolean autoExpandMedia = false;
    public static boolean autoExpandNotif = false;
    public static boolean autoExpandProgress = false;
    public static int expandTimeoutMs = 3500;

    public static float dpToPx(float dp) {
        return dp * displayDensity;
    }

    public static float spToPx(float sp) {
        return sp * displayDensity;
    }

    public static void readConfig() {
        File file = new File(configPath);
        if (!file.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            String json = sb.toString();

            masterEnabled = parseBool(json, "enabled", masterEnabled);
            cutoutCenterX = parseInt(json, "cutout_x", cutoutCenterX);
            cutoutCenterY = parseInt(json, "cutout_y", cutoutCenterY);
            cutoutRadius = parseInt(json, "cutout_radius", cutoutRadius);
            pillAlignment = parseStr(json, "pill_alignment", pillAlignment);
            notchMode = parseBool(json, "notch_mode", notchMode);
            xOffset = parseInt(json, "x_offset", xOffset);
            yOffset = parseInt(json, "y_offset", yOffset);
            customPillWidth = parseInt(json, "pill_width", customPillWidth);
            customPillHeight = parseInt(json, "pill_height", customPillHeight);
            customCardWidth = parseInt(json, "card_width", customCardWidth);
            customCardHeight = parseInt(json, "card_height", customCardHeight);
            cardRadius = parseInt(json, "card_radius", cardRadius);
            cardYOffset = parseInt(json, "card_y_offset", cardYOffset);
            cardPositionMode = parseStr(json, "card_position_mode", cardPositionMode);

            enableMedia = parseBool(json, "enable_media", enableMedia);
            mediaShowPillArt = parseBool(json, "media_show_pill_art", mediaShowPillArt);
            mediaArtStyle = parseStr(json, "media_art_style", mediaArtStyle);
            mediaShowWaveform = parseBool(json, "media_show_waveform", mediaShowWaveform);
            mediaPulseColor = parseStr(json, "media_pulse_color", mediaPulseColor);
            mediaAmbientGlow = parseBool(json, "media_ambient_glow", mediaAmbientGlow);
            mediaGlowOpacity = parseInt(json, "media_glow_opacity", mediaGlowOpacity);
            mediaMarquee = parseBool(json, "media_marquee", mediaMarquee);

            enableCharging = parseBool(json, "enable_charging", enableCharging);
            enableVolume = parseBool(json, "enable_volume", enableVolume);
            enableRinger = parseBool(json, "enable_ringer", enableRinger);
            enableNotifications = parseBool(json, "enable_notifications", enableNotifications);
            enableProgress = parseBool(json, "enable_progress", enableProgress);
            enableTorch = parseBool(json, "enable_torch", enableTorch);
            enableHaptics = parseBool(json, "enable_haptics", enableHaptics);

            stealthRingIdle = parseBool(json, "stealth_ring_idle", stealthRingIdle);
            hideInLandscape = parseBool(json, "hide_in_landscape", hideInLandscape);
            springStiffness = parseFloat(json, "spring_stiffness", springStiffness);
            springDamping = parseFloat(json, "spring_damping", springDamping);

            autoExpandCharging = parseBool(json, "auto_expand_charging", autoExpandCharging);
            autoExpandMedia = parseBool(json, "auto_expand_media", autoExpandMedia);
            autoExpandNotif = parseBool(json, "auto_expand_notification", autoExpandNotif);
            autoExpandProgress = parseBool(json, "auto_expand_progress", autoExpandProgress);
            expandTimeoutMs = parseInt(json, "expand_timeout_ms", expandTimeoutMs);

            if (HyperRingOverlay.previewLock) {
                HyperRingOverlay.calibrationEndTime = Long.MAX_VALUE;
            } else if (IslandState.currentIsland == IslandState.STATE_CALIBRATION) {
                HyperRingOverlay.calibrationEndTime = 0L;
                HyperRingOverlay.startCollapse();
            }
        } catch (Throwable ignored) {}
    }

    public static void persistStatusAsync() {
        if (HyperRingOverlay.backgroundHandler != null) {
            HyperRingOverlay.backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        String stateName = IslandState.getStateName(IslandState.currentIsland);
                        StringBuilder sb = new StringBuilder();
                        sb.append("{\n");
                        sb.append("  \"pid\": ").append(android.os.Process.myPid()).append(",\n");
                        sb.append("  \"active_island\": \"").append(stateName).append("\",\n");
                        sb.append("  \"expanded\": ").append(IslandState.isExpanded).append(",\n");
                        sb.append("  \"media_title\": \"").append(escapeJson(IslandState.mediaTitle)).append("\",\n");
                        sb.append("  \"media_artist\": \"").append(escapeJson(IslandState.mediaArtist)).append("\",\n");
                        sb.append("  \"media_playing\": ").append(IslandState.isMediaPlaying).append(",\n");
                        sb.append("  \"battery_pct\": ").append(IslandState.batteryPct).append(",\n");
                        sb.append("  \"battery_charging\": ").append(IslandState.isCharging).append(",\n");
                        sb.append("  \"volume_percent\": ").append(IslandState.volumePercent).append(",\n");
                        sb.append("  \"ringer_label\": \"").append(IslandState.ringerLabel).append("\",\n");
                        sb.append("  \"torch_active\": ").append(IslandState.isTorchActive).append(",\n");
                        sb.append("  \"progress_active\": ").append(IslandState.isProgressActive).append(",\n");
                        sb.append("  \"progress_pct\": ").append(IslandState.progressPct).append(",\n");
                        sb.append("  \"progress_title\": \"").append(escapeJson(IslandState.progressTitle)).append("\",\n");
                        sb.append("  \"charge_watt\": \"").append(IslandState.chargeWattStr).append("\",\n");
                        sb.append("  \"preview_lock\": ").append(HyperRingOverlay.previewLock).append("\n");
                        sb.append("}\n");

                        File statFile = new File(statusPath);
                        File tmp = new File(statusPath + ".tmp");
                        try (FileOutputStream fos = new FileOutputStream(tmp)) {
                            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                            fos.flush();
                        }
                        tmp.renameTo(statFile);
                    } catch (Throwable ignored) {}
                }
            });
        }
    }

    private static String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }

    public static int parseInt(String json, String key, int def) {
        try {
            String v = getJsonRawVal(json, key);
            if (v != null) return (int) Math.round(Double.parseDouble(v));
        } catch (Throwable ignored) {}
        return def;
    }

    public static float parseFloat(String json, String key, float def) {
        try {
            String v = getJsonRawVal(json, key);
            if (v != null) return Float.parseFloat(v);
        } catch (Throwable ignored) {}
        return def;
    }

    public static boolean parseBool(String json, String key, boolean def) {
        try {
            String v = getJsonRawVal(json, key);
            if (v != null) return Boolean.parseBoolean(v);
        } catch (Throwable ignored) {}
        return def;
    }

    public static String parseStr(String json, String key, String def) {
        try {
            String v = getJsonRawVal(json, key);
            if (v != null) {
                v = v.trim();
                if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                    return v.substring(1, v.length() - 1);
                }
                return v;
            }
        } catch (Throwable ignored) {}
        return def;
    }

    public static String getJsonRawVal(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon == -1) return null;
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return null;

        if (json.charAt(start) == '\"') {
            int end = json.indexOf('\"', start + 1);
            while (end != -1 && json.charAt(end - 1) == '\\') {
                end = json.indexOf('\"', end + 1);
            }
            if (end != -1) return json.substring(start, end + 1);
        } else {
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != '\n') {
                end++;
            }
            return json.substring(start, end).trim();
        }
        return null;
    }
}
