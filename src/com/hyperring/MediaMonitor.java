package com.hyperring;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

public class MediaMonitor {

    private static MediaController.Callback currentCallback = null;

    public static void init() {
        queryMediaSessionNative();
    }

    public static void queryMediaSessionNative() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            queryMediaSessionInternal();
        } else if (HyperRingOverlay.backgroundHandler != null) {
            HyperRingOverlay.backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    queryMediaSessionInternal();
                }
            });
        }
    }

    private static void queryMediaSessionInternal() {
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getServiceMethod = smClass.getMethod("getService", String.class);
            IBinder binder = (IBinder) getServiceMethod.invoke(null, "media_session");
            if (binder != null) {
                Class<?> stubClass = Class.forName("android.media.session.ISessionManager$Stub");
                Method asInterfaceMethod = stubClass.getMethod("asInterface", IBinder.class);
                Object ism = asInterfaceMethod.invoke(null, binder);
                if (ism != null) {
                    Method getSessionsMethod = ism.getClass().getMethod("getSessions", android.content.ComponentName.class, int.class);
                    List<?> tokens = (List<?>) getSessionsMethod.invoke(ism, null, 0);
                    if (tokens != null && !tokens.isEmpty()) {
                        MediaController chosen = null;
                        for (Object item : tokens) {
                            if (item instanceof MediaSession.Token) {
                                MediaController mc = new MediaController(HyperRingOverlay.context != null ? HyperRingOverlay.context : HyperRingOverlay.sysContext, (MediaSession.Token) item);
                                PlaybackState ps = mc.getPlaybackState();
                                if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) {
                                    chosen = mc;
                                    break;
                                } else if (chosen == null) {
                                    chosen = mc;
                                }
                            }
                        }
                        if (chosen != null) {
                            processMediaController(chosen);
                            return;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        queryMediaSessionDumpsysFallback();
    }

    public static void processMediaController(final MediaController mc) {
        if (mc == null) return;
        try {
            if (IslandState.activeMediaController != mc) {
                if (IslandState.activeMediaController != null && currentCallback != null) {
                    try { IslandState.activeMediaController.unregisterCallback(currentCallback); } catch (Throwable ignored) {}
                }
                IslandState.activeMediaController = mc;
                currentCallback = new MediaController.Callback() {
                    @Override
                    public void onPlaybackStateChanged(PlaybackState state) {
                        processMediaController(mc);
                    }
                    @Override
                    public void onMetadataChanged(MediaMetadata metadata) {
                        processMediaController(mc);
                    }
                    @Override
                    public void onSessionDestroyed() {
                        queryMediaSessionNative();
                    }
                };
                mc.registerCallback(currentCallback, HyperRingOverlay.handler);
            }

            PlaybackState ps = mc.getPlaybackState();
            boolean isPlaying = (ps != null && ps.getState() == PlaybackState.STATE_PLAYING);
            long pos = (ps != null) ? ps.getPosition() : 0L;
            long lastUpdate = (ps != null) ? ps.getLastPositionUpdateTime() : SystemClock.elapsedRealtime();

            MediaMetadata md = mc.getMetadata();
            String title = "";
            String artist = "";
            long duration = 0L;

            if (md != null) {
                title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
                if (title == null || title.isEmpty()) {
                    title = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                }
                artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (artist == null || artist.isEmpty()) {
                    artist = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                }
                duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
            }

            if (title == null || title.trim().isEmpty()) {
                title = "Unknown Track";
            }
            if (artist == null || artist.trim().isEmpty()) {
                artist = "Media Playback";
            }

            IslandState.mediaTrackPosition = pos;
            IslandState.mediaPositionUpdateTime = lastUpdate;
            IslandState.mediaTrackDuration = duration;

            String artKey = mc.getPackageName() + ":" + title + ":" + artist;
            boolean trackChanged = !artKey.equals(IslandState.lastArtKey);

            if (trackChanged) {
                IslandState.lastArtKey = artKey;
                Bitmap rawArt = null;
                if (md != null) {
                    rawArt = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
                    if (rawArt == null) rawArt = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                    if (rawArt == null) rawArt = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);

                    if (rawArt == null) {
                        String uriStr = md.getString(MediaMetadata.METADATA_KEY_ART_URI);
                        if (uriStr == null) uriStr = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
                        if (uriStr != null && !uriStr.isEmpty()) {
                            rawArt = decodeBitmapFromUri(uriStr);
                        }
                    }
                }

                if (rawArt != null && !rawArt.isRecycled()) {
                    int pillSize = Math.round(IslandConfig.dpToPx(24));
                    int cardSize = Math.round(IslandConfig.dpToPx(56));
                    Bitmap newPill = Bitmap.createScaledBitmap(rawArt, pillSize, pillSize, true);
                    Bitmap newCard = Bitmap.createScaledBitmap(rawArt, cardSize, cardSize, true);
                    int dominant = extractDominantVibrantColor(rawArt);

                    Bitmap oldPill = IslandState.currentPillArt;
                    Bitmap oldCard = IslandState.currentCardArt;
                    IslandState.currentPillArt = newPill;
                    IslandState.currentCardArt = newCard;
                    IslandState.mediaDominantColor = dominant;

                    if (oldPill != null && oldPill != newPill && !oldPill.isRecycled()) oldPill.recycle();
                    if (oldCard != null && oldCard != newCard && !oldCard.isRecycled()) oldCard.recycle();
                } else {
                    if (IslandState.currentPillArt != null && !IslandState.currentPillArt.isRecycled()) {
                        IslandState.currentPillArt.recycle();
                    }
                    if (IslandState.currentCardArt != null && !IslandState.currentCardArt.isRecycled()) {
                        IslandState.currentCardArt.recycle();
                    }
                    IslandState.currentPillArt = null;
                    IslandState.currentCardArt = null;
                    IslandState.mediaDominantColor = Color.parseColor("#38BDF8");
                }
            }

            final boolean finalPlaying = isPlaying;
            final String finalTitle = title;
            final String finalArtist = artist;
            final boolean finalTrackChanged = trackChanged;

            HyperRingOverlay.handler.post(new Runnable() {
                @Override
                public void run() {
                    boolean prevPlay = IslandState.isMediaPlaying;
                    IslandState.isMediaPlaying = finalPlaying;
                    IslandState.mediaTitle = finalTitle;
                    IslandState.mediaArtist = finalArtist;

                    if (IslandState.isMediaPlaying != prevPlay) {
                        IslandState.userDismissedMedia = false;
                        if (IslandState.isMediaPlaying && IslandConfig.enableMedia) {
                            HyperRingOverlay.handler.removeCallbacks(HyperRingOverlay.mediaPauseTimeoutRunnable);
                            if (!HyperRingOverlay.previewLock && IslandState.currentIsland != IslandState.STATE_CALIBRATION) {
                                IslandState.isCollapsing = false;
                                HyperRingOverlay.showIsland(IslandState.STATE_MEDIA, 0);
                            }
                        } else if (!IslandState.isMediaPlaying) {
                            if (IslandState.currentIsland == IslandState.STATE_MEDIA) {
                                HyperRingOverlay.handler.removeCallbacks(HyperRingOverlay.mediaPauseTimeoutRunnable);
                                HyperRingOverlay.handler.postDelayed(HyperRingOverlay.mediaPauseTimeoutRunnable, 20000);
                            }
                        }
                    } else if (IslandState.isMediaPlaying && IslandConfig.enableMedia && IslandState.currentIsland == IslandState.STATE_IDLE && !IslandState.userDismissedMedia) {
                        IslandState.isCollapsing = false;
                        if (!HyperRingOverlay.previewLock && IslandState.currentIsland != IslandState.STATE_CALIBRATION) {
                            HyperRingOverlay.showIsland(IslandState.STATE_MEDIA, 0);
                        }
                    }

                    if (finalTrackChanged) {
                        IslandState.userDismissedMedia = false;
                        if (IslandConfig.autoExpandMedia && IslandState.isMediaPlaying && !IslandState.isExpanded && !HyperRingOverlay.previewLock) {
                            HyperRingOverlay.expandCard();
                        }
                    }
                    if (IslandState.isMediaPlaying != prevPlay || finalTrackChanged || IslandState.currentIsland == IslandState.STATE_IDLE) {
                        HyperRingOverlay.wakeEngineLoop();
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    private static Bitmap decodeBitmapFromUri(String uriStr) {
        InputStream is = null;
        try {
            Uri uri = Uri.parse(uriStr);
            if (HyperRingOverlay.context != null) {
                is = HyperRingOverlay.context.getContentResolver().openInputStream(uri);
                if (is != null) return BitmapFactory.decodeStream(is);
            }
        } catch (Throwable ignored) {
        } finally {
            if (is != null) {
                try { is.close(); } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private static int extractDominantVibrantColor(Bitmap bmp) {
        if (bmp == null || bmp.isRecycled()) return Color.parseColor("#38BDF8");
        Bitmap thumb = null;
        try {
            thumb = Bitmap.createScaledBitmap(bmp, 16, 16, false);
            int[] pixels = new int[256];
            thumb.getPixels(pixels, 0, 16, 0, 0, 16, 16);
            if (thumb != bmp && !thumb.isRecycled()) thumb.recycle();
            thumb = null;

            float maxScore = -1f;
            int bestColor = Color.parseColor("#38BDF8");
            float[] hsv = new float[3];

            for (int c : pixels) {
                Color.colorToHSV(c, hsv);
                float sat = hsv[1];
                float val = hsv[2];
                if (val < 0.20f || (val > 0.90f && sat < 0.15f) || sat < 0.20f) continue;
                float score = (sat * 2.2f) + val;
                if (score > maxScore) {
                    maxScore = score;
                    bestColor = c;
                }
            }
            return bestColor;
        } catch (Throwable t) {
            return Color.parseColor("#38BDF8");
        } finally {
            if (thumb != null && thumb != bmp && !thumb.isRecycled()) thumb.recycle();
        }
    }

    public static String formatTimeMs(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = ms / 1000;
        long m = totalSec / 60;
        long s = totalSec % 60;
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    public static void togglePlayPause() {
        if (IslandState.activeMediaController != null) {
            try {
                MediaController.TransportControls tc = IslandState.activeMediaController.getTransportControls();
                if (tc != null) {
                    if (IslandState.isMediaPlaying) tc.pause();
                    else tc.play();
                    return;
                }
            } catch (Throwable ignored) {}
        }
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    public static void skipNext() {
        if (IslandState.activeMediaController != null) {
            try {
                MediaController.TransportControls tc = IslandState.activeMediaController.getTransportControls();
                if (tc != null) {
                    tc.skipToNext();
                    return;
                }
            } catch (Throwable ignored) {}
        }
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT);
    }

    public static void skipPrev() {
        if (IslandState.activeMediaController != null) {
            try {
                MediaController.TransportControls tc = IslandState.activeMediaController.getTransportControls();
                if (tc != null) {
                    tc.skipToPrevious();
                    return;
                }
            } catch (Throwable ignored) {}
        }
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }

    public static void seekTo(long posMs) {
        if (IslandState.activeMediaController != null) {
            try {
                MediaController.TransportControls tc = IslandState.activeMediaController.getTransportControls();
                if (tc != null) {
                    tc.seekTo(posMs);
                    IslandState.mediaTrackPosition = posMs;
                    IslandState.mediaPositionUpdateTime = SystemClock.elapsedRealtime();
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void sendMediaKeyEvent(final int keyCode) {
        if (HyperRingOverlay.backgroundHandler != null) {
            HyperRingOverlay.backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Runtime.getRuntime().exec(new String[]{"input", "keyevent", String.valueOf(keyCode)});
                    } catch (Throwable ignored) {}
                }
            });
        }
    }

    private static void queryMediaSessionDumpsysFallback() {
        try {
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"dumpsys", "media_session"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            boolean inStack = false;
            boolean curSessionPlaying = false;
            String curSessionTitle = "";
            String curSessionArtist = "";
            boolean foundPlaying = false;
            String playingTitle = "";
            String playingArtist = "";

            while ((line = reader.readLine()) != null) {
                if (line.contains("Sessions Stack")) {
                    inStack = true;
                    continue;
                }
                if (inStack && (line.startsWith("Audio playback") || line.startsWith("Media session config:"))) {
                    if (curSessionPlaying && !foundPlaying) {
                        foundPlaying = true;
                        playingTitle = curSessionTitle;
                        playingArtist = curSessionArtist;
                    }
                    break;
                }
                if (!inStack) continue;

                if (line.startsWith("    ") && !line.startsWith("      ")) {
                    if (curSessionPlaying && !foundPlaying) {
                        foundPlaying = true;
                        playingTitle = curSessionTitle;
                        playingArtist = curSessionArtist;
                        break;
                    }
                    curSessionPlaying = false;
                    curSessionTitle = "";
                    curSessionArtist = "";
                    continue;
                }

                String trimmed = line.trim();
                if (trimmed.contains("state=PlaybackState") || trimmed.contains("PlaybackState {state=")) {
                    if (trimmed.contains("state=3") || trimmed.contains("STATE_PLAYING") || trimmed.contains("state=PLAYING")) {
                        curSessionPlaying = true;
                    }
                } else if (trimmed.contains("description=")) {
                    int dIdx = trimmed.indexOf("description=");
                    String desc = trimmed.substring(dIdx + 12).trim();
                    if (desc.startsWith(",")) desc = desc.substring(1).trim();
                    String[] parts = desc.split(",");
                    if (parts.length > 0 && !parts[0].trim().isEmpty()) {
                        curSessionTitle = parts[0].trim();
                    }
                    if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                        curSessionArtist = parts[1].trim();
                    }
                }
            }
            reader.close();
            p.destroy();

            final boolean isPlaying = foundPlaying;
            final String title = !playingTitle.isEmpty() ? playingTitle : "Media Playback";
            final String artist = !playingArtist.isEmpty() ? playingArtist : "Active Audio";

            HyperRingOverlay.handler.post(new Runnable() {
                @Override
                public void run() {
                    boolean prev = IslandState.isMediaPlaying;
                    IslandState.isMediaPlaying = isPlaying;
                    IslandState.mediaTitle = title;
                    IslandState.mediaArtist = artist;

                    if (isPlaying && !prev && IslandConfig.enableMedia) {
                        IslandState.userDismissedMedia = false;
                        HyperRingOverlay.showIsland(IslandState.STATE_MEDIA, 0);
                    }
                    HyperRingOverlay.wakeEngineLoop();
                }
            });
        } catch (Throwable ignored) {}
    }
}
