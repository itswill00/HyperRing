package com.hyperring;

import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

public class WindowManagerController {

    public static WindowManager windowManager;
    public static WindowManager.LayoutParams params;
    public static RingView ringView;
    public static Display defaultDisplay;

    public static int lastDisplayRotation = -1;
    public static int lastDisplayW = -1;
    public static int lastDisplayH = -1;
    public static boolean isRebinding = false;

    private static final Runnable rebindRunnable = new Runnable() {
        @Override
        public void run() {
            checkDisplayRebind();
        }
    };

    public static void init(Context sysContext, Context context, Handler handler) {
        resolveDisplayMetrics(sysContext, context);
        registerDisplayListener(sysContext, handler);
    }

    public static void resolveDisplayMetrics(Context sysContext, Context context) {
        try {
            DisplayManager dm = (DisplayManager) sysContext.getSystemService(Context.DISPLAY_SERVICE);
            if (dm != null) {
                Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
                if (d != null) defaultDisplay = d;
            }
            DisplayMetrics realMetrics = new DisplayMetrics();
            if (defaultDisplay != null && defaultDisplay.getDisplayId() == Display.DEFAULT_DISPLAY) {
                defaultDisplay.getRealMetrics(realMetrics);
                if (realMetrics.widthPixels > 0 && realMetrics.heightPixels > 0) {
                    IslandConfig.displayWidthPx = realMetrics.widthPixels;
                    IslandConfig.displayHeightPx = realMetrics.heightPixels;
                    IslandConfig.displayDensity = realMetrics.density;
                }
            }
        } catch (Throwable ignored) {}

        int statusBarH = Math.round(38f * IslandConfig.displayDensity);
        try {
            int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                statusBarH = context.getResources().getDimensionPixelSize(resourceId);
            }
        } catch (Throwable ignored) {}

