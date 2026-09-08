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
import android.hardware.camera2.CameraManager;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Choreographer;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HyperRingOverlay {

    // Context & display handles
    private static Context context;
    private static Context sysContext;
    private static WindowManager windowManager;
    private static Display defaultDisplay;
    private static RingView ringView;
    private static WindowManager.LayoutParams params;
    private static Handler handler;
    private static ScheduledExecutorService workerPool;
    private static FileObserver stateObserver;

    // Filesystem paths
    private static String stateDir    = "/data/adb/modules/hyperring/state";
    private static String configPath  = stateDir + "/config.json";
    private static String statusPath  = stateDir + "/status.json";
    private static String triggerPath = stateDir + "/trigger.cmd";

    // Display specifications
    private static int displayWidthPx   = 1080;
    private static int displayHeightPx  = 2400;
    private static float displayDensity = 2.75f;

    // Geometry configuration
    private static int cutoutCenterX           = 540;
    private static int cutoutCenterY           = 52;
    private static int cutoutRadius            = 36;
    private static boolean enableMedia         = true;
    private static boolean enableCharging      = true;
    private static boolean enableVolume        = true;
    private static boolean enableRinger        = true;
    private static boolean enableNotifications = true;
    private static boolean enableHyperDL       = true;
    private static boolean enableHyperCore     = true;
    private static boolean stealthRingIdle     = false;
    private static boolean hideInLandscape     = true;
    private static float springStiffness       = 380.0f;
    private static float springDamping         = 0.78f;
    private static boolean autoExpandCharging  = true;
    private static int expandTimeoutMs         = 3500;

    // Island states
    public static final int STATE_IDLE         = 0;
    public static final int STATE_CHARGING     = 1;
    public static final int STATE_MEDIA        = 2;
    public static final int STATE_VOLUME       = 3;
    public static final int STATE_RINGER       = 4;
    public static final int STATE_NOTIFICATION = 5;
    public static final int STATE_HYPERDL      = 6;
    public static final int STATE_TORCH        = 7;
    public static final int STATE_CALIBRATION  = 8;

    private static volatile int currentIsland = STATE_IDLE;
    private static volatile boolean isExpanded = false;
    private static long expandCollapseTime = 0L;
    private static long calibrationEndTime = 0L;
    private static int currentDisplayRotation = Surface.ROTATION_0;

    // Screen power lifecycle
    private static volatile boolean isScreenInteractive = true;

    // Engine loop lifecycle (Zero-overhead sleep when settled)
    private static volatile boolean isLoopRunning = false;

    // Telemetry: Battery & Power
    private static volatile int batteryPct = 100;
    private static volatile boolean isCharging = false;
    private static volatile String chargeWattStr = "33W";
    private static volatile String chargeCurrentStr = "4200mA";
    private static volatile String batteryTempStr = "36.5°C";
    private static volatile String hyperCoreProfile = "Interactive";

    // Telemetry: Media
    private static volatile String mediaTitle = "No active playback";
    private static volatile String mediaArtist = "Media";
    private static volatile boolean isMediaPlaying = false;

    // Telemetry: Volume
    private static volatile int volumePercent = 65;
    
    // Telemetry: Ringer
    private static volatile String ringerLabel = "Ring";
    
    // Telemetry: Notification
    private static volatile String notifAppName = "Notification";
    private static volatile String notifTitle = "";
    private static volatile String notifContent = "";
    
    // Telemetry: HyperDL
    private static volatile boolean isHyperDLActive = false;
    private static volatile String hyperDLSpeed = "0 MB/s";
    private static volatile int hyperDLProgress = 0;
    private static volatile String hyperDLFile = "Download";
    private static volatile long lastStatusPersist = 0L;

    // Fourth-Order Runge-Kutta (RK4) Harmonic Spring Solver
    public static class Spring {
        public float current;
        public float target;
        public float velocity = 0f;
        public float stiffness;
        public float damping;

        public Spring(float initial, float k, float dampingRatio) {
            this.current = initial;
            this.target = initial;
            setParameters(k, dampingRatio);
        }

        public void setParameters(float k, float dampingRatio) {
            this.stiffness = k;
            this.damping = (float) (2.0 * Math.sqrt(k) * dampingRatio);
        }

        public void setTarget(float t) {
            this.target = t;
        }

        public void snapTo(float val) {
            this.current = val;
            this.target = val;
            this.velocity = 0f;
        }

        private float getAcceleration(float pos, float vel) {
            return -stiffness * (pos - target) - damping * vel;
        }

        public boolean update(float dt) {
            float p1 = current;
            float v1 = velocity;
            float a1 = getAcceleration(p1, v1);

            float p2 = p1 + 0.5f * v1 * dt;
            float v2 = v1 + 0.5f * a1 * dt;
            float a2 = getAcceleration(p2, v2);

            float p3 = p1 + 0.5f * v2 * dt;
            float v3 = v1 + 0.5f * a2 * dt;
            float a3 = getAcceleration(p3, v3);

            float p4 = p1 + v3 * dt;
            float v4 = v1 + a3 * dt;
            float a4 = getAcceleration(p4, v4);

            float dpos = (dt / 6.0f) * (v1 + 2f * v2 + 2f * v3 + v4);
            float dvel = (dt / 6.0f) * (a1 + 2f * a2 + 2f * a3 + a4);

            current += dpos;
            velocity += dvel;

            if (Math.abs(velocity) < 0.04f && Math.abs(current - target) < 0.04f) {
                current = target;
                velocity = 0f;
                return false;
            }
            return true;
        }

        public boolean isMoving() {
            return Math.abs(velocity) > 0.04f || Math.abs(current - target) > 0.04f;
        }
    }

    // Geometry Springs
    private static Spring springX;
    private static Spring springY;
    private static Spring springW;
    private static Spring springH;
    private static Spring springR;
    private static Spring springContentAlpha;

    // Visualizer simulation bars
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
    private static Paint paintAccentAmber;
    private static Paint paintIconFill;
    private static Paint paintCalibRing;
    private static Paint paintCalibCross;
    private static Paint paintTrack;
    private static RectF tempRectF;

    // Touch gesture tracking
    private static float touchDownY = 0f;
    private static float touchDownX = 0f;

    // Timing
    private static long lastFrameNanos = 0L;

    public static void main(String[] args) {
        if (args.length > 0 && args[0] != null && !args[0].isEmpty()) {
            stateDir = args[0];
            configPath = stateDir + "/config.json";
            statusPath = stateDir + "/status.json";
            triggerPath = stateDir + "/trigger.cmd";
        }

        System.out.println("HyperRing native daemon initialized (PID " + android.os.Process.myPid() + ")");

        try {
            Looper.prepareMainLooper();
        } catch (Throwable ignored) {}

        handler = new Handler(Looper.getMainLooper());
        workerPool = Executors.newScheduledThreadPool(2);

        initSystemFonts();
        initBinderPool();

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

        grantOverlayPermissions();
        resolveDisplayMetrics();
        readConfig();

        float initD = cutoutRadius * 2.0f;
        float initX = cutoutCenterX - cutoutRadius;
        float initY = cutoutCenterY - cutoutRadius;

        springX = new Spring(initX, springStiffness, springDamping);
        springY = new Spring(initY, springStiffness, springDamping);
        springW = new Spring(initD, springStiffness, springDamping);
        springH = new Spring(initD, springStiffness, springDamping);
        springR = new Spring(cutoutRadius, springStiffness, springDamping);
        springContentAlpha = new Spring(0.0f, springStiffness, springDamping);

        initGraphics();
        registerSystemReceivers();
        startBackgroundWorkers();
        initStateFileObserver();

        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    attachWindow();
                    wakeEngineLoop();
                } catch (Throwable t) {
                    System.err.println("Window mount failed: " + t.getMessage());
                }
            }
        });

        Looper.loop();
    }

    private static void initSystemFonts() {
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
    }

    private static void initBinderPool() {
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
    }

    private static void grantOverlayPermissions() {
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
    }

    private static void resolveDisplayMetrics() {
        try {
            DisplayMetrics realMetrics = new DisplayMetrics();
            if (defaultDisplay != null) {
                defaultDisplay.getRealMetrics(realMetrics);
                displayWidthPx = realMetrics.widthPixels;
                displayHeightPx = realMetrics.heightPixels;
                displayDensity = realMetrics.density;
            }
        } catch (Throwable ignored) {}

        int statusBarH = Math.round(38f * displayDensity);
        try {
            int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                statusBarH = context.getResources().getDimensionPixelSize(resourceId);
            }
        } catch (Throwable ignored) {}

        if (cutoutCenterX <= 0) {
            cutoutCenterX = displayWidthPx / 2;
        }
        if (cutoutCenterY <= 0) {
            cutoutCenterY = Math.max(Math.round(16f * displayDensity), statusBarH / 2);
        }
    }

    private static void initGraphics() {
        paintOledBlack = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintOledBlack.setColor(Color.BLACK);
        paintOledBlack.setStyle(Paint.Style.FILL);

        paintBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBorder.setColor(Color.argb(32, 255, 255, 255));
        paintBorder.setStyle(Paint.Style.STROKE);
        paintBorder.setStrokeWidth(dpToPx(0.75f));

        paintTextPrimary = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTextPrimary.setColor(Color.WHITE);
        try {
            paintTextPrimary.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        } catch (Throwable ignored) {}

        paintTextSecondary = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTextSecondary.setColor(Color.argb(175, 255, 255, 255));
        try {
            paintTextSecondary.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        } catch (Throwable ignored) {}

        paintTextTertiary = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTextTertiary.setColor(Color.argb(110, 255, 255, 255));
        try {
            paintTextTertiary.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        } catch (Throwable ignored) {}

        paintAccentGreen = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAccentGreen.setColor(Color.parseColor("#34D399"));
        paintAccentGreen.setStyle(Paint.Style.FILL);

        paintAccentCyan = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAccentCyan.setColor(Color.parseColor("#38BDF8"));
        paintAccentCyan.setStyle(Paint.Style.FILL);

        paintAccentAmber = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAccentAmber.setColor(Color.parseColor("#F59E0B"));
        paintAccentAmber.setStyle(Paint.Style.FILL);

        paintIconFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintIconFill.setColor(Color.WHITE);
        paintIconFill.setStyle(Paint.Style.FILL);

        paintCalibRing = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintCalibRing.setColor(Color.parseColor("#EF4444"));
        paintCalibRing.setStyle(Paint.Style.STROKE);
        paintCalibRing.setStrokeWidth(dpToPx(2.0f));

        paintCalibCross = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintCalibCross.setColor(Color.parseColor("#F59E0B"));
        paintCalibCross.setStyle(Paint.Style.STROKE);
        paintCalibCross.setStrokeWidth(dpToPx(1.5f));

        paintTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTrack.setColor(Color.argb(32, 255, 255, 255));
        paintTrack.setStyle(Paint.Style.FILL);

        tempRectF = new RectF();
    }

    private static void registerSystemReceivers() {
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
                        readBatteryHardwareTelemetry();
                        triggerChargingEvent();
                    } else if (chargingNow && isCharging) {
                        readBatteryHardwareTelemetry();
                        if (currentIsland == STATE_CHARGING) wakeEngineLoop();
                    } else if (!chargingNow) {
                        isCharging = false;
                        if (currentIsland == STATE_CHARGING) {
                            if (isMediaPlaying && enableMedia) {
                                currentIsland = STATE_MEDIA;
                            } else if (isHyperDLActive && enableHyperDL) {
                                currentIsland = STATE_HYPERDL;
                            } else {
                                currentIsland = STATE_IDLE;
                            }
                            isExpanded = false;
                            wakeEngineLoop();
                        }
                    }
                }
            }, batFilter);
        } catch (Throwable ignored) {}

        // Volume Change Receiver
        try {
            IntentFilter volFilter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent intent) {
                    if (intent == null || !enableVolume) return;
                    int stream = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", AudioManager.STREAM_MUSIC);
                    int val = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1);
                    if (val >= 0) {
                        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                        int max = am != null ? am.getStreamMaxVolume(stream) : 15;
                        volumePercent = Math.round((val / (float) Math.max(1, max)) * 100f);
                        currentIsland = STATE_VOLUME;
                        isExpanded = false;
                        expandCollapseTime = SystemClock.uptimeMillis() + 1800;
                        wakeEngineLoop();
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
                    if (intent == null || !enableRinger) return;
                    int mode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1);
                    if (mode >= 0) {
                        if (mode == AudioManager.RINGER_MODE_SILENT) ringerLabel = "Silent";
                        else if (mode == AudioManager.RINGER_MODE_VIBRATE) ringerLabel = "Vibrate";
                        else ringerLabel = "Ring";

                        currentIsland = STATE_RINGER;
                        isExpanded = false;
                        expandCollapseTime = SystemClock.uptimeMillis() + 2200;
                        wakeEngineLoop();
                    }
                }
            }, ringerFilter);
        } catch (Throwable ignored) {}

        // Torch Mode Callback
        try {
            CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cm != null) {
                cm.registerTorchCallback(new CameraManager.TorchCallback() {
                    @Override
                    public void onTorchModeChanged(String cameraId, boolean enabled) {
                        if (enabled) {
                            currentIsland = STATE_TORCH;
                            isExpanded = false;
                            expandCollapseTime = SystemClock.uptimeMillis() + 2600;
                            wakeEngineLoop();
                        } else if (currentIsland == STATE_TORCH) {
                            currentIsland = STATE_IDLE;
                            wakeEngineLoop();
                        }
                    }
                }, handler);
            }
        } catch (Throwable ignored) {}

        // Screen Power Receiver
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
                        lastFrameNanos = System.nanoTime();
                        wakeEngineLoop();
                    }
                }
            }, screenFilter);
        } catch (Throwable ignored) {}
    }

    private static void triggerChargingEvent() {
        currentIsland = STATE_CHARGING;
        if (autoExpandCharging) {
            isExpanded = true;
            expandCollapseTime = SystemClock.uptimeMillis() + expandTimeoutMs;
        } else {
            isExpanded = false;
            expandCollapseTime = SystemClock.uptimeMillis() + 4500;
        }
        wakeEngineLoop();
    }

    private static void initStateFileObserver() {
        try {
            File dir = new File(stateDir);
            if (!dir.exists()) dir.mkdirs();

            stateObserver = new FileObserver(stateDir, FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO | FileObserver.CREATE) {
                @Override
                public void onEvent(int event, String path) {
                    if (path == null) return;
                    if ("trigger.cmd".equalsIgnoreCase(path)) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                processTriggerCmd();
                            }
                        });
                    } else if ("config.json".equalsIgnoreCase(path)) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                readConfig();
                                wakeEngineLoop();
                            }
                        });
                    }
                }
            };
            stateObserver.startWatching();
        } catch (Throwable ignored) {}
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

        int initDiameter = Math.round(cutoutRadius * 2.0f);

        params = new WindowManager.LayoutParams(
                initDiameter,
                initDiameter,
                type,
                flags,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = cutoutCenterX - cutoutRadius;
        params.y = cutoutCenterY - cutoutRadius;

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
        if (!stealthRingIdle && currentIsland == STATE_IDLE) {
            ringView.setVisibility(View.GONE);
        }
    }

    static class RingView extends View {
        public RingView(Context context) {
            super(context);
            setClickable(true);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (params == null) return false;
            float x = event.getX();
            float y = event.getY();

            float pillRelX = springX.current - params.x;
            float pillRelY = springY.current - params.y;
            float pillW = springW.current;
            float pillH = springH.current;

            boolean insidePill = (x >= pillRelX && x <= pillRelX + pillW && y >= pillRelY && y <= pillRelY + pillH);

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (!insidePill) return false;
                    touchDownX = x;
                    touchDownY = y;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dy = y - touchDownY;
                    if (dy > dpToPx(20) && !isExpanded && currentIsland != STATE_IDLE && currentIsland != STATE_CALIBRATION) {
                        isExpanded = true;
                        expandCollapseTime = 0L;
                        wakeEngineLoop();
                        return true;
                    } else if (dy < -dpToPx(20) && isExpanded) {
                        isExpanded = false;
                        wakeEngineLoop();
                        return true;
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!insidePill) return false;
                    float totalDistX = Math.abs(x - touchDownX);
                    float totalDistY = Math.abs(y - touchDownY);
                    if (totalDistX < dpToPx(14) && totalDistY < dpToPx(14)) {
                        handleTap(x - pillRelX, y - pillRelY);
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
            if (params == null) return;
            float pillRelX = springX.current - params.x;
            float pillRelY = springY.current - params.y;
            float pillW = springW.current;
            float pillH = springH.current;

            canvas.save();
            canvas.translate(pillRelX, pillRelY);
            renderIsland(canvas, pillW, pillH);
            canvas.restore();
        }
    }

    private static void handleTap(float x, float y) {
        if (!isExpanded) {
            if (currentIsland != STATE_IDLE && currentIsland != STATE_CALIBRATION) {
                isExpanded = true;
                expandCollapseTime = 0L;
                wakeEngineLoop();
            }
        } else {
            float viewW = springW.current;
            float viewH = springH.current;

            if (currentIsland == STATE_MEDIA) {
                float btnY = viewH - dpToPx(22);
                float centerX = viewW / 2.0f;
                float prevBtnX = centerX - dpToPx(65);
                float nextBtnX = centerX + dpToPx(65);
                float hitRadius = dpToPx(26);

                if (Math.abs(y - btnY) < hitRadius) {
                    if (Math.abs(x - centerX) < hitRadius) {
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
            } else if (currentIsland == STATE_TORCH) {
                toggleTorchNative();
                return;
            }
            isExpanded = false;
            wakeEngineLoop();
        }
    }

    private static void renderIsland(Canvas canvas, float w, float h) {
        if (w <= 0 || h <= 0) return;

        float curR = springR.current;
        tempRectF.set(0, 0, w, h);

        // OLED Black Surface
        canvas.drawRoundRect(tempRectF, curR, curR, paintOledBlack);

        // Specular ambient boundary
        if (currentIsland != STATE_IDLE || isExpanded || currentIsland == STATE_CALIBRATION) {
            canvas.drawRoundRect(tempRectF, curR, curR, paintBorder);
        }

        // Calibration Reticle Overlay
        if (currentIsland == STATE_CALIBRATION) {
            float holeRelX = cutoutCenterX - springX.current;
            float holeRelY = cutoutCenterY - springY.current;
            canvas.drawCircle(holeRelX, holeRelY, cutoutRadius, paintCalibRing);
            canvas.drawLine(holeRelX - dpToPx(16), holeRelY, holeRelX + dpToPx(16), holeRelY, paintCalibCross);
            canvas.drawLine(holeRelX, holeRelY - dpToPx(16), holeRelX, holeRelY + dpToPx(16), paintCalibCross);
            return;
        }

        float contentAlpha = springContentAlpha.current;
        if (contentAlpha > 0.04f) {
            canvas.save();
            Path clipP = new Path();
            clipP.addRoundRect(tempRectF, curR, curR, Path.Direction.CW);
            canvas.clipPath(clipP);

            if (isExpanded) {
                renderExpandedContent(canvas, w, h, contentAlpha);
            } else {
                renderCompactContent(canvas, w, h, contentAlpha);
            }

            canvas.restore();
        }
    }

    private static void renderCompactContent(Canvas canvas, float curW, float curH, float alpha) {
        float centerY = curH / 2.0f;
        int intAlpha = Math.min(255, Math.max(0, (int) (alpha * 255)));

        float holeRelX = cutoutCenterX - springX.current;
        float holeR = cutoutRadius;
        boolean isCutoutCenter = Math.abs(cutoutCenterX - (displayWidthPx / 2.0f)) < (displayWidthPx * 0.15f);
        boolean isCutoutLeft = cutoutCenterX < displayWidthPx * 0.35f;

        float iconCenterX;
        float textCenterX;
        Paint.Align textAlign;

        if (isCutoutLeft) {
            iconCenterX = holeRelX + holeR + dpToPx(14);
            textCenterX = curW - dpToPx(18);
            textAlign = Paint.Align.RIGHT;
        } else if (!isCutoutCenter) {
            iconCenterX = dpToPx(18);
            textCenterX = holeRelX - holeR - dpToPx(14);
            textAlign = Paint.Align.RIGHT;
        } else {
            iconCenterX = Math.max(dpToPx(14), (holeRelX - holeR) / 2.0f);
            textCenterX = (holeRelX + holeR) + (curW - (holeRelX + holeR)) / 2.0f;
            textAlign = Paint.Align.CENTER;
        }

        if (currentIsland == STATE_CHARGING) {
            paintAccentGreen.setAlpha(intAlpha);
            drawBoltIcon(canvas, iconCenterX, centerY, dpToPx(12), paintAccentGreen);

            paintTextPrimary.setTextSize(spToPx(12.5f));
            paintTextPrimary.setTextAlign(textAlign);
            paintTextPrimary.setColor(Color.parseColor("#34D399"));
            paintTextPrimary.setAlpha(intAlpha);
            canvas.drawText(batteryPct + "%", textCenterX, centerY + dpToPx(4.5f), paintTextPrimary);
            paintTextPrimary.setColor(Color.WHITE);

        } else if (currentIsland == STATE_MEDIA) {
            paintAccentCyan.setAlpha(intAlpha);
            canvas.drawCircle(iconCenterX, centerY, dpToPx(5), paintAccentCyan);

            float barsStart = isCutoutCenter ? (textCenterX - dpToPx(10)) : (textCenterX - dpToPx(20));
            renderAudioBars(canvas, barsStart, centerY, alpha);

        } else if (currentIsland == STATE_VOLUME) {
            paintAccentCyan.setAlpha(intAlpha);
            drawSpeakerIcon(canvas, iconCenterX, centerY, dpToPx(12), volumePercent, paintAccentCyan);

            paintTextPrimary.setTextSize(spToPx(11.5f));
            paintTextPrimary.setTextAlign(textAlign);
            paintTextPrimary.setAlpha(intAlpha);
            canvas.drawText(volumePercent + "%", textCenterX, centerY + dpToPx(4), paintTextPrimary);

        } else if (currentIsland == STATE_RINGER) {
            paintAccentAmber.setAlpha(intAlpha);
            drawBellIcon(canvas, iconCenterX, centerY, dpToPx(12), ringerLabel, paintAccentAmber);

            paintTextPrimary.setTextSize(spToPx(11.5f));
            paintTextPrimary.setTextAlign(textAlign);
            paintTextPrimary.setAlpha(intAlpha);
            canvas.drawText(ringerLabel, textCenterX, centerY + dpToPx(4), paintTextPrimary);

        } else if (currentIsland == STATE_NOTIFICATION) {
            paintAccentAmber.setAlpha(intAlpha);
            drawMessageIcon(canvas, iconCenterX, centerY, dpToPx(11), paintAccentAmber);

            paintTextPrimary.setTextSize(spToPx(11f));
            paintTextPrimary.setTextAlign(textAlign);
            paintTextPrimary.setAlpha(intAlpha);
            canvas.drawText(truncate(notifAppName, 10), textCenterX, centerY + dpToPx(4), paintTextPrimary);

        } else if (currentIsland == STATE_TORCH) {
            paintAccentAmber.setAlpha(intAlpha);
            drawTorchIcon(canvas, iconCenterX, centerY, dpToPx(11), paintAccentAmber);

            paintTextPrimary.setTextSize(spToPx(11.5f));
            paintTextPrimary.setTextAlign(textAlign);
            paintTextPrimary.setAlpha(intAlpha);
            canvas.drawText("Torch", textCenterX, centerY + dpToPx(4), paintTextPrimary);

        } else if (currentIsland == STATE_HYPERDL) {
            paintAccentCyan.setAlpha(intAlpha);
            drawDownloadIcon(canvas, iconCenterX, centerY, dpToPx(10), paintAccentCyan);

            paintTextPrimary.setTextSize(spToPx(11));
            paintTextPrimary.setTextAlign(textAlign);
            paintTextPrimary.setAlpha(intAlpha);
            canvas.drawText(hyperDLSpeed, textCenterX, centerY + dpToPx(4), paintTextPrimary);
        }
    }

    private static void renderAudioBars(Canvas canvas, float startX, float centerY, float alpha) {
        paintAccentCyan.setAlpha(Math.min(255, Math.max(0, (int) (alpha * 255))));
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

    private static void renderExpandedContent(Canvas canvas, float curW, float curH, float alpha) {
        int intAlpha = Math.min(255, Math.max(0, (int) (alpha * 255)));
        paintTextPrimary.setAlpha(intAlpha);
        paintTextSecondary.setAlpha(Math.min(255, (int) (alpha * 175)));
        paintTextTertiary.setAlpha(Math.min(255, (int) (alpha * 95)));
        paintIconFill.setAlpha(intAlpha);

        float holeRelY = cutoutCenterY - springY.current;
        float holeR = cutoutRadius;
        boolean isCutoutCenter = Math.abs(cutoutCenterX - (displayWidthPx / 2.0f)) < (displayWidthPx * 0.15f);

        if (currentIsland == STATE_CHARGING) {
            // Elegant Native Layout: Safely clear of camera punch-hole
            float topY;
            if (isCutoutCenter) {
                // Sits with clean breathing room below the punch-hole
                topY = Math.max(dpToPx(38), holeRelY + holeR + dpToPx(10));
            } else {
                topY = dpToPx(24);
            }

            // Row 1: Left header status, Right big percentage
            paintAccentGreen.setAlpha(intAlpha);
            drawBoltIcon(canvas, dpToPx(22), topY, dpToPx(13), paintAccentGreen);

            paintTextPrimary.setTextSize(spToPx(13.5f));
            paintTextPrimary.setTextAlign(Paint.Align.LEFT);
            String title = (batteryPct >= 100) ? "Fully charged" : "Fast charging";
            canvas.drawText(title, dpToPx(38), topY + dpToPx(4.5f), paintTextPrimary);

            paintTextPrimary.setTextSize(spToPx(18f));
            paintTextPrimary.setColor(Color.parseColor("#34D399"));
            paintTextPrimary.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(batteryPct + "%", curW - dpToPx(20), topY + dpToPx(5.5f), paintTextPrimary);
            paintTextPrimary.setColor(Color.WHITE);

            // Row 2: Fluid horizontal charging level progress track
            float barY = topY + dpToPx(22);
            float barW = curW - dpToPx(40);
            paintTrack.setAlpha(Math.min(255, (int) (alpha * 35)));
            canvas.drawRoundRect(new RectF(dpToPx(20), barY, dpToPx(20) + barW, barY + dpToPx(6)), dpToPx(3), dpToPx(3), paintTrack);

            paintAccentGreen.setAlpha(intAlpha);
            float fillRatio = Math.max(0.04f, Math.min(1.0f, batteryPct / 100f));
            float fillW = barW * fillRatio;
            canvas.drawRoundRect(new RectF(dpToPx(20), barY, dpToPx(20) + fillW, barY + dpToPx(6)), dpToPx(3), dpToPx(3), paintAccentGreen);

            // Row 3: Power telemetry and HyperCore profile
            float bottomY = barY + dpToPx(24);
            paintTextSecondary.setTextSize(spToPx(11.5f));
            paintTextSecondary.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(chargeWattStr + " · " + chargeCurrentStr, dpToPx(20), bottomY, paintTextSecondary);

            paintTextTertiary.setTextSize(spToPx(11f));
            paintTextTertiary.setTextAlign(Paint.Align.RIGHT);
            String rightSub = (!batteryTempStr.isEmpty() ? batteryTempStr + " · " : "") + hyperCoreProfile;
            canvas.drawText(rightSub, curW - dpToPx(20), bottomY, paintTextTertiary);

        } else if (currentIsland == STATE_MEDIA) {
            float artSize = dpToPx(48);
            float artLeft = dpToPx(18);
            float artTop = isCutoutCenter ? Math.max(dpToPx(18), holeRelY + holeR + dpToPx(4)) : dpToPx(18);

            // Album art disc
            paintAccentCyan.setAlpha(Math.min(255, (int) (alpha * 38)));
            canvas.drawRoundRect(new RectF(artLeft, artTop, artLeft + artSize, artTop + artSize), dpToPx(12), dpToPx(12), paintAccentCyan);

            paintAccentCyan.setAlpha(intAlpha);
            canvas.drawCircle(artLeft + artSize / 2f, artTop + artSize / 2f, dpToPx(10), paintAccentCyan);
            paintOledBlack.setAlpha(intAlpha);
            canvas.drawCircle(artLeft + artSize / 2f, artTop + artSize / 2f, dpToPx(4), paintOledBlack);

            // Track & Artist text
            float textLeft = artLeft + artSize + dpToPx(14);
            paintTextPrimary.setTextSize(spToPx(14));
            paintTextPrimary.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(truncate(mediaTitle, 20), textLeft, artTop + dpToPx(20), paintTextPrimary);

            paintTextSecondary.setTextSize(spToPx(12));
            canvas.drawText(truncate(mediaArtist.isEmpty() ? "Media Playback" : mediaArtist, 22), textLeft, artTop + dpToPx(38), paintTextSecondary);

            // Progress track
            float barY = artTop + artSize + dpToPx(14);
            float barW = curW - dpToPx(36);
            paintTrack.setAlpha(Math.min(255, (int) (alpha * 40)));
            canvas.drawRoundRect(new RectF(dpToPx(18), barY, dpToPx(18) + barW, barY + dpToPx(2.5f)), dpToPx(2), dpToPx(2), paintTrack);

            paintAccentCyan.setAlpha(intAlpha);
            canvas.drawRoundRect(new RectF(dpToPx(18), barY, dpToPx(18) + (barW * 0.42f), barY + dpToPx(2.5f)), dpToPx(2), dpToPx(2), paintAccentCyan);

            // Playback controls
            float btnY = curH - dpToPx(20);
            float centerX = curW / 2.0f;
            float prevBtnX = centerX - dpToPx(65);
            float nextBtnX = centerX + dpToPx(65);

            drawPrevIcon(canvas, prevBtnX, btnY, dpToPx(13), paintIconFill);
            if (isMediaPlaying) {
                drawPauseIcon(canvas, centerX, btnY, dpToPx(13), paintIconFill);
            } else {
                drawPlayIcon(canvas, centerX, btnY, dpToPx(13), paintIconFill);
            }
            drawNextIcon(canvas, nextBtnX, btnY, dpToPx(13), paintIconFill);

        } else if (currentIsland == STATE_VOLUME) {
            float topY = isCutoutCenter ? Math.max(dpToPx(22), holeRelY + holeR + dpToPx(6)) : dpToPx(22);

            paintAccentCyan.setAlpha(intAlpha);
            drawSpeakerIcon(canvas, dpToPx(24), topY, dpToPx(14), volumePercent, paintAccentCyan);

            paintTextPrimary.setTextSize(spToPx(14));
            paintTextPrimary.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("Media Volume", dpToPx(44), topY + dpToPx(5), paintTextPrimary);

            paintTextPrimary.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(volumePercent + "%", curW - dpToPx(20), topY + dpToPx(5), paintTextPrimary);

            float barY = topY + dpToPx(22);
            float barW = curW - dpToPx(40);
            paintTrack.setAlpha(Math.min(255, (int) (alpha * 35)));
            canvas.drawRoundRect(new RectF(dpToPx(20), barY, dpToPx(20) + barW, barY + dpToPx(8)), dpToPx(4), dpToPx(4), paintTrack);

            paintAccentCyan.setAlpha(intAlpha);
            float fillRatio = Math.max(0.04f, Math.min(1.0f, volumePercent / 100f));
            canvas.drawRoundRect(new RectF(dpToPx(20), barY, dpToPx(20) + (barW * fillRatio), barY + dpToPx(8)), dpToPx(4), dpToPx(4), paintAccentCyan);

            float bottomY = barY + dpToPx(22);
            paintTextTertiary.setTextSize(spToPx(11f));
            paintTextTertiary.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("Speaker output", dpToPx(20), bottomY, paintTextTertiary);

            paintTextTertiary.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(volumePercent == 0 ? "Muted" : "Active", curW - dpToPx(20), bottomY, paintTextTertiary);

        } else if (currentIsland == STATE_RINGER) {
            float topY = isCutoutCenter ? Math.max(dpToPx(22), holeRelY + holeR + dpToPx(6)) : dpToPx(22);

            paintAccentAmber.setAlpha(intAlpha);
            drawBellIcon(canvas, dpToPx(24), topY, dpToPx(14), ringerLabel, paintAccentAmber);

            paintTextPrimary.setTextSize(spToPx(14));
            paintTextPrimary.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("Ringer Profile", dpToPx(44), topY + dpToPx(5), paintTextPrimary);

            paintTextPrimary.setTextAlign(Paint.Align.RIGHT);
            paintTextPrimary.setColor(Color.parseColor("#FBBF24"));
            canvas.drawText(ringerLabel, curW - dpToPx(20), topY + dpToPx(5), paintTextPrimary);
            paintTextPrimary.setColor(Color.WHITE);

            float descY = topY + dpToPx(26);
            paintTextSecondary.setTextSize(spToPx(12f));
            paintTextSecondary.setTextAlign(Paint.Align.LEFT);
            String desc = "Calls and notifications audible";
            if ("Silent".equalsIgnoreCase(ringerLabel)) desc = "Alarms and media only · Calls muted";
            else if ("Vibrate".equalsIgnoreCase(ringerLabel)) desc = "Calls and alerts will vibrate";
            canvas.drawText(desc, dpToPx(20), descY, paintTextSecondary);

        } else if (currentIsland == STATE_NOTIFICATION) {
            float topY = isCutoutCenter ? Math.max(dpToPx(22), holeRelY + holeR + dpToPx(6)) : dpToPx(22);

            paintAccentAmber.setAlpha(intAlpha);
            drawMessageIcon(canvas, dpToPx(24), topY, dpToPx(13), paintAccentAmber);

            paintTextPrimary.setTextSize(spToPx(13.5f));
            paintTextPrimary.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(notifAppName, dpToPx(44), topY + dpToPx(4), paintTextPrimary);

            paintTextTertiary.setTextSize(spToPx(11f));
            paintTextTertiary.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("Just now", curW - dpToPx(20), topY + dpToPx(4), paintTextTertiary);

            float titleY = topY + dpToPx(24);
            paintTextPrimary.setTextSize(spToPx(13f));
            paintTextPrimary.setTextAlign(Paint.Align.LEFT);
            String displayTitle = (notifTitle != null && !notifTitle.isEmpty()) ? notifTitle : notifAppName;
            canvas.drawText(truncate(displayTitle, 30), dpToPx(20), titleY, paintTextPrimary);

            float contentY = titleY + dpToPx(20);
            paintTextSecondary.setTextSize(spToPx(11.5f));
            String displayContent = (notifContent != null && !notifContent.isEmpty()) ? notifContent : "New message received";
            canvas.drawText(truncate(displayContent, 36), dpToPx(20), contentY, paintTextSecondary);

        } else if (currentIsland == STATE_TORCH) {
            float topY = isCutoutCenter ? Math.max(dpToPx(24), holeRelY + holeR + dpToPx(6)) : dpToPx(24);

            paintAccentAmber.setAlpha(intAlpha);
            drawTorchIcon(canvas, dpToPx(24), topY, dpToPx(14), paintAccentAmber);

            paintTextPrimary.setTextSize(spToPx(14));
            paintTextPrimary.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("Flashlight", dpToPx(44), topY + dpToPx(5), paintTextPrimary);

            paintTextPrimary.setTextAlign(Paint.Align.RIGHT);
            paintTextPrimary.setColor(Color.parseColor("#FBBF24"));
            canvas.drawText("Active", curW - dpToPx(20), topY + dpToPx(5), paintTextPrimary);
            paintTextPrimary.setColor(Color.WHITE);

            float descY = topY + dpToPx(26);
            paintTextSecondary.setTextSize(spToPx(12f));
            paintTextSecondary.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("Rear LED illuminated · Tap to toggle", dpToPx(20), descY, paintTextSecondary);

        } else if (currentIsland == STATE_HYPERDL) {
            float topY = isCutoutCenter ? Math.max(dpToPx(22), holeRelY + holeR + dpToPx(6)) : dpToPx(22);

            paintAccentCyan.setAlpha(intAlpha);
            drawDownloadIcon(canvas, dpToPx(24), topY, dpToPx(12), paintAccentCyan);

            paintTextPrimary.setTextSize(spToPx(13.5f));
            paintTextPrimary.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("HyperDL Engine", dpToPx(44), topY + dpToPx(5), paintTextPrimary);

            paintTextPrimary.setTextAlign(Paint.Align.RIGHT);
            paintTextPrimary.setColor(Color.parseColor("#38BDF8"));
            canvas.drawText(hyperDLSpeed, curW - dpToPx(20), topY + dpToPx(5), paintTextPrimary);
            paintTextPrimary.setColor(Color.WHITE);

            float fileY = topY + dpToPx(22);
            paintTextSecondary.setTextSize(spToPx(11.5f));
            paintTextSecondary.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(truncate(hyperDLFile, 30), dpToPx(20), fileY, paintTextSecondary);

            float barY = fileY + dpToPx(16);
            float barW = curW - dpToPx(40);
            paintTrack.setAlpha(Math.min(255, (int) (alpha * 40)));
            canvas.drawRoundRect(new RectF(dpToPx(20), barY, dpToPx(20) + barW, barY + dpToPx(4)), dpToPx(2), dpToPx(2), paintTrack);

            paintAccentCyan.setAlpha(intAlpha);
            float fillRatio = Math.max(0.04f, Math.min(1.0f, hyperDLProgress / 100f));
            canvas.drawRoundRect(new RectF(dpToPx(20), barY, dpToPx(20) + (barW * fillRatio), barY + dpToPx(4)), dpToPx(2), dpToPx(2), paintAccentCyan);
        }
    }

    // Vector Iconography
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

    private static void drawSpeakerIcon(Canvas canvas, float cx, float cy, float size, int pct, Paint paint) {
        Path p = new Path();
        p.moveTo(cx - size * 0.4f, cy - size * 0.2f);
        p.lineTo(cx - size * 0.15f, cy - size * 0.2f);
        p.lineTo(cx + size * 0.2f, cy - size * 0.45f);
        p.lineTo(cx + size * 0.2f, cy + size * 0.45f);
        p.lineTo(cx - size * 0.15f, cy + size * 0.2f);
        p.lineTo(cx - size * 0.4f, cy + size * 0.2f);
        p.close();
        canvas.drawPath(p, paint);

        if (pct > 0) {
            Paint arcP = new Paint(paint);
            arcP.setStyle(Paint.Style.STROKE);
            arcP.setStrokeWidth(dpToPx(1.2f));
            canvas.drawArc(new RectF(cx, cy - size * 0.3f, cx + size * 0.5f, cy + size * 0.3f), -50, 100, false, arcP);
            if (pct > 50) {
                canvas.drawArc(new RectF(cx + size * 0.15f, cy - size * 0.45f, cx + size * 0.8f, cy + size * 0.45f), -50, 100, false, arcP);
            }
        }
    }

    private static void drawBellIcon(Canvas canvas, float cx, float cy, float size, String mode, Paint paint) {
        Path p = new Path();
        p.moveTo(cx, cy - size * 0.45f);
        p.quadTo(cx + size * 0.35f, cy - size * 0.4f, cx + size * 0.35f, cy + size * 0.2f);
        p.lineTo(cx + size * 0.45f, cy + size * 0.35f);
        p.lineTo(cx - size * 0.45f, cy + size * 0.35f);
        p.lineTo(cx - size * 0.35f, cy + size * 0.2f);
        p.quadTo(cx - size * 0.35f, cy - size * 0.4f, cx, cy - size * 0.45f);
        p.close();
        canvas.drawPath(p, paint);
        canvas.drawCircle(cx, cy + size * 0.45f, size * 0.1f, paint);

        if ("Silent".equalsIgnoreCase(mode)) {
            Paint slashP = new Paint(paint);
            slashP.setStyle(Paint.Style.STROKE);
            slashP.setStrokeWidth(dpToPx(1.4f));
            canvas.drawLine(cx - size * 0.45f, cy + size * 0.45f, cx + size * 0.45f, cy - size * 0.45f, slashP);
        } else if ("Vibrate".equalsIgnoreCase(mode)) {
            Paint arcP = new Paint(paint);
            arcP.setStyle(Paint.Style.STROKE);
            arcP.setStrokeWidth(dpToPx(1.1f));
            canvas.drawArc(new RectF(cx - size * 0.7f, cy - size * 0.3f, cx - size * 0.3f, cy + size * 0.3f), 135, 90, false, arcP);
            canvas.drawArc(new RectF(cx + size * 0.3f, cy - size * 0.3f, cx + size * 0.7f, cy + size * 0.3f), -45, 90, false, arcP);
        }
    }

    private static void drawMessageIcon(Canvas canvas, float cx, float cy, float size, Paint paint) {
        Path p = new Path();
        float w = size * 0.85f;
        float h = size * 0.65f;
        float l = cx - w / 2f;
        float t = cy - h / 2f - size * 0.05f;
        float r = l + w;
        float b = t + h;
        float cr = dpToPx(2.5f);

        p.moveTo(l + cr, t);
        p.lineTo(r - cr, t);
        p.quadTo(r, t, r, t + cr);
        p.lineTo(r, b - cr);
        p.quadTo(r, b, r - cr, b);
        p.lineTo(l + w * 0.45f, b);
        p.lineTo(l + w * 0.2f, b + size * 0.2f);
        p.lineTo(l + w * 0.25f, b);
        p.lineTo(l + cr, b);
        p.quadTo(l, b, l, b - cr);
        p.lineTo(l, t + cr);
        p.quadTo(l, t, l + cr, t);
        p.close();
        canvas.drawPath(p, paint);
    }

    private static void drawTorchIcon(Canvas canvas, float cx, float cy, float size, Paint paint) {
        Path p = new Path();
        p.moveTo(cx - size * 0.35f, cy - size * 0.45f);
        p.lineTo(cx + size * 0.35f, cy - size * 0.45f);
        p.lineTo(cx + size * 0.2f, cy - size * 0.1f);
        p.lineTo(cx + size * 0.15f, cy + size * 0.45f);
        p.lineTo(cx - size * 0.15f, cy + size * 0.45f);
        p.lineTo(cx - size * 0.2f, cy - size * 0.1f);
        p.close();
        canvas.drawPath(p, paint);
    }

    private static void drawDownloadIcon(Canvas canvas, float cx, float cy, float size, Paint paint) {
        Path p = new Path();
        p.moveTo(cx, cy + size * 0.35f);
        p.lineTo(cx - size * 0.32f, cy + size * 0.03f);
        p.lineTo(cx - size * 0.12f, cy + size * 0.03f);
        p.lineTo(cx - size * 0.12f, cy - size * 0.45f);
        p.lineTo(cx + size * 0.12f, cy - size * 0.45f);
        p.lineTo(cx + size * 0.12f, cy + size * 0.03f);
        p.lineTo(cx + size * 0.32f, cy + size * 0.03f);
        p.close();
        canvas.drawPath(p, paint);

        tempRectF.set(cx - size * 0.38f, cy + size * 0.42f, cx + size * 0.38f, cy + size * 0.52f);
        canvas.drawRoundRect(tempRectF, dpToPx(1), dpToPx(1), paint);
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

    // Hardware VSYNC-Paced Choreographer Frame Loop (Awake only when animating)
    private static final Choreographer.FrameCallback vsyncCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!isScreenInteractive) {
                isLoopRunning = false;
                return;
            }

            float dt = (frameTimeNanos - lastFrameNanos) * 1e-9f;
            lastFrameNanos = frameTimeNanos;
            if (dt <= 0f || dt > 0.04f) dt = 0.016f;

            long now = SystemClock.uptimeMillis();

            checkDisplayOrientation();
            resolveTargetState(now);

            boolean sxMoving = springX.update(dt);
            boolean syMoving = springY.update(dt);
            boolean swMoving = springW.update(dt);
            boolean shMoving = springH.update(dt);
            boolean srMoving = springR.update(dt);
            boolean saMoving = springContentAlpha.update(dt);

            boolean anyMoving = sxMoving || syMoving || swMoving || shMoving || srMoving || saMoving;

            if (currentIsland == STATE_MEDIA && isMediaPlaying && (now - lastWaveStep > 85)) {
                stepAudioBars();
                lastWaveStep = now;
            }

            if (ringView != null) ringView.invalidate();

            if (now - lastStatusPersist > 2500) {
                persistStatusAsync();
                lastStatusPersist = now;
            }

            // Continue loop only if springs are still in motion, timeouts are active, or media is playing
            boolean needNextFrame = anyMoving 
                    || (currentIsland == STATE_MEDIA && isMediaPlaying) 
                    || (expandCollapseTime > 0) 
                    || (currentIsland == STATE_CALIBRATION);

            if (needNextFrame) {
                Choreographer.getInstance().postFrameCallback(this);
            } else {
                isLoopRunning = false;
                onAnimationSettled();
            }
        }
    };

    public static void wakeEngineLoop() {
        if (handler == null) return;
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!isLoopRunning && isScreenInteractive) {
                    isLoopRunning = true;
                    lastFrameNanos = System.nanoTime();
                    // Before animation starts, ensure window params accommodate the target state
                    prepareWindowForTarget();
                    Choreographer.getInstance().postFrameCallback(vsyncCallback);
                }
            }
        });
    }

    private static void checkDisplayOrientation() {
        if (defaultDisplay == null || ringView == null) return;
        int rot = defaultDisplay.getRotation();
        if (rot != currentDisplayRotation) {
            currentDisplayRotation = rot;
            if (hideInLandscape && currentDisplayRotation != Surface.ROTATION_0) {
                ringView.setVisibility(View.GONE);
            } else {
                if (currentIsland != STATE_IDLE || stealthRingIdle) {
                    ringView.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    private static void resolveTargetState(long now) {
        if (currentIsland == STATE_CALIBRATION && now > calibrationEndTime) {
            currentIsland = STATE_IDLE;
        }

        if (expandCollapseTime > 0 && now > expandCollapseTime) {
            if (isExpanded) {
                isExpanded = false;
                expandCollapseTime = now + 3500;
            } else if (currentIsland == STATE_CHARGING || currentIsland == STATE_VOLUME
                    || currentIsland == STATE_RINGER || currentIsland == STATE_NOTIFICATION || currentIsland == STATE_TORCH) {
                if (isMediaPlaying && enableMedia) {
                    currentIsland = STATE_MEDIA;
                } else if (isHyperDLActive && enableHyperDL) {
                    currentIsland = STATE_HYPERDL;
                } else {
                    currentIsland = STATE_IDLE;
                }
                expandCollapseTime = 0L;
            }
        }

        float targetW;
        float targetH;
        float targetR;
        float targetX;
        float targetY;
        float targetContentAlpha;

        if (currentIsland == STATE_IDLE) {
            targetW = cutoutRadius * 2.0f;
            targetH = cutoutRadius * 2.0f;
            targetR = cutoutRadius;
            targetX = cutoutCenterX - cutoutRadius;
            targetY = cutoutCenterY - cutoutRadius;
            targetContentAlpha = 0.0f;

            if (!stealthRingIdle && springContentAlpha.current < 0.02f && Math.abs(springW.current - targetW) < 1f) {
                if (ringView != null && ringView.getVisibility() != View.GONE) {
                    ringView.setVisibility(View.GONE);
                }
            }
        } else if (currentIsland == STATE_CALIBRATION) {
            if (ringView != null && ringView.getVisibility() != View.VISIBLE) {
                ringView.setVisibility(View.VISIBLE);
            }
            targetW = Math.max(cutoutRadius * 2.6f, dpToPx(72));
            targetH = targetW;
            targetR = targetW / 2.0f;
            targetX = cutoutCenterX - (targetW / 2.0f);
            targetY = cutoutCenterY - (targetH / 2.0f);
            targetContentAlpha = 1.0f;

        } else if (!isExpanded) {
            // Compact pill
            if (ringView != null && ringView.getVisibility() != View.VISIBLE) {
                ringView.setVisibility(View.VISIBLE);
            }

            targetH = Math.max(cutoutRadius * 2.0f, dpToPx(34));
            targetR = targetH / 2.0f;
            targetY = cutoutCenterY - (targetH / 2.0f);
            targetContentAlpha = 1.0f;

            if (currentIsland == STATE_CHARGING) {
                targetW = dpToPx(138);
            } else if (currentIsland == STATE_MEDIA) {
                targetW = dpToPx(148);
            } else if (currentIsland == STATE_VOLUME) {
                targetW = dpToPx(144);
            } else if (currentIsland == STATE_RINGER) {
                targetW = dpToPx(136);
            } else if (currentIsland == STATE_NOTIFICATION) {
                targetW = dpToPx(150);
            } else if (currentIsland == STATE_TORCH) {
                targetW = dpToPx(132);
            } else {
                targetW = dpToPx(142);
            }

            // Horizontal alignment with camera cutout
            if (Math.abs(cutoutCenterX - (displayWidthPx / 2.0f)) < (displayWidthPx * 0.15f)) {
                targetX = cutoutCenterX - (targetW / 2.0f);
            } else if (cutoutCenterX < displayWidthPx * 0.35f) {
                targetX = Math.max(dpToPx(8), cutoutCenterX - targetH / 2.0f);
            } else {
                targetX = Math.min(displayWidthPx - targetW - dpToPx(8), cutoutCenterX + targetH / 2.0f - targetW);
            }

            targetX = Math.max(dpToPx(6), Math.min(displayWidthPx - targetW - dpToPx(6), targetX));

        } else {
            // Expanded card
            if (ringView != null && ringView.getVisibility() != View.VISIBLE) {
                ringView.setVisibility(View.VISIBLE);
            }

            targetW = Math.min(displayWidthPx - dpToPx(24), dpToPx(320));
            targetR = dpToPx(24);
            targetContentAlpha = 1.0f;

            if (currentIsland == STATE_MEDIA) {
                targetH = dpToPx(126);
            } else if (currentIsland == STATE_CHARGING) {
                targetH = dpToPx(112);
            } else if (currentIsland == STATE_VOLUME) {
                targetH = dpToPx(88);
            } else {
                targetH = dpToPx(96);
            }

            targetY = Math.max(dpToPx(4), Math.min(displayHeightPx - targetH - dpToPx(12), cutoutCenterY - cutoutRadius - dpToPx(2)));

            if (Math.abs(cutoutCenterX - (displayWidthPx / 2.0f)) < (displayWidthPx * 0.15f)) {
                targetX = cutoutCenterX - (targetW / 2.0f);
            } else if (cutoutCenterX < displayWidthPx * 0.35f) {
                targetX = Math.max(dpToPx(12), cutoutCenterX - cutoutRadius - dpToPx(4));
            } else {
                targetX = Math.min(displayWidthPx - targetW - dpToPx(12), cutoutCenterX + cutoutRadius + dpToPx(4) - targetW);
            }

            targetX = Math.max(dpToPx(8), Math.min(displayWidthPx - targetW - dpToPx(8), targetX));
        }

        springX.setTarget(targetX);
        springY.setTarget(targetY);
        springW.setTarget(targetW);
        springH.setTarget(targetH);
        springR.setTarget(targetR);
        springContentAlpha.setTarget(targetContentAlpha);
    }

    /**
     * Pre-allocates window bounds to contain upcoming animations.
     * Called ONCE before animation starts, avoiding mid-animation window resizing.
     */
    private static void prepareWindowForTarget() {
        if (params == null || windowManager == null || ringView == null) return;

        if (currentIsland == STATE_IDLE) {
            int targetFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

            int d = Math.round(cutoutRadius * 2.0f);
            int x = cutoutCenterX - cutoutRadius;
            int y = cutoutCenterY - cutoutRadius;

            if (params.width != d || params.height != d || params.x != x || params.y != y || params.flags != targetFlags) {
                params.width = d;
                params.height = d;
                params.x = x;
                params.y = y;
                params.flags = targetFlags;
                try { windowManager.updateViewLayout(ringView, params); } catch (Exception ignored) {}
            }
            return;
        }

        int targetFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;

        int reqW;
        int reqH;
        int reqX;
        int reqY;

        if (isExpanded) {
            reqW = Math.min(displayWidthPx - dpToPx(24), dpToPx(320));
            reqH = (currentIsland == STATE_MEDIA) ? dpToPx(126) : (currentIsland == STATE_CHARGING ? dpToPx(112) : dpToPx(96));
            reqY = Math.max(dpToPx(4), cutoutCenterY - cutoutRadius - dpToPx(2));
            if (Math.abs(cutoutCenterX - (displayWidthPx / 2.0f)) < (displayWidthPx * 0.15f)) {
                reqX = Math.round(cutoutCenterX - (reqW / 2.0f));
            } else if (cutoutCenterX < displayWidthPx * 0.35f) {
                reqX = Math.round(Math.max(dpToPx(12), cutoutCenterX - cutoutRadius - dpToPx(4)));
            } else {
                reqX = Math.round(Math.min(displayWidthPx - reqW - dpToPx(12), cutoutCenterX + cutoutRadius + dpToPx(4) - reqW));
            }
            reqX = Math.max(dpToPx(8), Math.min(displayWidthPx - reqW - dpToPx(8), reqX));
        } else {
            reqW = dpToPx(150);
            reqH = Math.max(cutoutRadius * 2, dpToPx(34));
            reqY = Math.round(cutoutCenterY - (reqH / 2.0f));
            if (Math.abs(cutoutCenterX - (displayWidthPx / 2.0f)) < (displayWidthPx * 0.15f)) {
                reqX = Math.round(cutoutCenterX - (reqW / 2.0f));
            } else if (cutoutCenterX < displayWidthPx * 0.35f) {
                reqX = Math.round(Math.max(dpToPx(8), cutoutCenterX - reqH / 2.0f));
            } else {
                reqX = Math.round(Math.min(displayWidthPx - reqW - dpToPx(8), cutoutCenterX + reqH / 2.0f - reqW));
            }
            reqX = Math.max(dpToPx(6), Math.min(displayWidthPx - reqW - dpToPx(6), reqX));
        }

        float curLeft = (springX != null) ? springX.current : params.x;
        float curTop = (springY != null) ? springY.current : params.y;
        float curRight = curLeft + ((springW != null) ? springW.current : params.width);
        float curBottom = curTop + ((springH != null) ? springH.current : params.height);

        int unionLeft = Math.max(0, (int) Math.floor(Math.min(curLeft, reqX)));
        int unionTop = Math.max(0, (int) Math.floor(Math.min(curTop, reqY)));
        int unionRight = Math.min(displayWidthPx, (int) Math.ceil(Math.max(curRight, reqX + reqW)));
        int unionBottom = Math.min(displayHeightPx, (int) Math.ceil(Math.max(curBottom, reqY + reqH)));
        int unionW = Math.max(reqW, unionRight - unionLeft);
        int unionH = Math.max(reqH, unionBottom - unionTop);

        if (params.width != unionW || params.height != unionH || params.x != unionLeft || params.y != unionTop || params.flags != targetFlags) {
            params.width = unionW;
            params.height = unionH;
            params.x = unionLeft;
            params.y = unionTop;
            params.flags = targetFlags;
            try { windowManager.updateViewLayout(ringView, params); } catch (Exception ignored) {}
        }
    }

    /**
     * Called once when all springs reach stationary equilibrium.
     * Snaps window bounds neatly to the settled card size.
     */
    private static void onAnimationSettled() {
        if (params == null || windowManager == null || ringView == null) return;

        if (currentIsland == STATE_IDLE) {
            int d = Math.round(cutoutRadius * 2.0f);
            params.width = d;
            params.height = d;
            params.x = cutoutCenterX - cutoutRadius;
            params.y = cutoutCenterY - cutoutRadius;
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            try { windowManager.updateViewLayout(ringView, params); } catch (Exception ignored) {}

            if (!stealthRingIdle) {
                ringView.setVisibility(View.GONE);
            }
        } else {
            int reqW = Math.round(springW.current);
            int reqH = Math.round(springH.current);
            int reqX = Math.round(springX.current);
            int reqY = Math.round(springY.current);

            int targetFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;

            if (params.width != reqW || params.height != reqH || params.x != reqX || params.y != reqY || params.flags != targetFlags) {
                params.width = reqW;
                params.height = reqH;
                params.x = reqX;
                params.y = reqY;
                params.flags = targetFlags;
                try { windowManager.updateViewLayout(ringView, params); } catch (Exception ignored) {}
            }
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

    private static void startBackgroundWorkers() {
        // Worker 1: Media session & ecosystem status poll
        workerPool.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isScreenInteractive) return;

                    if (enableMedia) {
                        queryMediaSessionNative();
                    }
                    if (enableHyperDL) {
                        queryHyperDLStatusNative();
                    }
                    if (isCharging) {
                        readBatteryHardwareTelemetry();
                        if (enableHyperCore) {
                            readHyperCoreStatusNative();
                        }
                    }

                    // Auto state transitions (priority-based)
                    if (currentIsland == STATE_CHARGING && isCharging) {
                        // Keep charging state
                    } else if (currentIsland == STATE_VOLUME || currentIsland == STATE_RINGER || currentIsland == STATE_TORCH || currentIsland == STATE_NOTIFICATION) {
                        // Transient states wait for their timeouts
                    } else if (isHyperDLActive && enableHyperDL) {
                        if (currentIsland != STATE_HYPERDL && currentIsland != STATE_CALIBRATION) {
                            currentIsland = STATE_HYPERDL;
                            isExpanded = false;
                            wakeEngineLoop();
                        }
                    } else if (isMediaPlaying && enableMedia) {
                        if (currentIsland != STATE_MEDIA && currentIsland != STATE_CALIBRATION) {
                            currentIsland = STATE_MEDIA;
                            isExpanded = false;
                            wakeEngineLoop();
                        }
                    } else if (currentIsland != STATE_CHARGING && currentIsland != STATE_CALIBRATION) {
                        if (currentIsland != STATE_IDLE) {
                            currentIsland = STATE_IDLE;
                            isExpanded = false;
                            wakeEngineLoop();
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }, 1000, 1500, TimeUnit.MILLISECONDS);

        // Worker 2: Real-time notification logcat reader
        workerPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    java.lang.Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-b", "events", "-s", "notification_enqueue"});
                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    Pattern flagPattern = Pattern.compile("flags=0x([0-9a-fA-F]+)");
                    while ((line = reader.readLine()) != null) {
                        if (!enableNotifications || !isScreenInteractive) continue;
                        if (line.contains("notification_enqueue") || line.contains("Notification(")) {
                            Matcher fm = flagPattern.matcher(line);
                            if (fm.find()) {
                                try {
                                    int flags = Integer.parseInt(fm.group(1), 16);
                                    // Skip ongoing / foreground service / group summaries
                                    if ((flags & (0x02 | 0x40 | 0x200)) != 0) {
                                        continue;
                                    }
                                } catch (Throwable ignored) {}
                            }

                            int idx = line.indexOf("[");
                            if (idx != -1) {
                                String body = line.substring(idx + 1);
                                String[] parts = body.split(",");
                                if (parts.length >= 3) {
                                    String pkg = parts[2].trim();
                                    if (isUserFacingPackage(pkg)) {
                                        notifAppName = resolveFriendlyAppName(pkg);
                                        queryNotificationDetailsAsync(pkg);
                                        currentIsland = STATE_NOTIFICATION;
                                        isExpanded = false;
                                        expandCollapseTime = SystemClock.uptimeMillis() + 3200;
                                        wakeEngineLoop();
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        });
    }

    private static boolean isUserFacingPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        if (pkg.equals("android") || pkg.contains("systemui") || pkg.contains("misound") 
                || pkg.contains("securitycenter") || pkg.contains("powerkeeper") 
                || pkg.contains("googlequicksearchbox") || pkg.contains("daemon")) {
            return false;
        }
        return true;
    }

    private static String resolveFriendlyAppName(String pkg) {
        if (pkg.contains("whatsapp")) return "WhatsApp";
        if (pkg.contains("telegram")) return "Telegram";
        if (pkg.contains("instagram")) return "Instagram";
        if (pkg.contains("twitter") || pkg.contains("x.android")) return "X";
        if (pkg.contains("discord")) return "Discord";
        if (pkg.contains("youtube")) return "YouTube";
        if (pkg.contains("spotify")) return "Spotify";
        if (pkg.contains("gmail") || pkg.contains("email")) return "Mail";
        if (pkg.contains("messaging") || pkg.contains("mms")) return "Messages";
        int dot = pkg.lastIndexOf(".");
        return dot != -1 ? pkg.substring(dot + 1) : pkg;
    }

    private static void queryNotificationDetailsAsync(final String pkg) {
        workerPool.execute(new Runnable() {
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
                                if (sIdx != -1) {
                                    title = line.substring(sIdx + 8, line.length() - 1);
                                }
                            } else if (line.contains("android.text=") && text.isEmpty()) {
                                int sIdx = line.indexOf("String (");
                                if (sIdx != -1) {
                                    text = line.substring(sIdx + 8, line.length() - 1);
                                }
                            }
                        }
                    }
                    reader.close();
                    p.destroy();
                    if (!title.isEmpty()) notifTitle = title;
                    if (!text.isEmpty()) notifContent = text;
                    wakeEngineLoop();
                } catch (Throwable ignored) {}
            }
        });
    }

    private static void queryMediaSessionNative() {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            boolean audioActive = am != null && am.isMusicActive();

            if (!audioActive && !isMediaPlaying) {
                return;
            }

            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"dumpsys", "media_session"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            boolean foundPlaying = false;
            String tempTitle = "";
            String tempArtist = "";

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("state=PlaybackState")) {
                    if (line.contains("state=3") || line.contains("STATE_PLAYING")) {
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

            boolean prevPlay = isMediaPlaying;
            isMediaPlaying = foundPlaying || audioActive;
            if (foundPlaying) {
                if (!tempTitle.isEmpty()) mediaTitle = tempTitle;
                if (!tempArtist.isEmpty()) mediaArtist = tempArtist;
            }

            if (isMediaPlaying != prevPlay) {
                wakeEngineLoop();
            }
        } catch (Throwable ignored) {}
    }

    private static void toggleMediaPlayback() {
        dispatchKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        isMediaPlaying = !isMediaPlaying;
        wakeEngineLoop();
    }

    private static void skipMediaNext() {
        dispatchKey(KeyEvent.KEYCODE_MEDIA_NEXT);
        wakeEngineLoop();
    }

    private static void skipMediaPrevious() {
        dispatchKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        wakeEngineLoop();
    }

    private static void dispatchKey(final int keyCode) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Runtime.getRuntime().exec(new String[]{"input", "keyevent", String.valueOf(keyCode)});
                } catch (Throwable ignored) {}
            }
        }).start();
    }

    private static void toggleTorchNative() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                    if (cm != null) {
                        String[] ids = cm.getCameraIdList();
                        if (ids != null && ids.length > 0) {
                            try {
                                cm.setTorchMode("0", currentIsland != STATE_TORCH);
                                return;
                            } catch (Throwable t) {
                                for (String id : ids) {
                                    try {
                                        cm.setTorchMode(id, currentIsland != STATE_TORCH);
                                        return;
                                    } catch (Throwable ignored) {}
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }).start();
    }

    private static void queryHyperDLStatusNative() {
        File f = new File("/data/local/tmp/hyperdl_status.json");
        if (!f.exists()) {
            if (isHyperDLActive) {
                isHyperDLActive = false;
                wakeEngineLoop();
            }
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
                hyperDLFile = parseStr(json, "title", "Download");
            } else {
                isHyperDLActive = false;
            }
        } catch (Throwable ignored) {
            isHyperDLActive = false;
        }
    }

    private static void readBatteryHardwareTelemetry() {
        try {
            long currentUa = readLongFromFile("/sys/class/power_supply/battery/current_now");
            long voltageUv = readLongFromFile("/sys/class/power_supply/battery/voltage_now");
            long tempTenths = readLongFromFile("/sys/class/power_supply/battery/temp");

            if (tempTenths > 0) {
                float tempC = tempTenths / 10.0f;
                batteryTempStr = String.format(Locale.US, "%.1f°C", tempC);
            }

            if (currentUa != 0 && voltageUv != 0) {
                double currentA = Math.abs(currentUa) / 1000000.0;
                double voltageV = voltageUv / 1000000.0;
                double watt = currentA * voltageV;
                int currentMa = (int) Math.round(Math.abs(currentUa) / 1000.0);

                if (watt > 0.5) {
                    chargeWattStr = String.format(Locale.US, "%.1fW", watt);
                    chargeCurrentStr = currentMa + "mA";
                }
            }
        } catch (Throwable ignored) {}
    }

    private static long readLongFromFile(String path) {
        File f = new File(path);
        if (!f.exists()) return 0L;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line = br.readLine();
            if (line != null) {
                return Long.parseLong(line.trim());
            }
        } catch (Throwable ignored) {}
        return 0L;
    }

    private static void readHyperCoreStatusNative() {
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
                    else if (currentIsland == STATE_VOLUME) stateName = "volume";
                    else if (currentIsland == STATE_RINGER) stateName = "ringer";
                    else if (currentIsland == STATE_NOTIFICATION) stateName = "notification";
                    else if (currentIsland == STATE_TORCH) stateName = "torch";
                    else if (currentIsland == STATE_HYPERDL) stateName = "hyperdl";
                    else if (currentIsland == STATE_CALIBRATION) stateName = "calibration";

                    StringBuilder sb = new StringBuilder();
                    sb.append("{\n");
                    sb.append("  \"pid\": ").append(android.os.Process.myPid()).append(",\n");
                    sb.append("  \"active_island\": \"").append(stateName).append("\",\n");
                    sb.append("  \"expanded\": ").append(isExpanded).append(",\n");
                    sb.append("  \"media_title\": \"").append(mediaTitle.replace("\"", "\\\"")).append("\",\n");
                    sb.append("  \"media_artist\": \"").append(mediaArtist.replace("\"", "\\\"")).append("\",\n");
                    sb.append("  \"media_playing\": ").append(isMediaPlaying).append(",\n");
                    sb.append("  \"battery_pct\": ").append(batteryPct).append(",\n");
                    sb.append("  \"battery_charging\": ").append(isCharging).append(",\n");
                    sb.append("  \"volume_percent\": ").append(volumePercent).append(",\n");
                    sb.append("  \"ringer_label\": \"").append(ringerLabel).append("\",\n");
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

    private static void processTriggerCmd() {
        File trig = new File(triggerPath);
        if (!trig.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(trig))) {
            String cmd = br.readLine();
            if (cmd != null) {
                cmd = cmd.trim();
                if (cmd.startsWith("charge")) {
                    readBatteryHardwareTelemetry();
                    triggerChargingEvent();
                } else if (cmd.startsWith("media")) {
                    currentIsland = STATE_MEDIA;
                    isMediaPlaying = true;
                    mediaTitle = "Starboy";
                    mediaArtist = "The Weeknd";
                    isExpanded = false;
                    wakeEngineLoop();
                } else if (cmd.startsWith("volume")) {
                    String[] parts = cmd.split(":");
                    if (parts.length > 1) {
                        try { volumePercent = Integer.parseInt(parts[1].trim()); } catch (Exception ignored) {}
                    } else {
                        volumePercent = 75;
                    }
                    currentIsland = STATE_VOLUME;
                    isExpanded = false;
                    expandCollapseTime = SystemClock.uptimeMillis() + 2000;
                    wakeEngineLoop();
                } else if (cmd.startsWith("ringer")) {
                    String[] parts = cmd.split(":");
                    if (parts.length > 1) {
                        ringerLabel = parts[1].trim();
                    } else {
                        ringerLabel = "Silent";
                    }
                    currentIsland = STATE_RINGER;
                    isExpanded = false;
                    expandCollapseTime = SystemClock.uptimeMillis() + 2200;
                    wakeEngineLoop();
                } else if (cmd.startsWith("notif") || cmd.startsWith("notification")) {
                    String[] parts = cmd.split(":");
                    if (parts.length > 3) {
                        notifAppName = parts[1].trim();
                        notifTitle = parts[2].trim();
                        notifContent = parts[3].trim();
                    } else if (parts.length > 2) {
                        notifAppName = parts[1].trim();
                        notifTitle = parts[2].trim();
                        notifContent = "New message received";
                    } else if (parts.length > 1) {
                        notifAppName = parts[1].trim();
                        notifTitle = "Notification";
                        notifContent = "New update available";
                    } else {
                        notifAppName = "Telegram";
                        notifTitle = "Alex";
                        notifContent = "Build completed successfully";
                    }
                    currentIsland = STATE_NOTIFICATION;
                    isExpanded = false;
                    expandCollapseTime = SystemClock.uptimeMillis() + 3200;
                    wakeEngineLoop();
                } else if (cmd.startsWith("torch")) {
                    if (currentIsland == STATE_TORCH) {
                        currentIsland = STATE_IDLE;
                    } else {
                        currentIsland = STATE_TORCH;
                        isExpanded = false;
                        expandCollapseTime = SystemClock.uptimeMillis() + 2600;
                    }
                    wakeEngineLoop();
                } else if (cmd.startsWith("hyperdl") || cmd.startsWith("download")) {
                    currentIsland = STATE_HYPERDL;
                    isHyperDLActive = true;
                    hyperDLSpeed = "12.4 MB/s";
                    hyperDLProgress = 68;
                    isExpanded = false;
                    wakeEngineLoop();
                } else if ("calibrate".equalsIgnoreCase(cmd)) {
                    currentIsland = STATE_CALIBRATION;
                    calibrationEndTime = SystemClock.uptimeMillis() + 6000;
                    wakeEngineLoop();
                } else if ("expand".equalsIgnoreCase(cmd)) {
                    if (currentIsland == STATE_IDLE) currentIsland = STATE_MEDIA;
                    isExpanded = true;
                    wakeEngineLoop();
                } else if ("collapse".equalsIgnoreCase(cmd)) {
                    isExpanded = false;
                    wakeEngineLoop();
                } else if ("idle".equalsIgnoreCase(cmd)) {
                    currentIsland = STATE_IDLE;
                    isExpanded = false;
                    wakeEngineLoop();
                }
            }
        } catch (Throwable ignored) {}
        trig.delete();
    }

    private static void readConfig() {
        File file = new File(configPath);
        if (!file.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            String json = sb.toString();

            cutoutCenterX       = parseInt(json, "cutout_x", cutoutCenterX);
            cutoutCenterY       = parseInt(json, "cutout_y", cutoutCenterY);
            cutoutRadius        = parseInt(json, "cutout_radius", cutoutRadius);
            enableMedia         = parseBool(json, "enable_media", enableMedia);
            enableCharging      = parseBool(json, "enable_charging", enableCharging);
            enableVolume        = parseBool(json, "enable_volume", enableVolume);
            enableRinger        = parseBool(json, "enable_ringer", enableRinger);
            enableNotifications = parseBool(json, "enable_notifications", enableNotifications);
            enableHyperDL       = parseBool(json, "enable_hyperdl", enableHyperDL);
            enableHyperCore     = parseBool(json, "enable_hypercore", enableHyperCore);
            stealthRingIdle     = parseBool(json, "stealth_ring_idle", stealthRingIdle);
            hideInLandscape     = parseBool(json, "hide_in_landscape", hideInLandscape);
            springStiffness     = parseFloat(json, "spring_stiffness", springStiffness);
            springDamping       = parseFloat(json, "spring_damping", springDamping);
            autoExpandCharging  = parseBool(json, "auto_expand_charging", autoExpandCharging);
            expandTimeoutMs     = parseInt(json, "expand_timeout_ms", expandTimeoutMs);

            if (springX != null) {
                springX.setParameters(springStiffness, springDamping);
                springY.setParameters(springStiffness, springDamping);
                springW.setParameters(springStiffness, springDamping);
                springH.setParameters(springStiffness, springDamping);
                springR.setParameters(springStiffness, springDamping);
                springContentAlpha.setParameters(springStiffness, springDamping);
            }
        } catch (Throwable ignored) {}
    }

    private static int dpToPx(float dp) {
        return Math.round(dp * displayDensity);
    }

    private static int spToPx(float sp) {
        return Math.round(sp * displayDensity);
    }

    private static String getJsonRawVal(String json, String key) {
        if (json == null || key == null) return null;
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(?:\"([^\"]*)\"|([^,\\}\\s]+))");
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
