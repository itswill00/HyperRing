package com.hyperring;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.session.MediaController;

public class IslandState {
    public static final int STATE_IDLE = 0;
    public static final int STATE_CHARGING = 1;
    public static final int STATE_MEDIA = 2;
    public static final int STATE_VOLUME = 3;
    public static final int STATE_RINGER = 4;
    public static final int STATE_NOTIFICATION = 5;
    public static final int STATE_PROGRESS = 6;
    public static final int STATE_TORCH = 7;
    public static final int STATE_CALIBRATION = 8;

    public static volatile int currentIsland = STATE_IDLE;
    public static volatile int activeIslandType = STATE_IDLE;
    public static volatile int previousIsland = STATE_IDLE;
    public static volatile boolean isCollapsing = false;
    public static volatile boolean isExpanded = false;
    public static volatile boolean isHUNTucked = false;
    public static volatile boolean isScreenInteractive = true;

    // Battery telemetry
    public static volatile int batteryPct = -1;
    public static volatile boolean isCharging = false;
    public static volatile String chargeWattStr = "0.0W";
    public static volatile String chargeCurrentStr = "0mA";
    public static volatile String batteryTempStr = "";

    // Volume & Ringer telemetry
    public static volatile int volumePercent = 50;
    public static volatile int lastVolumeLevel = -1;
    public static volatile String ringerLabel = "Ring";
    public static volatile int lastRingerLevel = -1;

    // Media telemetry
    public static volatile String mediaTitle = "No active playback";
    public static volatile String mediaArtist = "Media";
    public static volatile boolean isMediaPlaying = false;
    public static volatile long lastMediaPoll = 0L;
    public static volatile long mediaTrackPosition = 0L;
    public static volatile long mediaPositionUpdateTime = 0L;
    public static volatile long mediaTrackDuration = 0L;
    public static volatile MediaController activeMediaController = null;
    public static volatile Bitmap currentPillArt = null;
    public static volatile Bitmap currentCardArt = null;
    public static volatile int mediaDominantColor = Color.TRANSPARENT;
    public static volatile String lastArtKey = "";
    public static volatile boolean userDismissedMedia = false;
    public static volatile long mediaPausedUptime = 0L;

    // Notification telemetry
    public static volatile String notifAppName = "Notification";
    public static volatile String notifTitle = "";
    public static volatile String notifContent = "";
    public static volatile long lastNotifTime = 0L;
    public static volatile String lastNotifPkg = "";
    public static volatile Bitmap notifAppIcon = null;

    // Universal Download & Progress telemetry
    public static volatile boolean isProgressActive = false;
    public static volatile int progressPct = 0;
    public static volatile int progressMax = 100;
    public static volatile String progressTitle = "Download";
    public static volatile String progressSubtitle = "0 MB / 0 MB";
    public static volatile String progressAppName = "Chrome";
    public static volatile String progressPkg = "";
    public static volatile boolean progressIndeterminate = false;
    public static volatile boolean isProgressComplete = false;
    public static volatile long progressCompleteTime = 0L;
    public static volatile Bitmap progressAppIcon = null;

    // Torch telemetry
    public static volatile boolean isTorchActive = false;

    public static int statePriority(int state) {
        switch (state) {
            case STATE_CALIBRATION: return 0;
            case STATE_CHARGING:    return 1;
            case STATE_VOLUME:      return 2;
            case STATE_RINGER:      return 2;
            case STATE_NOTIFICATION: return 3;
            case STATE_PROGRESS:    return 4;
            case STATE_TORCH:       return 5;
            case STATE_MEDIA:       return 6;
            default:                return 99;
        }
    }

    public static String getStateName(int state) {
        switch (state) {
            case STATE_CHARGING:    return "charging";
            case STATE_MEDIA:       return "media";
            case STATE_VOLUME:      return "volume";
            case STATE_RINGER:      return "ringer";
            case STATE_NOTIFICATION: return "notification";
            case STATE_PROGRESS:    return "progress";
            case STATE_TORCH:       return "torch";
            case STATE_CALIBRATION: return "calibration";
            default:                return "idle";
        }
    }
}