        if (IslandConfig.cutoutCenterX <= 0) {
            IslandConfig.cutoutCenterX = IslandConfig.displayWidthPx / 2;
        }
        if (IslandConfig.cutoutCenterY <= 0) {
            IslandConfig.cutoutCenterY = Math.max(Math.round(16f * IslandConfig.displayDensity), statusBarH / 2);
        }
        if (IslandConfig.cutoutRadius <= 0) {
            IslandConfig.cutoutRadius = Math.round(11f * IslandConfig.displayDensity);
        }
    }

    public static void registerDisplayListener(Context sysContext, final Handler handler) {
        try {
            DisplayManager dm = (DisplayManager) sysContext.getSystemService(Context.DISPLAY_SERVICE);
            if (dm != null) {
                dm.registerDisplayListener(new DisplayManager.DisplayListener() {
                    @Override
                    public void onDisplayAdded(int displayId) {
                        if (displayId != Display.DEFAULT_DISPLAY) return;
                        scheduleRebindRetry();
                    }

                    @Override
                    public void onDisplayRemoved(int displayId) {
                        if (displayId != Display.DEFAULT_DISPLAY) return;
                        scheduleRebindRetry();
                    }

                    @Override
                    public void onDisplayChanged(int displayId) {
                        if (displayId != Display.DEFAULT_DISPLAY) return;
                        if (defaultDisplay != null) {
                            int curRot = defaultDisplay.getRotation();
                            DisplayMetrics dm2 = new DisplayMetrics();
                            defaultDisplay.getRealMetrics(dm2);
                            if (curRot != lastDisplayRotation || dm2.widthPixels != lastDisplayW || dm2.heightPixels != lastDisplayH) {
                                lastDisplayRotation = curRot;
                                lastDisplayW = dm2.widthPixels;
                                lastDisplayH = dm2.heightPixels;
                                scheduleRebindRetry();
                            }
                        } else {
                            scheduleRebindRetry();
                        }
                    }
                }, handler);
            }
        } catch (Throwable ignored) {}
    }

    public static void scheduleRebindRetry() {
        if (HyperRingOverlay.handler == null) return;
        HyperRingOverlay.handler.removeCallbacks(rebindRunnable);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            rebindRunnable.run();
        } else {
            HyperRingOverlay.handler.post(rebindRunnable);
        }
    }

    public static void checkDisplayRebind() {
        if (HyperRingOverlay.handler == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            HyperRingOverlay.handler.post(new Runnable() {
                @Override public void run() { checkDisplayRebind(); }
            });
            return;
        }
        if (isRebinding) return;
        isRebinding = true;
        try {
            DisplayManager dm = (DisplayManager) HyperRingOverlay.sysContext.getSystemService(Context.DISPLAY_SERVICE);
            if (dm != null) {
                Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
                if (d != null) defaultDisplay = d;
            }
            resolveDisplayMetrics(HyperRingOverlay.sysContext, HyperRingOverlay.context);

            if (ringView != null && windowManager != null) {
                boolean attached = ringView.isAttachedToWindow() && ringView.getWindowToken() != null;
                if (attached) {
                    prepareWindowForTarget();
                    if (ringView != null && IslandState.currentIsland != IslandState.STATE_IDLE && ringView.getVisibility() != View.VISIBLE) {
                        ringView.setVisibility(View.VISIBLE);
                    }
                    HyperRingOverlay.wakeEngineLoop();
                } else {
                    try { windowManager.removeViewImmediate(ringView); } catch (Throwable ignored) {}
                    if (params != null) {
                        params.type = 2017;
                        params.token = null;
                        params.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
                        params.windowAnimations = 0;
                        try {
                            windowManager.addView(ringView, params);
                        } catch (Throwable t) {
                            try {
                                params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                                windowManager.addView(ringView, params);
                            } catch (Throwable ignored2) {}
                        }
                    }
                    if (IslandState.currentIsland != IslandState.STATE_IDLE) {
                        if (ringView != null && ringView.getVisibility() != View.VISIBLE) {
                            ringView.setVisibility(View.VISIBLE);
                        }
                        prepareWindowForTarget();
                        ringView.requestLayout();
                        ringView.invalidate();
                    }
                    HyperRingOverlay.wakeEngineLoop();
                    if (IslandState.isMediaPlaying && IslandState.currentIsland == IslandState.STATE_IDLE && IslandConfig.masterEnabled && !IslandState.userDismissedMedia) {
                        HyperRingOverlay.showIsland(IslandState.STATE_MEDIA, 0);
                    }
                }
            } else if (ringView == null) {
                attachWindow();
            }
        } finally {
            isRebinding = false;
        }
    }

    public static void attachWindow() {
        if (defaultDisplay == null || defaultDisplay.getDisplayId() != Display.DEFAULT_DISPLAY) {
            DisplayManager dm = (DisplayManager) HyperRingOverlay.sysContext.getSystemService(Context.DISPLAY_SERVICE);
            if (dm != null) defaultDisplay = dm.getDisplay(Display.DEFAULT_DISPLAY);
        }
        if (defaultDisplay != null) {
            Context dCtx = HyperRingOverlay.sysContext.createDisplayContext(defaultDisplay);
            if (dCtx != null) HyperRingOverlay.context = dCtx;
        }
        windowManager = (WindowManager) HyperRingOverlay.context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) return;

        ringView = new RingView(HyperRingOverlay.context);
        ringView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                v.postInvalidate();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                if (!isRebinding) scheduleRebindRetry();
            }
        });

        int type = 2017; // TYPE_STATUS_BAR_SUB_PANEL
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

        int initDiameter = Math.round(IslandConfig.cutoutRadius * 2.0f);
        params = new WindowManager.LayoutParams(initDiameter, initDiameter, type, flags, PixelFormat.TRANSLUCENT);
        params.setTitle("HyperRingOverlay");
        params.windowAnimations = 0;
        try {
            java.lang.reflect.Field pf = WindowManager.LayoutParams.class.getField("privateFlags");
            int curPf = pf.getInt(params);
            pf.setInt(params, curPf | 0x00000040); // PRIVATE_FLAG_NO_MOVE_ANIMATION
        } catch (Throwable ignored) {}

        float effCutoutX;
        float effCutoutY;
        if (isLandscape()) {
            int rot = (defaultDisplay != null) ? defaultDisplay.getRotation() : Surface.ROTATION_90;
            if (rot == Surface.ROTATION_270) {
                effCutoutX = (IslandConfig.displayWidthPx - IslandConfig.cutoutCenterY) + IslandConfig.xOffset;
            } else {
                effCutoutX = IslandConfig.cutoutCenterY + IslandConfig.xOffset;
            }
            effCutoutY = (IslandConfig.displayHeightPx / 2.0f) + IslandConfig.yOffset;
        } else {
            effCutoutX = IslandConfig.cutoutCenterX + IslandConfig.xOffset;
            effCutoutY = IslandConfig.cutoutCenterY + IslandConfig.yOffset;
        }
        if (effCutoutX <= 0 && IslandConfig.displayWidthPx > 0) {
            effCutoutX = IslandConfig.displayWidthPx / 2.0f;
        }
        int compactH = (IslandConfig.customPillHeight > 0) ? Math.round(IslandConfig.dpToPx(IslandConfig.customPillHeight)) : Math.max(Math.round(IslandConfig.cutoutRadius * 2.0f), Math.round(IslandConfig.dpToPx(34)));
        int topAnchorY = IslandConfig.notchMode ? Math.max(0, IslandConfig.yOffset) : Math.round(effCutoutY - (compactH / 2.0f));

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = Math.round(effCutoutX - (initDiameter / 2.0f));
        params.y = topAnchorY;

        try {
            java.lang.reflect.Field f = WindowManager.LayoutParams.class.getField("LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS");
            params.layoutInDisplayCutoutMode = f.getInt(null);
        } catch (Throwable t) {
            try {
                java.lang.reflect.Field f = WindowManager.LayoutParams.class.getField("LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES");
                params.layoutInDisplayCutoutMode = f.getInt(null);
            } catch (Throwable ignored) {}
        }

        try {
            params.type = 2017;
            windowManager.addView(ringView, params);
            System.out.println("HyperRing window attached successfully as type 2017");
        } catch (Throwable t1) {
            System.err.println("Failed to attach as 2017: " + t1.getMessage());
            try {
                params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                windowManager.addView(ringView, params);
                System.out.println("HyperRing window attached as TYPE_APPLICATION_OVERLAY");
            } catch (Throwable t2) {
                System.err.println("Failed to attach as 2038: " + t2.getMessage());
                try {
                    params.type = 2032;
                    windowManager.addView(ringView, params);
                    System.out.println("HyperRing window attached as TYPE_ACCESSIBILITY_OVERLAY");
                } catch (Throwable t3) {
                    System.err.println("Failed to attach as 2032: " + t3.getMessage());
                }
            }
        }

        if (IslandState.currentIsland == IslandState.STATE_IDLE) {
            if (IslandState.isMediaPlaying && IslandConfig.enableMedia && IslandConfig.masterEnabled) {
                HyperRingOverlay.showIsland(IslandState.STATE_MEDIA, 0);
            } else {
                ringView.setVisibility(View.GONE);
            }
        } else {
            ringView.setVisibility(View.VISIBLE);
            prepareWindowForTarget();
        }
    }

    public static boolean isLandscape() {
        try {
            if (defaultDisplay != null) {
                int rot = defaultDisplay.getRotation();
                if (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270) return true;
                DisplayMetrics metrics = new DisplayMetrics();
                defaultDisplay.getRealMetrics(metrics);
                if (metrics.widthPixels > metrics.heightPixels) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static float getPillRelX(float viewW, float pillW) {
        if ("left".equalsIgnoreCase(IslandConfig.pillAlignment)) {
            return 0f;
        } else if ("right".equalsIgnoreCase(IslandConfig.pillAlignment)) {
            return Math.max(0f, viewW - pillW);
        } else {
            return Math.max(0f, (viewW - pillW) / 2.0f);
        }
    }

    public static float getPillRelY(float viewH, float pillH) {
        if (IslandState.currentIsland == IslandState.STATE_CALIBRATION) {
            return Math.max(0f, (viewH - pillH) / 2.0f);
        }
        return 0f;
    }

    public static int getDefaultPillWidth(int state) {
        switch (state) {
            case IslandState.STATE_CHARGING:     return Math.round(IslandConfig.dpToPx(124));
            case IslandState.STATE_MEDIA:        return Math.round(IslandConfig.dpToPx(124));
            case IslandState.STATE_VOLUME:       return Math.round(IslandConfig.dpToPx(124));
            case IslandState.STATE_RINGER:       return Math.round(IslandConfig.dpToPx(124));
            case IslandState.STATE_NOTIFICATION: return Math.round(IslandConfig.dpToPx(124));
            case IslandState.STATE_PROGRESS:     return Math.round(IslandConfig.dpToPx(132));
            case IslandState.STATE_TORCH:        return Math.round(IslandConfig.dpToPx(124));
            default:                             return Math.round(IslandConfig.dpToPx(124));
        }
    }

    public static int getDefaultCardHeight(int state) {
        if (IslandConfig.customCardHeight > 0) return Math.round(IslandConfig.dpToPx(IslandConfig.customCardHeight));
        switch (state) {
            case IslandState.STATE_MEDIA:        return Math.round(IslandConfig.dpToPx(178));
            case IslandState.STATE_CHARGING:     return Math.round(IslandConfig.dpToPx(116));
            case IslandState.STATE_VOLUME:       return Math.round(IslandConfig.dpToPx(92));
            case IslandState.STATE_PROGRESS:     return Math.round(IslandConfig.dpToPx(124));
            case IslandState.STATE_NOTIFICATION: return Math.round(IslandConfig.dpToPx(108));
            default:                             return Math.round(IslandConfig.dpToPx(100));
        }
    }

    public static void prepareWindowForTarget() {
        if (params == null || windowManager == null || ringView == null) return;

        float effCutoutX;
        float effCutoutY;
        if (isLandscape()) {
            int rot = (defaultDisplay != null) ? defaultDisplay.getRotation() : Surface.ROTATION_90;
            if (rot == Surface.ROTATION_270) {
                effCutoutX = (IslandConfig.displayWidthPx - IslandConfig.cutoutCenterY) + IslandConfig.xOffset;
            } else {
                effCutoutX = IslandConfig.cutoutCenterY + IslandConfig.xOffset;
            }
            effCutoutY = (IslandConfig.displayHeightPx / 2.0f) + IslandConfig.yOffset;
        } else {
            effCutoutX = IslandConfig.cutoutCenterX + IslandConfig.xOffset;
            effCutoutY = IslandConfig.cutoutCenterY + IslandConfig.yOffset;
        }
        if (effCutoutX <= 0 && IslandConfig.displayWidthPx > 0) {
            effCutoutX = IslandConfig.displayWidthPx / 2.0f;
        }

        int compactH = (IslandConfig.customPillHeight > 0) ? Math.round(IslandConfig.dpToPx(IslandConfig.customPillHeight)) : Math.max(Math.round(IslandConfig.cutoutRadius * 2.0f), Math.round(IslandConfig.dpToPx(34)));
        int topAnchorY = IslandConfig.notchMode ? Math.max(0, IslandConfig.yOffset) : Math.round(effCutoutY - (compactH / 2.0f));

        if (IslandConfig.hideInLandscape && isLandscape() && IslandState.currentIsland != IslandState.STATE_CALIBRATION) {
            if (ringView.getVisibility() != View.GONE) ringView.setVisibility(View.GONE);
            if (IslandState.isExpanded) IslandState.isExpanded = false;
            params.width = 1;
            params.height = 1;
            params.x = Math.round(effCutoutX - 0.5f);
            params.y = topAnchorY;
            params.gravity = Gravity.TOP | Gravity.START;
            params.windowAnimations = 0;
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            try { windowManager.updateViewLayout(ringView, params); } catch (Exception ignored) {}
            return;
        }

        if (IslandState.isHUNTucked && IslandState.currentIsland != IslandState.STATE_CALIBRATION) {
            if (ringView.getVisibility() != View.GONE) ringView.setVisibility(View.GONE);
            params.width = 1;
            params.height = 1;
            params.x = Math.round(effCutoutX - 0.5f);
            params.y = topAnchorY;
            params.gravity = Gravity.TOP | Gravity.START;
            params.windowAnimations = 0;
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            try { windowManager.updateViewLayout(ringView, params); } catch (Exception ignored) {}
            return;
        }

        int targetFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

        if (IslandState.currentIsland == IslandState.STATE_IDLE && !IslandState.isCollapsing) {
            targetFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            if (ringView.getVisibility() != View.GONE) ringView.setVisibility(View.GONE);
            params.width = 1;
            params.height = 1;
            params.x = Math.round(effCutoutX - 0.5f);
            params.y = topAnchorY;
            params.gravity = Gravity.TOP | Gravity.START;
            params.windowAnimations = 0;
            params.flags = targetFlags;
            try { windowManager.updateViewLayout(ringView, params); } catch (Exception ignored) {}
            return;
        }

        int reqW;
        int reqH;
        int reqY;

        boolean isMorphFlight = ringView != null && ringView.isMorphInFlight();

        if (IslandState.currentIsland == IslandState.STATE_CALIBRATION) {
            reqW = Math.round(Math.max(IslandConfig.cutoutRadius * 3.0f, IslandConfig.dpToPx(72)));
            reqH = reqW;
            reqY = Math.round(effCutoutY - (reqH / 2.0f));
        } else if (IslandState.isExpanded || isMorphFlight) {
            int defaultCardW = (IslandConfig.customCardWidth > 0) ? Math.round(IslandConfig.dpToPx(IslandConfig.customCardWidth)) : Math.round(IslandConfig.dpToPx(320));
            reqW = Math.min(IslandConfig.displayWidthPx - Math.round(IslandConfig.dpToPx(16)), defaultCardW);
            int cardState = (IslandState.previousIsland != IslandState.STATE_IDLE && !IslandState.isExpanded) ? IslandState.previousIsland : IslandState.currentIsland;
            int rawCardH = (IslandConfig.customCardHeight > 0) ? Math.round(IslandConfig.dpToPx(IslandConfig.customCardHeight)) : Math.max(getDefaultCardHeight(IslandState.currentIsland), getDefaultCardHeight(cardState));
            reqH = Math.min(IslandConfig.displayHeightPx - topAnchorY - Math.round(IslandConfig.dpToPx(16)), rawCardH);
            reqY = topAnchorY;
        } else if (IslandState.isCollapsing || IslandState.currentIsland == IslandState.STATE_IDLE) {
            int defaultW = getDefaultPillWidth(IslandState.currentIsland);
            reqW = (IslandConfig.customPillWidth > 0) ? Math.round(IslandConfig.dpToPx(IslandConfig.customPillWidth)) : defaultW;
            reqH = Math.round(IslandConfig.cutoutRadius * 2.0f);
            reqY = Math.round(effCutoutY - IslandConfig.cutoutRadius);
        } else {
            int defaultW = getDefaultPillWidth(IslandState.currentIsland);
            reqW = (IslandConfig.customPillWidth > 0) ? Math.round(IslandConfig.dpToPx(IslandConfig.customPillWidth)) : defaultW;
            reqH = compactH;
            reqY = topAnchorY;
        }

        int newW = reqW;
        int newH = reqH;
        int targetGravity = Gravity.TOP | Gravity.START;
        int targetX = Math.round(effCutoutX - (newW / 2.0f));

        if (targetX <= 0 && effCutoutX > 0 && IslandConfig.displayWidthPx > newW) {
            targetX = Math.max(0, Math.round(effCutoutX - (newW / 2.0f)));
        }
        if (IslandConfig.displayWidthPx > newW) {
            targetX = Math.max(0, Math.min(IslandConfig.displayWidthPx - newW, targetX));
        }

        boolean dimChanged = Math.abs(params.width - newW) > 1 || Math.abs(params.height - newH) > 1;
        boolean posChanged = Math.abs(params.y - reqY) > 1 || params.x != targetX || params.gravity != targetGravity;
        boolean flagChanged = params.flags != targetFlags || params.windowAnimations != 0;

        if (dimChanged || posChanged || flagChanged) {
            params.width = newW;
            params.height = newH;
            params.y = reqY;
            params.flags = targetFlags;
            params.gravity = targetGravity;
            params.x = targetX;
            params.windowAnimations = 0;
        }

        try {
            java.lang.reflect.Field pf = WindowManager.LayoutParams.class.getField("privateFlags");
            int curPf = pf.getInt(params);
            if ((curPf & 0x00000040) == 0) {
                pf.setInt(params, curPf | 0x00000040);
                dimChanged = true;
            }
        } catch (Throwable ignored) {}

        boolean visChanged = false;
        if (ringView.getVisibility() != View.VISIBLE) {
            ringView.setVisibility(View.VISIBLE);
            visChanged = true;
        }

        if (dimChanged || posChanged || flagChanged || visChanged) {
            try { windowManager.updateViewLayout(ringView, params); } catch (Exception ignored) {}
        }
    }
}
