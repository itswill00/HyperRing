package com.hyperring;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationMonitor {

    private static Pattern flagPattern = Pattern.compile("flags=0x([0-9a-fA-F]+)");

    public static void startStreaming() {
        HyperRingOverlay.workerPool.execute(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    java.lang.Process p = null;
                    try {
                        ProcessBuilder pb = new ProcessBuilder("logcat", "-b", "main", "-b", "system", "-b", "events", "-v", "brief", "-T", "1");
                        pb.redirectErrorStream(true);
                        p = pb.start();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                        String line;

                        while (true) {
                            try {
                                line = reader.readLine();
                            } catch (java.io.IOException ioEx) {
                                break;
                            }
                            if (line == null) break;

                            if (!IslandConfig.masterEnabled) continue;

                            // Volume Event Streaming
                            if (IslandConfig.enableVolume && (line.contains("Volume controller visible: true")
                                    || line.contains("vol.MiuiVolumeDialog")
                                    || line.contains("MediaVolumeConr")
                                    || line.contains("dispatchVolumeKeyEvent"))) {
                                AudioMonitor.queryVolumeFallback(HyperRingOverlay.context != null ? HyperRingOverlay.context : HyperRingOverlay.sysContext);
                            }

                            // Media Playback State Event Detection (fast 50ms response)
                            if (IslandConfig.enableMedia && (line.contains("MediaSessionRecord") || line.contains("updatePlaybackState") || line.contains("dispatchMediaKeyEvent"))) {
                                long nowTick = SystemClock.uptimeMillis();
                                if (nowTick - IslandState.lastMediaPoll > 50) {
                                    IslandState.lastMediaPoll = nowTick;
                                    MediaMonitor.queryMediaSessionNative();
                                }
                            }

                            // Heads-Up Notification (HUN) banner alert collision avoidance
                            if (line.contains("notification_alert") || line.contains("heads_up") || line.contains("sysui_heads_up")) {
                                HyperRingOverlay.tuckForHUN(4500);
                            }

                            // Notification Cancellation Detection (cleans up ongoing downloads)
                            if (line.contains("notification_cancel")) {
                                handleNotificationCancel(line);
                            }

                            // Notification Enqueue & Progress Detection
                            if (line.contains("notification_enqueue")) {
                                handleNotificationEnqueue(line);
                            }
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        if (p != null) {
                            try { p.destroy(); } catch (Throwable ignored) {}
                        }
                    }
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) {}
                }
            }
        });
    }

    private static void handleNotificationCancel(String line) {
        if (!IslandState.isProgressActive) return;
        int startIdx = line.indexOf("[");
        if (startIdx != -1) {
            String body = line.substring(startIdx + 1);
            String[] parts = body.split(",");
            if (parts.length >= 3) {
                String pkg = parts[2].trim();
                if (pkg.equalsIgnoreCase(IslandState.progressPkg)) {
                    HyperRingOverlay.handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (IslandState.isProgressActive) {
                                IslandState.isProgressActive = false;
                                if (IslandState.currentIsland == IslandState.STATE_PROGRESS) {
                                    HyperRingOverlay.startCollapse();
                                }
                            }
                        }
                    });
                }
            }
        }
    }

    private static void handleNotificationEnqueue(String line) {
        if (line.contains("category=transport")
                || line.contains("channel=music")
                || line.contains("channel=playback")
                || line.contains("category=service")
                || line.contains("category=sys")) {
            return;
        }

        int flags = 0;
        Matcher fm = flagPattern.matcher(line);
        if (fm.find()) {
            try {
                flags = Integer.parseInt(fm.group(1), 16);
            } catch (Throwable ignored) {}
        }

        int startIdx = line.indexOf("[");
        if (startIdx == -1) return;
        String body = line.substring(startIdx + 1);
        String[] parts = body.split(",");
        if (parts.length < 3) return;
        String pkg = parts[2].trim();

        if (IslandState.activeMediaController != null && pkg.equalsIgnoreCase(IslandState.activeMediaController.getPackageName())) {
            return;
        }

        boolean isOngoing = (flags & (0x02 | 0x40)) != 0;
        boolean isDownloader = isDownloadPackage(pkg) || line.contains("category=progress") || line.contains("download");

        // 1. Universal Download / Progress Detection
        if (IslandConfig.enableProgress && isDownloader && isOngoing) {
            parseDownloadProgressAsync(pkg, line);
            return;
        }

        // 2. Standard Heads-Up & Messaging Notifications
        if (IslandConfig.enableNotifications && !isOngoing) {
            if (isUserFacingPackage(pkg)) {
                long nowMs = SystemClock.uptimeMillis();
                if (nowMs - IslandState.lastNotifTime > 3000 || !pkg.equals(IslandState.lastNotifPkg)) {
                    IslandState.lastNotifTime = nowMs;
                    IslandState.lastNotifPkg = pkg;
                    IslandState.notifAppName = resolveFriendlyAppName(pkg);
                    IslandState.notifTitle = "";
                    IslandState.notifContent = "New notification";
                    Context c = HyperRingOverlay.context != null ? HyperRingOverlay.context : HyperRingOverlay.sysContext;
                    IslandState.notifAppIcon = AppIconLoader.getAppIcon(c, pkg, Math.round(IslandConfig.dpToPx(28)));
                    queryNotificationDetailsAsync(pkg);

                    if (!HyperRingOverlay.previewLock && IslandState.currentIsland != IslandState.STATE_CALIBRATION) {
                        if (line.contains("alert=1") || line.contains("high") || line.contains("heads")) {
                            HyperRingOverlay.tuckForHUN(4500);
                        } else {
                            long notifTimeout = IslandConfig.autoExpandNotif ? Math.max(IslandConfig.expandTimeoutMs, 4500) : 3500;
                            HyperRingOverlay.showIsland(IslandState.STATE_NOTIFICATION, notifTimeout);
                            if (IslandConfig.autoExpandNotif && !IslandState.isExpanded && !HyperRingOverlay.previewLock) {
                                HyperRingOverlay.expandCard();
                            }
                        }
                    }
                }
            }
        }
    }

    public static boolean isDownloadPackage(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase();
        return p.contains("chrome") || p.contains("vending") || p.contains("download")
                || p.contains("firefox") || p.contains("brave") || p.contains("opera")
                || p.contains("telegram") || p.contains("adm") || p.contains("idm")
                || p.contains("browser");
    }

    private static void parseDownloadProgressAsync(final String pkg, final String logLine) {
        HyperRingOverlay.workerPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    java.lang.Process p = Runtime.getRuntime().exec(new String[]{"dumpsys", "notification", "--noredact"});
                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    boolean inTarget = false;
                    String title = "";
                    String text = "";
                    int progress = -1;
                    int progressMax = -1;
                    boolean indeterminate = false;

                    while ((line = reader.readLine()) != null) {
                        if (line.contains("NotificationRecord(") && line.contains("pkg=" + pkg)) {
                            inTarget = true;
                            title = "";
                            text = "";
                            progress = -1;
                            progressMax = -1;
                            indeterminate = false;
                        } else if (inTarget && line.contains("NotificationRecord(")) {
                            if (progressMax > 0 || !title.isEmpty()) break;
                            inTarget = false;
                        } else if (inTarget) {
                            if (line.contains("android.title=") && title.isEmpty()) {
                                int sIdx = line.indexOf("String (");
                                if (sIdx != -1) title = line.substring(sIdx + 8, line.length() - 1);
                            } else if (line.contains("android.text=") && text.isEmpty()) {
                                int sIdx = line.indexOf("String (");
                                if (sIdx != -1) text = line.substring(sIdx + 8, line.length() - 1);
                            } else if (line.contains("android.progress=")) {
                                int sIdx = line.indexOf("Integer (");
                                if (sIdx != -1) {
                                    try { progress = Integer.parseInt(line.substring(sIdx + 9, line.length() - 1)); } catch (Exception ignored) {}
                                }
                            } else if (line.contains("android.progressMax=")) {
                                int sIdx = line.indexOf("Integer (");
                                if (sIdx != -1) {
                                    try { progressMax = Integer.parseInt(line.substring(sIdx + 9, line.length() - 1)); } catch (Exception ignored) {}
                                }
                            } else if (line.contains("android.progressIndeterminate=")) {
                                indeterminate = line.contains("true");
                            }
                        }
                    }
                    reader.close();
                    p.destroy();

                    if (progressMax <= 0 && !indeterminate) {
                        // Not a progress notification
                        return;
                    }

                    int pct = 0;
                    if (progressMax > 0 && progress >= 0) {
                        pct = Math.round((progress / (float) progressMax) * 100f);
                        pct = Math.max(0, Math.min(100, pct));
                    } else if (indeterminate) {
                        pct = 50;
                    }

                    final int finalPct = pct;
                    final int finalMax = progressMax;
                    final String finalTitle = !title.isEmpty() ? title : "Downloading...";
                    final String finalText = !text.isEmpty() ? text : (pct + "% completed");
                    final boolean finalIndeterminate = indeterminate;
                    final String appName = resolveFriendlyAppName(pkg);

                    HyperRingOverlay.handler.post(new Runnable() {
                        @Override
                        public void run() {
                            IslandState.isProgressActive = true;
                            IslandState.progressPct = finalPct;
                            IslandState.progressMax = finalMax;
                            IslandState.progressTitle = finalTitle;
                            IslandState.progressSubtitle = finalText;
                            IslandState.progressAppName = appName;
                            IslandState.progressPkg = pkg;
                            IslandState.progressIndeterminate = finalIndeterminate;
                            Context c = HyperRingOverlay.context != null ? HyperRingOverlay.context : HyperRingOverlay.sysContext;
                            IslandState.progressAppIcon = AppIconLoader.getAppIcon(c, pkg, Math.round(IslandConfig.dpToPx(28)));

                            if (finalPct >= 100 || finalText.toLowerCase().contains("complete") || finalText.toLowerCase().contains("finish")) {
                                IslandState.isProgressComplete = true;
                                IslandState.progressCompleteTime = SystemClock.uptimeMillis();
                                HyperRingOverlay.showIsland(IslandState.STATE_PROGRESS, 2500);
                            } else {
                                IslandState.isProgressComplete = false;
                                if (!HyperRingOverlay.previewLock && IslandState.currentIsland != IslandState.STATE_CALIBRATION) {
                                    HyperRingOverlay.showIsland(IslandState.STATE_PROGRESS, 0);
                                    if (IslandConfig.autoExpandProgress && !IslandState.isExpanded) {
                                        HyperRingOverlay.expandCard();
                                    }
                                }
                            }
                            HyperRingOverlay.wakeEngineLoop();
                        }
                    });
                } catch (Throwable ignored) {}
            }
        });
    }

    private static void queryNotificationDetailsAsync(final String pkg) {
        HyperRingOverlay.workerPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    java.lang.Process p = Runtime.getRuntime().exec(new String[]{"dumpsys", "notification", "--noredact"});
                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    boolean inTarget = false;
                    String title = "";
                    String text = "";
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("NotificationRecord(") && line.contains("pkg=" + pkg)) {
                            inTarget = true;
                            title = "";
                            text = "";
                        } else if (inTarget && line.contains("NotificationRecord(")) {
                            break;
                        } else if (inTarget) {
                            if (line.contains("android.title=") && title.isEmpty()) {
                                int sIdx = line.indexOf("String (");
                                if (sIdx != -1) title = line.substring(sIdx + 8, line.length() - 1);
                            } else if (line.contains("android.text=") && text.isEmpty()) {
                                int sIdx = line.indexOf("String (");
                                if (sIdx != -1) text = line.substring(sIdx + 8, line.length() - 1);
                            }
                        }
                    }
                    reader.close();
                    p.destroy();
                    if (!title.isEmpty()) IslandState.notifTitle = title;
                    if (!text.isEmpty()) IslandState.notifContent = text;
                    HyperRingOverlay.wakeEngineLoop();
                } catch (Throwable ignored) {}
            }
        });
    }

    public static String resolveFriendlyAppName(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "Notification";
        if (pkg.contains("chrome")) return "Chrome";
        if (pkg.contains("vending")) return "Google Play";
        if (pkg.contains("downloads")) return "Downloads";
        if (pkg.contains("whatsapp")) return "WhatsApp";
        if (pkg.contains("telegram")) return "Telegram";
        if (pkg.contains("spotify")) return "Spotify";
        if (pkg.contains("youtube")) return "YouTube";
        if (pkg.contains("gm") || pkg.contains("gmail")) return "Gmail";
        if (pkg.contains("twitter") || pkg.contains("x.android")) return "X";
        if (pkg.contains("instagram")) return "Instagram";
        if (pkg.contains("discord")) return "Discord";
        if (pkg.contains("firefox")) return "Firefox";
        if (pkg.contains("brave")) return "Brave";

        Context c = HyperRingOverlay.context != null ? HyperRingOverlay.context : HyperRingOverlay.sysContext;
        if (c != null) {
            try {
                PackageManager pm = c.getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                CharSequence cs = pm.getApplicationLabel(ai);
                if (cs != null && cs.length() > 0) return cs.toString();
            } catch (Throwable ignored) {}
        }
        String[] parts = pkg.split("\\.");
        String last = parts[parts.length - 1];
        if (last.length() > 1) {
            return Character.toUpperCase(last.charAt(0)) + last.substring(1);
        }
        return last;
    }

    public static boolean isUserFacingPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        String p = pkg.toLowerCase();
        if (p.startsWith("com.android.") || p.startsWith("com.miui.") || p.startsWith("com.xiaomi.")
                || p.startsWith("com.qualcomm.") || p.startsWith("com.mediatek.")) {
            return false;
        }
        if (p.contains("launcher") || p.contains("inputmethod") || p.contains("keyboard")
                || p.contains("termux") || p.contains("hyperring") || p.contains("overlay")
                || p.contains("bluetooth") || p.contains("backup")) {
            return false;
        }
        return true;
    }
}
