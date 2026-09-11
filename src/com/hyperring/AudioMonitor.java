package com.hyperring;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;

public class AudioMonitor {

    public static void init(Context context) {
        queryVolumeFallback(context);
        queryRingerFallback(context);
        registerReceivers(context);
    }

    public static void registerReceivers(final Context context) {
        // Volume Change Receiver
        try {
            IntentFilter volFilter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent intent) {
                    if (intent == null || !IslandConfig.enableVolume) return;
                    int stream = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1);
                    // Only trigger for music stream (3) to prevent keyboard false triggers on MIUI
                    if (stream != AudioManager.STREAM_MUSIC) return;
                    int val = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1);
                    int prevVal = intent.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -1);
                    if (val < 0 || val == prevVal) return;

                    AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                    int max = am != null ? am.getStreamMaxVolume(stream) : 15;
                    IslandState.volumePercent = Math.round((val / (float) Math.max(1, max)) * 100f);
                    IslandState.lastVolumeLevel = val;
                    HyperRingOverlay.previewLock = false;

                    if (IslandState.isExpanded) {
                        HyperRingOverlay.collapseCard();
                    }
                    if (IslandState.currentIsland != IslandState.STATE_CALIBRATION) {
                        HyperRingOverlay.showIsland(IslandState.STATE_VOLUME, 1800);
                    }
                }
            }, volFilter);
        } catch (Throwable ignored) {}

        // Ringer Mode Change Receiver
        try {
            IntentFilter ringerFilter = new IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION);
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent intent) {
                    if (intent == null || !IslandConfig.enableRinger) return;
                    int mode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1);
                    if (mode >= 0) {
                        if (mode == AudioManager.RINGER_MODE_SILENT) IslandState.ringerLabel = "Silent";
                        else if (mode == AudioManager.RINGER_MODE_VIBRATE) IslandState.ringerLabel = "Vibrate";
                        else IslandState.ringerLabel = "Ring";

                        HyperRingOverlay.showIsland(IslandState.STATE_RINGER, 2200);
                    }
                }
            }, ringerFilter);
        } catch (Throwable ignored) {}
    }

    public static void queryVolumeFallback(Context context) {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                if (cur >= 0 && max > 0) {
                    IslandState.volumePercent = Math.round((cur / (float) max) * 100f);
                    if (IslandState.lastVolumeLevel != cur && IslandState.lastVolumeLevel != -1 && IslandConfig.enableVolume) {
                        IslandState.lastVolumeLevel = cur;
                        if (IslandState.currentIsland != IslandState.STATE_CALIBRATION) {
                            HyperRingOverlay.showIsland(IslandState.STATE_VOLUME, 1800);
                        }
                    } else {
                        IslandState.lastVolumeLevel = cur;
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void queryRingerFallback(Context context) {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int mode = am.getRingerMode();
                String label = (mode == AudioManager.RINGER_MODE_SILENT) ? "Silent" :
                               (mode == AudioManager.RINGER_MODE_VIBRATE) ? "Vibrate" : "Ring";
                if (IslandState.lastRingerLevel != mode && IslandState.lastRingerLevel != -1 && IslandConfig.enableRinger) {
                    IslandState.lastRingerLevel = mode;
                    IslandState.ringerLabel = label;
                    if (IslandState.currentIsland != IslandState.STATE_CALIBRATION) {
                        HyperRingOverlay.showIsland(IslandState.STATE_RINGER, 2200);
                    }
                } else {
                    IslandState.lastRingerLevel = mode;
                    IslandState.ringerLabel = label;
                }
            }
        } catch (Throwable ignored) {}
    }
}
