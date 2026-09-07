package com.hyperring;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HyperRingOverlay {
    // Context and window references
    private static Context context;
    private static Context sysContext;
    private static WindowManager windowManager;
    private static Display defaultDisplay;
    private static RingView ringView;
    private static WindowManager.LayoutParams params;
    private static Handler handler;

    // Filesystem paths
    private static String stateDir    = "/data/adb/modules/hyperring/state";
    private static String configPath  = stateDir + "/config.json";
    private static String statusPath  = stateDir + "/status.json";
    private static String triggerPath = stateDir + "/trigger.cmd";

    // Geometry configuration
    private static int cutoutCenterX = 0;
    private static int cutoutCenterY = 0;
    private static int cutoutRadius  = 36;
    private static boolean enableMedia    = true;
    private static boolean enableCharging = true;
    private static boolean enableHyperDL  = true;
    private static boolean enableHyperCore = true;
    private static float springStiffness = 360.0f;
    private static float springDamping   = 0.76f;
    private static boolean autoExpandCharging = true;
    private static int expandTimeoutMs = 3500;
    private static long lastConfigModified = 0L;

    // Island states
    public static final int STATE_IDLE     = 0;
    public static final int STATE_CHARGING = 1;
    public static final int STATE_MEDIA    = 2;
    public static final int STATE_HYPERDL  = 3;

    private static int currentIsland = STATE_IDLE;
    private static boolean isExpanded = false;
    private static long expandCollapseTime = 0L;
    private static int currentDisplayRotation = Surface.ROTATION_0;

    // Screen power lifecycle
    private static volatile boolean isScreenInteractive = true;

    // Telemetry
    private static int batteryPct = 100;
    private static boolean isCharging = false;
    private static String chargeWattStr = "33W";
    private static String chargeCurrentStr = "4200mA";
    private static String hyperCoreProfile = "Interactive";

    private static String mediaTitle = "No active playback";
    private static String mediaArtist = "Media";
    private static boolean isMediaPlaying = false;
    private static long lastMediaCheck = 0L;

    private static boolean isHyperDLActive = false;
    private static String hyperDLSpeed = "0 MB/s";
    private static int hyperDLProgress = 0;
    private static String hyperDLFile = "File Download";
    private static long lastHyperDLCheck = 0L;
    private static long lastStatusPersist = 0L;

    // Spring physics model
    public static class Spring {
        public float current;
        public float target;
        public float velocity = 0f;
        public float stiffness = 360f;
        public float damping = 28f;

        public Spring(float initial, float k, float dampingRatio) {
            this.current = initial;
            this.target = initial;
            this.stiffness = k;
            this.damping = (float) (2.0 * Math.sqrt(k) * dampingRatio);
        }

        public void setStiffnessAndDamping(float k, float dampingRatio) {
            this.stiffness = k;
            this.damping = (float) (2.0 * Math.sqrt(k) * dampingRatio);
        }

        public void setTarget(float target) {
            this.target = target;
        }

        public boolean update(float dt) {
            float displacement = current - target;
            float springForce = -stiffness * displacement;
            float dampingForce = -damping * velocity;
            float acceleration = springForce + dampingForce;

            velocity += acceleration * dt;
            current += velocity * dt;

            if (Math.abs(velocity) < 0.1f && Math.abs(displacement) < 0.1f) {
                current = target;
                velocity = 0f;
                return false;
            }
            return true;
        }
    }

    // Dynamic morphing springs
    private static Spring springW;
    private static Spring springH;
    private static Spring springR;
    private static Spring springContentAlpha;

    // Audio visualizer bars simulation
    private static float[] barHeights = new float[]{0.3f, 0.7f, 0.5f, 0.9f};
    private static float[] barTargets = new float[]{0.3f, 0.7f, 0.5f, 0.9f};
    private static long lastWaveStep = 0L;

    // Graphics objects
    private static Paint paintOledBlack;
    private static Paint paintBorder;
    private static Paint paintTextPrimary;
    private static Paint paintTextSecondary;
    private static Paint paintTextTertiary;
    private static Paint paintAccentGreen;
    private static Paint paintAccentCyan;
    private static Paint paintIconFill;
    private static Path squirclePath;
    private static RectF tempRectF;

    // Touch gesture tracking
    private static float touchDownY = 0f;
    private static float touchDownX = 0f;

    // Loop timing
    private static long lastFrameTime = 0L;

    public static void main(String[] args) {
        if (args.length > 0 && args[0] != null && !args[0].isEmpty()) {
            stateDir = args[0];
            configPath = stateDir + "/config.json";
            statusPath = stateDir + "/status.json";
            triggerPath = stateDir + "/trigger.cmd";
        }

        System.out.println("HyperRing service initialized (pid " + android.os.Process.myPid() + ")");

        try {
            Looper.prepareMainLooper();
        } catch (Throwable ignored) {}

        handler = new Handler(Looper.getMainLooper());

        // Load system fonts natively to prevent minikin aborts
        try {
            Method fontMapMethod = Typeface.class.getDeclaredMethod("loadPreinstalledSystemFontMap");
            fontMapMethod.setAccessible(true);
            fontMapMethod.invoke(null);
        } catch (Throwable ignored) {}

        try {
            Method getMapMethod = Typeface.class.getDeclaredMethod("getSystemFontMap");
            getMapMethod.setAccessible(true);
            Map<?, ?> map = (Map<?, ?>) getMapMethod.invoke(null);
            if (map != null) {
                Object sansSerif = map.get("sans-serif");
                if (sansSerif instanceof Typeface) {
                    Method setDef = Typeface.class.getDeclaredMethod("setDefault", Typeface.class);
                    setDef.setAccessible(true);
                    setDef.invoke(null, (Typeface) sansSerif);
                }
            }
        } catch (Throwable ignored) {}

        // Initialize libbinder worker thread pool for IPC callbacks
        try {
            Class<?> binderInternal = Class.forName("com.android.internal.os.BinderInternal");
            try {
                Method disableBg = binderInternal.getMethod("disableBackgroundScheduling", boolean.class);
                disableBg.invoke(null, true);
            } catch (Throwable ignored) {}

            try {
                final Method joinThreadPool = binderInternal.getMethod("joinThreadPool");
                Thread binderThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            joinThreadPool.invoke(null);
                        } catch (Throwable ignored) {}
                    }
                }, "HyperRing-Binder");
                binderThread.setDaemon(true);
                binderThread.start();
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}

        // Acquire system context
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method systemMainMethod = activityThreadClass.getMethod("systemMain");
            Object activityThread = systemMainMethod.invoke(null);
            sysContext = (Context) activityThreadClass.getMethod("getSystemContext").invoke(activityThread);
        } catch (Throwable t) {
            System.err.println("Failed to acquire system context: " + t.getMessage());
        }

        if (sysContext == null) {
            System.err.println("Fatal: system context unavailable");
            return;
        }

        context = sysContext;
        try {
            DisplayManager dm = (DisplayManager) sysContext.getSystemService(Context.DISPLAY_SERVICE);
            if (dm != null) {
                defaultDisplay = dm.getDisplay(Display.DEFAULT_DISPLAY);
                if (defaultDisplay != null) {
                    Context dCtx = sysContext.createDisplayContext(defaultDisplay);
                    if (dCtx != null) context = dCtx;
                }
            }
        } catch (Throwable ignored) {}

        // Enable SYSTEM_ALERT_WINDOW permission across system uids
        try {
            Class<?> appOpsClass = Class.forName("android.app.AppOpsManager");
            Object appOps = sysContext.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps != null) {
                Method setUidModeMethod = null;
                for (Method m : appOpsClass.getDeclaredMethods()) {
                    if ("setUidMode".equals(m.getName()) && m.getParameterTypes().length >= 3) {
                        setUidModeMethod = m;
                        break;
                    }
                }
                if (setUidModeMethod != null) {
                    setUidModeMethod.setAccessible(true);
                    int myUid = android.os.Process.myUid();
                    setUidModeMethod.invoke(appOps, 24, myUid, 0);
                    setUidModeMethod.invoke(appOps, 24, 1000, 0);
                    setUidModeMethod.invoke(appOps, 24, 2000, 0);
                }
            }
        } catch (Throwable ignored) {}

        // Auto-detect cutout defaults if unconfigured
        resolveCutoutDefaults();
        readConfig();

        float initDiameter = cutoutRadius * 2.0f;
        springW = new Spring(initDiameter, springStiffness, springDamping);
        springH = new Spring(initDiameter, springStiffness, springDamping);
        springR = new Spring(cutoutRadius, springStiffness, springDamping);
        springContentAlpha = new Spring(0.0f, springStiffness, springDamping);

        initGraphics();
        registerReceivers();

        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    attachWindow();
                    startEngineLoop();
                } catch (Throwable t) {
                    System.err.println("Window mount failed: " + t.getMessage());
                }
            }
        });

        Looper.loop();
    }

    private static void resolveCutoutDefaults() {
        int screenW = 1080;
        int statusBarH = dpToPx(38);

        try {
            DisplayMetrics realMetrics = new DisplayMetrics();
            if (defaultDisplay != null) {
                defaultDisplay.getRealMetrics(realMetrics);
                screenW = realMetrics.widthPixels;
            }
        } catch (Throwable ignored) {}

        try {
            int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                statusBarH = context.getResources().getDimensionPixelSize(resourceId);
            }
        } catch (Throwable ignored) {}

        if (cutoutCenterX <= 0) {
            cutoutCenterX = screenW / 2;
        }
        if (cutoutCenterY <= 0) {
            cutoutCenterY = Math.max(dpToPx(18), statusBarH / 2);
        }
    }

    private static void initGraphics() {
        paintOledBlack = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintOledBlack.setColor(Color.BLACK);
        paintOledBlack.setStyle(Paint.Style.FILL);

        paintBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBorder.setColor(Color.argb(26, 255, 255, 255));
        paintBorder.setStyle(Paint.Style.STROKE);
        paintBorder.setStrokeWidth(dpToPx(0.75f));

        paintTextPrimary = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTextPrimary.setColor(Color.WHITE);
        try {
            paintTextPrimary.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        } catch (Throwable ignored) {}

        paintTextSecondary = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTextSecondary.setColor(Color.argb(165, 255, 255, 255));
        try {
            paintTextSecondary.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        } catch (Throwable ignored) {}

        paintTextTertiary = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTextTertiary.setColor(Color.argb(100, 255, 255, 255));
        try {
            paintTextTertiary.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        } catch (Throwable ignored) {}

        paintAccentGreen = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAccentGreen.setColor(Color.parseColor("#22C55E"));
        paintAccentGreen.setStyle(Paint.Style.FILL);

        paintAccentCyan = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAccentCyan.setColor(Color.parseColor("#38BDF8"));
        paintAccentCyan.setStyle(Paint.Style.FILL);

        paintIconFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintIconFill.setColor(Color.WHITE);
        paintIconFill.setStyle(Paint.Style.FILL);

        squirclePath = new Path();
        tempRectF = new RectF();
    }

    private static void registerReceivers() {
        // Battery Receiver
        try {
            IntentFilter batFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent intent) {
                    if (intent == null) return;
                    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    if (level >= 0 && scale > 0) {
                        batteryPct = Math.round((level / (float) scale) * 100f);
                    }
                    int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    boolean chargingNow = status == BatteryManager.BATTERY_STATUS_CHARGING
                            || status == BatteryManager.BATTERY_STATUS_FULL;

                    if (chargingNow && !isCharging && enableCharging) {
                        isCharging = true;
                        triggerChargingEvent();
                    } else if (!chargingNow) {
                        isCharging = false;
                        if (currentIsland == STATE_CHARGING) {
                            currentIsland = STATE_IDLE;
                            isExpanded = false;
                        }
                    }
                }
            }, batFilter);
        } catch (Throwable ignored) {}

        // Screen Power & Interactive State Receiver (Zero background battery drain)
        try {
            IntentFilter screenFilter = new IntentFilter();
            screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
            screenFilter.addAction(Intent.ACTION_SCREEN_ON);
            screenFilter.addAction(Intent.ACTION_USER_PRESENT);
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent intent) {
                    if (intent == null) return;
                    String act = intent.getAction();
                    if (Intent.ACTION_SCREEN_OFF.equals(act)) {
                        isScreenInteractive = false;
                    } else if (Intent.ACTION_SCREEN_ON.equals(act) || Intent.ACTION_USER_PRESENT.equals(act)) {
                        isScreenInteractive = true;
                        lastFrameTime = SystemClock.uptimeMillis();
                    }
                }
            }, screenFilter);
        } catch (Throwable ignored) {}
    }

    private static void triggerChargingEvent() {
        currentIsland = STATE_CHARGING;
        readHyperCoreStatus();
        if (autoExpandCharging) {
            isExpanded = true;
            expandCollapseTime = SystemClock.uptimeMillis() + expandTimeoutMs;
        } else {
            isExpanded = false;
            expandCollapseTime = SystemClock.uptimeMillis() + 4000;
        }
    }

    private static void attachWindow() {
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) return;

        ringView = new RingView(context);

        int type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

        int initW = Math.round(cutoutRadius * 2.2f);
        int initH = Math.round(cutoutRadius * 2.2f);

        params = new WindowManager.LayoutParams(
                initW,
                initH,
                type,
                flags,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = cutoutCenterX - (initW / 2);
        params.y = cutoutCenterY - (initH / 2);

        try {
            java.lang.reflect.Field f = WindowManager.LayoutParams.class.getField("LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS");
            params.layoutInDisplayCutoutMode = f.getInt(null);
        } catch (Throwable t) {
            try {
                java.lang.reflect.Field f = WindowManager.LayoutParams.class.getField("LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES");
                params.layoutInDisplayCutoutMode = f.getInt(null);
            } catch (Throwable ignored) {}
        }

        windowManager.addView(ringView, params);
        System.out.println("HyperRing overlay mounted at (" + params.x + ", " + params.y + ")");
    }

    static class RingView extends View {
        public RingView(Context context) {
            super(context);
            setClickable(true);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();

            // Strict Hit-Testing: Only capture touches within the active island shape
            float curW = springW.current;
            float curH = springH.current;
            float pillLeft = (getWidth() - curW) / 2.0f;
            float pillTop  = (getHeight() - curH) / 2.0f;
            float margin   = dpToPx(8);

            RectF hitBox = new RectF(pillLeft - margin, pillTop - margin, pillLeft + curW + margin, pillTop + curH + margin);

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (!hitBox.contains(x, y)) {
                    // Tap outside island: if expanded, collapse cleanly
                    if (isExpanded) {
                        isExpanded = false;
                    }
                    // Pass touch through to underlying applications
                    return false;
                }
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchDownX = x;
                    touchDownY = y;
                    if (springH != null) springH.velocity += 100f;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dy = y - touchDownY;
                    if (dy > dpToPx(24) && !isExpanded && currentIsland != STATE_IDLE) {
                        isExpanded = true;
                        expandCollapseTime = 0L;
                        return true;
                    } else if (dy < -dpToPx(24) && isExpanded) {
                        isExpanded = false;
                        return true;
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    float totalDistX = Math.abs(x - touchDownX);
                    float totalDistY = Math.abs(y - touchDownY);
                    if (totalDistX < dpToPx(16) && totalDistY < dpToPx(16)) {
                        handleTap(x, y);
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            renderIsland(canvas, getWidth(), getHeight());
        }
    }

    private static void handleTap(float x, float y) {
        if (!isExpanded) {
            if (currentIsland != STATE_IDLE) {
                isExpanded = true;
                expandCollapseTime = 0L;
            }
        } else {
            if (currentIsland == STATE_MEDIA) {
                float viewW = ringView.getWidth();
                float viewH = ringView.getHeight();
                float centerY = viewH * 0.72f;

                float playBtnX = viewW * 0.5f;
                float prevBtnX = viewW * 0.28f;
                float nextBtnX = viewW * 0.72f;
                float hitRadius = dpToPx(26);

                if (Math.abs(y - centerY) < hitRadius) {
                    if (Math.abs(x - playBtnX) < hitRadius) {
                        toggleMediaPlayback();
                        return;
                    } else if (Math.abs(x - prevBtnX) < hitRadius) {
                        skipMediaPrevious();
                        return;
                    } else if (Math.abs(x - nextBtnX) < hitRadius) {
                        skipMediaNext();
                        return;
                    }
                }
            }
            isExpanded = false;
        }
    }

    private static void renderIsland(Canvas canvas, float w, float h) {
        float curW = springW.current;
        float curH = springH.current;
        float curR = springR.current;

        float left = (w - curW) / 2.0f;
        float top  = (h - curH) / 2.0f;
        float right = left + curW;
        float bottom = top + curH;

        tempRectF.set(left, top, right, bottom);
        squirclePath.reset();
        squirclePath.addRoundRect(tempRectF, curR, curR, Path.Direction.CW);

        // Absolute OLED black ground
        canvas.drawPath(squirclePath, paintOledBlack);

        // Specular border on active pill/card
        if (currentIsland != STATE_IDLE || isExpanded) {
            canvas.drawPath(squirclePath, paintBorder);
        }

        float contentAlpha = springContentAlpha.current;
        if (contentAlpha > 0.05f) {
            canvas.save();
            canvas.clipPath(squirclePath);

            if (isExpanded) {
                renderExpandedContent(canvas, left, top, curW, curH, contentAlpha);
            } else {
                renderCompactContent(canvas, left, top, curW, curH, contentAlpha);
            }

            canvas.restore();
        }
    }

    private static void renderCompactContent(Canvas canvas, float left, float top, float curW, float curH, float alpha) {
        float right = left + curW;
        float holeCenterX = left + (curW / 2.0f);
        float centerY = top + (curH / 2.0f);
        float holeR = cutoutRadius;
        int intAlpha = (int) (alpha * 255);

        // Calculate safety boundaries around camera hole to prevent any visual overlap
        float leftSafeX = Math.max(left + dpToPx(14), holeCenterX - holeR - dpToPx(16));
        float rightSafeX = Math.min(right - dpToPx(38), holeCenterX + holeR + dpToPx(10));

        if (currentIsland == STATE_MEDIA) {
            // Left: Vector audio wave disc
            paintAccentCyan.setAlpha(intAlpha);
            canvas.drawCircle(leftSafeX, centerY, dpToPx(5), paintAccentCyan);

            // Right: Vector audio visualizer bars
            renderAudioBars(canvas, rightSafeX, centerY, alpha);

        } else if (currentIsland == STATE_CHARGING) {
            // Left: Clean vector lightning bolt
            paintAccentGreen.setAlpha(intAlpha);
            drawBoltIcon(canvas, leftSafeX, centerY, dpToPx(11), paintAccentGreen);

            // Right: Battery percentage with tabular numbers
            paintTextPrimary.setTextSize(spToPx(11.5f));
            paintTextPrimary.setTextAlign(Paint.Align.LEFT);
            paintTextPrimary.setAlpha(intAlpha);
            canvas.drawText(batteryPct + "%", rightSafeX, centerY + dpToPx(4), paintTextPrimary);

        } else if (currentIsland == STATE_HYPERDL) {
            // Left: Progress indicator point
            paintAccentCyan.setAlpha(intAlpha);
            canvas.drawCircle(leftSafeX, centerY, dpToPx(4.5f), paintAccentCyan);

            // Right: Speed metrics
            paintTextPrimary.setTextSize(spToPx(11));
            paintTextPrimary.setTextAlign(Paint.Align.LEFT);
            paintTextPrimary.setAlpha(intAlpha);
            canvas.drawText(hyperDLSpeed, rightSafeX, centerY + dpToPx(4), paintTextPrimary);
        }
    }

    private static void renderAudioBars(Canvas canvas, float startX, float centerY, float alpha) {
        paintAccentCyan.setAlpha((int) (alpha * 255));
        float barW = dpToPx(2.2f);
        float gap = dpToPx(2.0f);
        float maxH = dpToPx(13);

        for (int i = 0; i < 4; i++) {
            float bx = startX + (i * (barW + gap));
            float bh = maxH * barHeights[i];
            float bTop = centerY - (bh / 2.0f);
            float bBottom = centerY + (bh / 2.0f);
            canvas.drawRoundRect(new RectF(bx, bTop, bx + barW, bBottom), barW / 2f, barW / 2f, paintAccentCyan);
        }
    }

    private static void renderExpandedContent(Canvas canvas, float left, float top, float curW, float curH, float alpha) {
        int intAlpha = (int) (alpha * 255);
        paintTextPrimary.setAlpha(intAlpha);
        paintTextSecondary.setAlpha((int) (alpha * 165));
        paintTextTertiary.setAlpha((int) (alpha * 90));
        paintIconFill.setAlpha(intAlpha);

        if (currentIsland == STATE_MEDIA) {
            // Album art container
            float artSize = dpToPx(50);
            float artLeft = left + dpToPx(18);
            float artTop = top + dpToPx(18);
            paintAccentCyan.setAlpha((int) (alpha * 40));
            canvas.drawRoundRect(new RectF(artLeft, artTop, artLeft + artSize, artTop + artSize), dpToPx(12), dpToPx(12), paintAccentCyan);

            // Inner disc icon
            paintAccentCyan.setAlpha(intAlpha);
            canvas.drawCircle(artLeft + artSize / 2f, artTop + artSize / 2f, dpToPx(10), paintAccentCyan);
            paintOledBlack.setAlpha(intAlpha);
            canvas.drawCircle(artLeft + artSize / 2f, artTop + artSize / 2f, dpToPx(4), paintOledBlack);

            // Text metadata
            float textLeft = artLeft + artSize + dpToPx(14);
            paintTextPrimary.setTextSize(spToPx(14));
            paintTextPrimary.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(truncate(mediaTitle, 26), textLeft, artTop + dpToPx(20), paintTextPrimary);

            paintTextSecondary.setTextSize(spToPx(12));
            canvas.drawText(truncate(mediaArtist.isEmpty() ? "Media" : mediaArtist, 28), textLeft, artTop + dpToPx(38), paintTextSecondary);

            // Progress seekbar
            float barY = top + dpToPx(84);
            float barW = curW - dpToPx(36);
            paintTextTertiary.setAlpha(45);
            canvas.drawRoundRect(new RectF(left + dpToPx(18), barY, left + dpToPx(18) + barW, barY + dpToPx(2.5f)), dpToPx(2), dpToPx(2), paintTextTertiary);

            paintAccentCyan.setAlpha(intAlpha);
            canvas.drawRoundRect(new RectF(left + dpToPx(18), barY, left + dpToPx(18) + (barW * 0.42f), barY + dpToPx(2.5f)), dpToPx(2), dpToPx(2), paintAccentCyan);

            // Vector playback controls
            float btnY = top + curH - dpToPx(24);
            float centerX = left + (curW / 2.0f);
            float prevBtnX = centerX - dpToPx(65);
            float nextBtnX = centerX + dpToPx(65);

            drawPrevIcon(canvas, prevBtnX, btnY, dpToPx(14), paintIconFill);
            if (isMediaPlaying) {
                drawPauseIcon(canvas, centerX, btnY, dpToPx(14), paintIconFill);
            } else {
                drawPlayIcon(canvas, centerX, btnY, dpToPx(14), paintIconFill);
            }
            drawNextIcon(canvas, nextBtnX, btnY, dpToPx(14), paintIconFill);

        } else if (currentIsland == STATE_CHARGING) {
            // Charging header
            float iconX = left + dpToPx(28);
            float centerY = top + dpToPx(36);
            paintAccentGreen.setAlpha(intAlpha);
            drawBoltIcon(canvas, iconX, centerY, dpToPx(16), paintAccentGreen);

            paintTextPrimary.setTextSize(spToPx(17));
            paintTextPrimary.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("Charging • " + batteryPct + "%", iconX + dpToPx(20), centerY + dpToPx(6), paintTextPrimary);

            // Technical details
            paintTextSecondary.setTextSize(spToPx(12));
            canvas.drawText("Power " + chargeWattStr + " • Current " + chargeCurrentStr, left + dpToPx(24), top + dpToPx(74), paintTextSecondary);
            paintTextTertiary.setTextSize(spToPx(11));
            canvas.drawText("Profile " + hyperCoreProfile, left + dpToPx(24), top + dpToPx(94), paintTextTertiary);

        } else if (currentIsland == STATE_HYPERDL) {
            // HyperDL card
            paintTextPrimary.setTextSize(spToPx(14));
            paintTextPrimary.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("Download Active", left + dpToPx(20), top + dpToPx(32), paintTextPrimary);

            paintTextSecondary.setTextSize(spToPx(12));
            canvas.drawText(truncate(hyperDLFile, 30) + " • " + hyperDLSpeed, left + dpToPx(20), top + dpToPx(52), paintTextSecondary);

            float barY = top + dpToPx(70);
            float barW = curW - dpToPx(40);
            paintTextTertiary.setAlpha(45);
            canvas.drawRoundRect(new RectF(left + dpToPx(20), barY, left + dpToPx(20) + barW, barY + dpToPx(3)), dpToPx(2), dpToPx(2), paintTextTertiary);

            paintAccentCyan.setAlpha(intAlpha);
            float fillRatio = Math.max(0.04f, Math.min(1.0f, hyperDLProgress / 100f));
            canvas.drawRoundRect(new RectF(left + dpToPx(20), barY, left + dpToPx(20) + (barW * fillRatio), barY + dpToPx(3)), dpToPx(2), dpToPx(2), paintAccentCyan);
        }
    }

    // Vector iconography helpers
    private static void drawBoltIcon(Canvas canvas, float cx, float cy, float size, Paint paint) {
        Path p = new Path();
        p.moveTo(cx + size * 0.12f, cy - size * 0.5f);
        p.lineTo(cx - size * 0.38f, cy + size * 0.05f);
        p.lineTo(cx - size * 0.05f, cy + size * 0.05f);
        p.lineTo(cx - size * 0.12f, cy + size * 0.5f);
        p.lineTo(cx + size * 0.38f, cy - size * 0.05f);
        p.lineTo(cx + size * 0.05f, cy - size * 0.05f);
        p.close();
        canvas.drawPath(p, paint);
    }

    private static void drawPlayIcon(Canvas canvas, float cx, float cy, float size, Paint paint) {
        Path p = new Path();
        p.moveTo(cx - size * 0.35f, cy - size * 0.45f);
        p.lineTo(cx + size * 0.45f, cy);
        p.lineTo(cx - size * 0.35f, cy + size * 0.45f);
        p.close();
        canvas.drawPath(p, paint);
    }

    private static void drawPauseIcon(Canvas canvas, float cx, float cy, float size, Paint paint) {
        float barW = size * 0.22f;
        float barH = size * 0.85f;
        float gap = size * 0.16f;
        canvas.drawRoundRect(new RectF(cx - gap - barW, cy - barH / 2f, cx - gap, cy + barH / 2f), dpToPx(1), dpToPx(1), paint);
        canvas.drawRoundRect(new RectF(cx + gap, cy - barH / 2f, cx + gap + barW, cy + barH / 2f), dpToPx(1), dpToPx(1), paint);
    }

    private static void drawNextIcon(Canvas canvas, float cx, float cy, float size, Paint paint) {
        Path p = new Path();
        p.moveTo(cx - size * 0.45f, cy - size * 0.35f);
        p.lineTo(cx, cy);
        p.lineTo(cx - size * 0.45f, cy + size * 0.35f);
        p.close();
        p.moveTo(cx, cy - size * 0.35f);
        p.lineTo(cx + size * 0.45f, cy);
        p.lineTo(cx, cy + size * 0.35f);
        p.close();
        canvas.drawPath(p, paint);
    }

    private static void drawPrevIcon(Canvas canvas, float cx, float cy, float size, Paint paint) {
        Path p = new Path();
        p.moveTo(cx, cy - size * 0.35f);
        p.lineTo(cx - size * 0.45f, cy);
        p.lineTo(cx, cy + size * 0.35f);
        p.close();
        p.moveTo(cx + size * 0.45f, cy - size * 0.35f);
        p.lineTo(cx, cy);
        p.lineTo(cx + size * 0.45f, cy + size * 0.35f);
        p.close();
        canvas.drawPath(p, paint);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    private static void startEngineLoop() {
        lastFrameTime = SystemClock.uptimeMillis();

        handler.post(new Runnable() {
            @Override
            public void run() {
                // If screen is off, suspend high-refresh rendering to conserve battery
                if (!isScreenInteractive) {
                    handler.postDelayed(this, 1500);
                    return;
                }

                long now = SystemClock.uptimeMillis();
                float dt = Math.min(0.033f, (now - lastFrameTime) / 1000.0f);
                lastFrameTime = now;

                syncConfigAndTriggers();
                checkDisplayOrientation();
                pollDataSources(now);
                resolveTargetState(now);

                boolean movingW = springW.update(dt);
                boolean movingH = springH.update(dt);
                boolean movingR = springR.update(dt);
                boolean movingC = springContentAlpha.update(dt);

                if (currentIsland == STATE_MEDIA && isMediaPlaying && (now - lastWaveStep > 85)) {
                    stepAudioBars();
                    lastWaveStep = now;
                }

                updateWindowBounds();
                if (ringView != null) ringView.invalidate();

                if (now - lastStatusPersist > 2500) {
                    persistStatusAsync();
                    lastStatusPersist = now;
                }

                boolean activeAnimation = movingW || movingH || movingR || movingC || (currentIsland == STATE_MEDIA && isMediaPlaying);
                int nextDelay = activeAnimation ? 14 : 60;
                handler.postDelayed(this, nextDelay);
            }
        });
    }

    private static void checkDisplayOrientation() {
        if (defaultDisplay == null || ringView == null) return;
        int rot = defaultDisplay.getRotation();
        if (rot != currentDisplayRotation) {
            currentDisplayRotation = rot;
            if (currentDisplayRotation != Surface.ROTATION_0) {
                // In landscape gaming/video playback, hide overlay completely
                ringView.setVisibility(View.GONE);
            } else {
                ringView.setVisibility(View.VISIBLE);
            }
        }
    }

    private static void resolveTargetState(long now) {
        if (expandCollapseTime > 0 && now > expandCollapseTime) {
            if (isExpanded) {
                isExpanded = false;
                expandCollapseTime = now + 4000;
            } else if (currentIsland == STATE_CHARGING) {
                currentIsland = STATE_IDLE;
                expandCollapseTime = 0L;
            }
        }

        float targetW;
        float targetH;
        float targetR;
        float targetContentAlpha;

        if (currentIsland == STATE_IDLE) {
            targetW = cutoutRadius * 2.0f;
            targetH = cutoutRadius * 2.0f;
            targetR = cutoutRadius;
            targetContentAlpha = 0.0f;
        } else if (!isExpanded) {
            targetH = Math.max(cutoutRadius * 2.0f, dpToPx(34));
            targetR = targetH / 2.0f;
            targetContentAlpha = 1.0f;

            if (currentIsland == STATE_CHARGING) {
                targetW = dpToPx(136);
            } else if (currentIsland == STATE_MEDIA) {
                targetW = dpToPx(146);
            } else {
                targetW = dpToPx(150);
            }
        } else {
            targetW = dpToPx(320);
            targetContentAlpha = 1.0f;

            if (currentIsland == STATE_MEDIA) {
                targetH = dpToPx(132);
                targetR = dpToPx(24);
            } else if (currentIsland == STATE_CHARGING) {
                targetH = dpToPx(110);
                targetR = dpToPx(24);
            } else {
                targetH = dpToPx(96);
                targetR = dpToPx(22);
            }
        }

        springW.setTarget(targetW);
        springH.setTarget(targetH);
        springR.setTarget(targetR);
        springContentAlpha.setTarget(targetContentAlpha);
    }

    private static void updateWindowBounds() {
        if (params == null || windowManager == null || ringView == null) return;

        int reqW = Math.round(Math.max(springW.current, springW.target) + dpToPx(20));
        int reqH = Math.round(Math.max(springH.current, springH.target) + dpToPx(20));

        int newX = cutoutCenterX - (reqW / 2);
        int newY = cutoutCenterY - (reqH / 2);

        int targetFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;

        // In idle state, ignore touches so status bar / camera area is completely transparent to touches
        if (currentIsland == STATE_IDLE) {
            targetFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }

        boolean layoutChanged = params.width != reqW || params.height != reqH || params.x != newX || params.y != newY || params.flags != targetFlags;

        if (layoutChanged) {
            params.width = reqW;
            params.height = reqH;
            params.x = newX;
            params.y = newY;
            params.flags = targetFlags;
            try {
                windowManager.updateViewLayout(ringView, params);
            } catch (Exception ignored) {}
        }
    }

    private static void stepAudioBars() {
        for (int i = 0; i < 4; i++) {
            barHeights[i] += (barTargets[i] - barHeights[i]) * 0.45f;
            if (Math.abs(barTargets[i] - barHeights[i]) < 0.1f) {
                barTargets[i] = 0.2f + (float) Math.random() * 0.8f;
            }
        }
    }

    private static void pollDataSources(long now) {
        if (enableMedia && (now - lastMediaCheck > 1200)) {
            lastMediaCheck = now;
            queryMediaSession();
        }

        if (enableHyperDL && (now - lastHyperDLCheck > 1500)) {
            lastHyperDLCheck = now;
            queryHyperDLStatus();
        }

        if (isCharging && enableCharging && currentIsland == STATE_CHARGING) {
            // Retain active charging state
        } else if (isHyperDLActive && enableHyperDL) {
            if (currentIsland != STATE_HYPERDL) {
                currentIsland = STATE_HYPERDL;
                isExpanded = false;
            }
        } else if (isMediaPlaying && enableMedia) {
            if (currentIsland != STATE_MEDIA) {
                currentIsland = STATE_MEDIA;
                isExpanded = false;
            }
        } else if (currentIsland != STATE_CHARGING) {
            currentIsland = STATE_IDLE;
            isExpanded = false;
        }
    }

    private static void queryMediaSession() {
        try {
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"dumpsys", "media_session"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            boolean foundPlaying = false;
            String tempTitle = "";
            String tempArtist = "";

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("state=PlaybackState")) {
                    if (line.contains("state=3")) {
                        foundPlaying = true;
                    }
                } else if (line.startsWith("description=")) {
                    String desc = line.substring(12);
                    String[] parts = desc.split(",");
                    if (parts.length > 0) tempTitle = parts[0].trim();
                    if (parts.length > 1) tempArtist = parts[1].trim();
                }
                if (foundPlaying && !tempTitle.isEmpty()) {
                    break;
                }
            }
            reader.close();
            p.destroy();

            isMediaPlaying = foundPlaying;
            if (foundPlaying) {
                if (!tempTitle.isEmpty()) mediaTitle = tempTitle;
                if (!tempArtist.isEmpty()) mediaArtist = tempArtist;
            }
        } catch (Throwable ignored) {}
    }

    private static void toggleMediaPlayback() {
        try {
            Runtime.getRuntime().exec(new String[]{"input", "keyevent", "85"});
            isMediaPlaying = !isMediaPlaying;
        } catch (Throwable ignored) {}
    }

    private static void skipMediaNext() {
        try {
            Runtime.getRuntime().exec(new String[]{"input", "keyevent", "87"});
        } catch (Throwable ignored) {}
    }

    private static void skipMediaPrevious() {
        try {
            Runtime.getRuntime().exec(new String[]{"input", "keyevent", "88"});
        } catch (Throwable ignored) {}
    }

    private static void queryHyperDLStatus() {
        File f = new File("/data/local/tmp/hyperdl_status.json");
        if (!f.exists()) {
            isHyperDLActive = false;
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            String json = sb.toString();

            String status = getJsonRawVal(json, "status");
            if ("downloading".equalsIgnoreCase(status) || "active".equalsIgnoreCase(status)) {
                isHyperDLActive = true;
                hyperDLSpeed = parseStr(json, "speed", "8.4 MB/s");
                hyperDLProgress = parseInt(json, "progress", 50);
                hyperDLFile = parseStr(json, "title", "File Download");
            } else {
                isHyperDLActive = false;
            }
        } catch (Throwable ignored) {
            isHyperDLActive = false;
        }
    }

    private static void readHyperCoreStatus() {
        File f = new File("/dev/hypercore_status.json");
        if (!f.exists()) f = new File("/data/adb/modules/hypercore/status.json");
        if (!f.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            String json = sb.toString();

            hyperCoreProfile = parseStr(json, "profile", hyperCoreProfile);
            chargeWattStr = parseStr(json, "watt", chargeWattStr);
            chargeCurrentStr = parseStr(json, "current", chargeCurrentStr);
        } catch (Throwable ignored) {}
    }

    private static void persistStatusAsync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String stateName = "idle";
                    if (currentIsland == STATE_CHARGING) stateName = "charging";
                    else if (currentIsland == STATE_MEDIA) stateName = "media";
                    else if (currentIsland == STATE_HYPERDL) stateName = "hyperdl";

                    StringBuilder sb = new StringBuilder();
                    sb.append("{\n");
                    sb.append("  \"active_island\": \"").append(stateName).append("\",\n");
                    sb.append("  \"expanded\": ").append(isExpanded).append(",\n");
                    sb.append("  \"media_title\": \"").append(mediaTitle.replace("\"", "\\\"")).append("\",\n");
                    sb.append("  \"media_artist\": \"").append(mediaArtist.replace("\"", "\\\"")).append("\",\n");
                    sb.append("  \"media_playing\": ").append(isMediaPlaying).append(",\n");
                    sb.append("  \"battery_pct\": ").append(batteryPct).append(",\n");
                    sb.append("  \"battery_charging\": ").append(isCharging).append(",\n");
                    sb.append("  \"hypercore_profile\": \"").append(hyperCoreProfile).append("\",\n");
                    sb.append("  \"hyperdl_active\": ").append(isHyperDLActive).append(",\n");
                    sb.append("  \"hyperdl_speed\": \"").append(hyperDLSpeed).append("\",\n");
                    sb.append("  \"hyperdl_progress\": ").append(hyperDLProgress).append("\n");
                    sb.append("}\n");

                    File tmp = new File(statusPath + ".tmp." + android.os.Process.myPid());
                    File target = new File(statusPath);
                    FileWriter fw = new FileWriter(tmp);
                    fw.write(sb.toString());
                    fw.flush();
                    fw.close();
                    tmp.renameTo(target);
                } catch (Throwable ignored) {}
            }
        }).start();
    }

    private static void syncConfigAndTriggers() {
        File trig = new File(triggerPath);
        if (trig.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(trig))) {
                String cmd = br.readLine();
                if (cmd != null) {
                    cmd = cmd.trim();
                    if ("charge".equalsIgnoreCase(cmd)) {
                        triggerChargingEvent();
                    } else if ("media".equalsIgnoreCase(cmd)) {
                        currentIsland = STATE_MEDIA;
                        isMediaPlaying = true;
                        mediaTitle = "Starboy";
                        mediaArtist = "The Weeknd";
                        isExpanded = false;
                    } else if ("expand".equalsIgnoreCase(cmd)) {
                        if (currentIsland == STATE_IDLE) currentIsland = STATE_MEDIA;
                        isExpanded = true;
                    } else if ("collapse".equalsIgnoreCase(cmd)) {
                        isExpanded = false;
                    } else if ("idle".equalsIgnoreCase(cmd)) {
                        currentIsland = STATE_IDLE;
                        isExpanded = false;
                    }
                }
            } catch (Throwable ignored) {}
            trig.delete();
        }

        File cfg = new File(configPath);
        if (cfg.exists()) {
            long mod = cfg.lastModified();
            if (mod != lastConfigModified) {
                readConfig();
                lastConfigModified = mod;
            }
        }
    }

    private static void readConfig() {
        File file = new File(configPath);
        if (!file.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            String json = sb.toString();

            cutoutCenterX = parseInt(json, "cutout_x", cutoutCenterX);
            cutoutCenterY = parseInt(json, "cutout_y", cutoutCenterY);
            cutoutRadius  = parseInt(json, "cutout_radius", cutoutRadius);
            enableMedia   = parseBool(json, "enable_media", enableMedia);
            enableCharging = parseBool(json, "enable_charging", enableCharging);
            enableHyperDL  = parseBool(json, "enable_hyperdl", enableHyperDL);
            enableHyperCore = parseBool(json, "enable_hypercore", enableHyperCore);
            springStiffness = parseFloat(json, "spring_stiffness", springStiffness);
            springDamping   = parseFloat(json, "spring_damping", springDamping);
            autoExpandCharging = parseBool(json, "auto_expand_charging", autoExpandCharging);
            expandTimeoutMs = parseInt(json, "expand_timeout_ms", expandTimeoutMs);

            if (springW != null) {
                springW.setStiffnessAndDamping(springStiffness, springDamping);
                springH.setStiffnessAndDamping(springStiffness, springDamping);
                springR.setStiffnessAndDamping(springStiffness, springDamping);
            }
        } catch (Throwable ignored) {}
    }

    private static int dpToPx(float dp) {
        if (context == null) return Math.round(dp * 2.5f);
        try {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            if (dm != null) return Math.round(dp * dm.density);
        } catch (Throwable ignored) {}
        return Math.round(dp * 2.5f);
    }

    private static int spToPx(float sp) {
        if (context == null) return Math.round(sp * 2.5f);
        try {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            if (dm != null) return Math.round(sp * dm.scaledDensity);
        } catch (Throwable ignored) {}
        return Math.round(sp * 2.5f);
    }

    private static String getJsonRawVal(String json, String key) {
        if (json == null || key == null) return null;
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(?:\"([^\"]*)\"|([^,\\}\\]\\s]+))");
        Matcher m = p.matcher(json);
        if (m.find()) {
            String val = m.group(1) != null ? m.group(1) : m.group(2);
            return val != null ? val.trim() : null;
        }
        return null;
    }

    private static boolean parseBool(String json, String key, boolean defVal) {
        String val = getJsonRawVal(json, key);
        if (val != null) {
            if ("true".equalsIgnoreCase(val) || "1".equals(val)) return true;
            if ("false".equalsIgnoreCase(val) || "0".equals(val)) return false;
        }
        return defVal;
    }

    private static String parseStr(String json, String key, String defVal) {
        String val = getJsonRawVal(json, key);
        if (val != null && !val.isEmpty()) return val;
        return defVal;
    }

    private static float parseFloat(String json, String key, float defVal) {
        String val = getJsonRawVal(json, key);
        if (val != null) {
            try {
                return Float.parseFloat(val.replace(',', '.'));
            } catch (Exception ignored) {}
        }
        return defVal;
    }

    private static int parseInt(String json, String key, int defVal) {
        String val = getJsonRawVal(json, key);
        if (val != null) {
            try {
                return Math.round(Float.parseFloat(val.replace(',', '.')));
            } catch (Exception ignored) {}
        }
        return defVal;
    }
}
