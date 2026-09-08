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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RadialGradient;
import android.graphics.LinearGradient;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.hardware.camera2.CameraManager;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import java.io.InputStream;
import java.util.List;
import android.os.BatteryManager;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.graphics.Outline;
import android.os.HandlerThread;
import android.view.Choreographer;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewOutlineProvider;
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
    private static HandlerThread backgroundWorkerThread;
    private static Handler backgroundHandler;
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
    private static int cutoutCenterX           = 0;
    private static int cutoutCenterY           = 0;
    private static int cutoutRadius            = 0;
    private static int xOffset                 = 0;
    private static int yOffset                 = 0;
    private static int customPillWidth         = 0;
    private static int customPillHeight        = 0;
    private static int customCardWidth         = 0;
    private static int customCardHeight        = 0;
    private static int cardRadius              = 24;
    private static int cardYOffset             = 0;
    private static String cardPositionMode     = "below";
    private static String pillAlignment        = "center";
    private static boolean notchMode           = false;
    private static boolean masterEnabled       = true;
    private static boolean previewLock         = false;
    private static boolean enableMedia         = true;
    private static boolean mediaShowPillArt    = true;
    private static String mediaArtStyle        = "rounded";
    private static boolean mediaShowWaveform   = true;
    private static boolean mediaAmbientGlow    = true;
    private static int mediaGlowOpacity        = 25;
    private static boolean mediaMarquee        = true;
    private static boolean enableCharging      = true;
    private static boolean enableVolume        = true;
    private static boolean enableRinger        = true;
    private static boolean enableNotifications = true;
    private static boolean enableTorch         = true;
    private static boolean enableHyperDL       = true;
    private static boolean enableHyperCore     = true;
    private static boolean stealthRingIdle     = false;
    private static boolean hideInLandscape     = true;
    private static float springStiffness       = 380.0f;
    private static float springDamping         = 0.78f;
    private static boolean autoExpandCharging  = true;
    private static int lastDisplayRotation     = -1;
    private static int lastDisplayW            = -1;
    private static int lastDisplayH            = -1;
    private static boolean autoExpandMedia     = false;
    private static boolean autoExpandNotif     = false;
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
    private static volatile int activeIslandType = STATE_IDLE;
    private static volatile boolean isCollapsing = false;
    private static volatile boolean isExpanded = false;
    private static volatile boolean isHUNTucked = false;
    private static long calibrationEndTime = 0L;

    // Screen power lifecycle
    private static volatile boolean isScreenInteractive = true;

    // Engine loop lifecycle (Zero-overhead sleep when settled)
    private static volatile boolean isLoopRunning = false;

    // Telemetry: Battery & Power
    private static volatile int batteryPct = -1;
    private static volatile boolean isCharging = false;
    private static volatile String chargeWattStr = "0.0W";
    private static volatile String chargeCurrentStr = "0mA";
    private static volatile String batteryTempStr = "";
    private static volatile String hyperCoreProfile = "Default";

    // Telemetry: Media
    private static volatile String mediaTitle = "No active playback";
    private static volatile String mediaArtist = "Media";
    private static volatile boolean isMediaPlaying = false;
    private static volatile long lastMediaPoll = 0L;
    private static volatile long mediaTrackPosition = 0L;
    private static volatile long mediaPositionUpdateTime = 0L;
    private static volatile long mediaTrackDuration = 0L;
    private static volatile MediaController activeMediaController = null;
    private static volatile Bitmap currentPillArt = null;
    private static volatile Bitmap currentCardArt = null;
    private static volatile int mediaDominantColor = Color.TRANSPARENT;
    private static volatile String lastArtKey = "";

    // Telemetry: Volume
    private static volatile int volumePercent = 50;
    private static volatile int lastVolumeLevel = -1;
    
    // Telemetry: Ringer
    private static volatile String ringerLabel = "Ring";
    private static volatile int lastRingerLevel = -1;

    // Telemetry: Torch
    private static volatile boolean isTorchActive = false;

    // Telemetry: Notification
    private static volatile String notifAppName = "Notification";
    private static volatile String notifTitle = "";
    private static volatile String notifContent = "";
    private static volatile long lastNotifTime = 0L;
    private static volatile String lastNotifPkg = "";


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

            if (target <= cutoutRadius * 2.0f && target > 1.5f && current < target) {
                current = target;
                velocity = 0f;
                return false;
            }

            boolean isNormalized = (target <= 1.05f && target >= 0.0f);
            float posEpsilon = isNormalized ? 0.0005f : 0.25f;
            float velEpsilon = isNormalized ? 0.001f : 0.25f;

            if (Math.abs(velocity) < velEpsilon && Math.abs(current - target) < posEpsilon) {
                current = target;
                velocity = 0f;
                return false;
            }
            return true;
        }

        public boolean isMoving() {
            boolean isNormalized = (target <= 1.05f && target >= 0.0f);
            float posEpsilon = isNormalized ? 0.0005f : 0.25f;
            float velEpsilon = isNormalized ? 0.001f : 0.25f;
            return Math.abs(velocity) > velEpsilon || Math.abs(current - target) > posEpsilon;
        }

        public boolean isAtEquilibrium() {
            return !isMoving();
        }
    }

    // Fluid Geometry Springs (width, height, corner-radius, content alpha, unified morph progress)
    private static Spring springW;
    private static Spring springH;
    private static Spring springR;
    private static Spring springContentAlpha;
    private static Spring morphSpring;
    private static Spring crossfadeSpring;
    private static int previousIsland = STATE_IDLE;

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
    private static Paint paintArtBitmap;
    private static Paint paintAmbientGlow;
    private static Paint paintProgress;
    private static RectF tempRectF;
    private static final RectF artRectF = new RectF();
    private static final Path artClipPath = new Path();
    private static final Path tempClipPath = new Path();
    private static RadialGradient cachedGlowGradient = null;
    private static int cachedGlowColor = 0;
    private static float cachedGlowW = -1f;
    private static Paint paintFadeMask;
    private static final RectF marqueeBounds = new RectF();
    private static LinearGradient cachedMarqueeGradient = null;
    private static float cachedMarqueeLeft = -1f;
    private static float cachedMarqueeRight = -1f;

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
        backgroundWorkerThread = new HandlerThread("HyperRing-IO");
        backgroundWorkerThread.start();
        backgroundHandler = new Handler(backgroundWorkerThread.getLooper());
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

        // Direct hardware telemetry and system state initialization
        checkScreenInteractive();
        queryBatteryHardware();
        initAudioTelemetry();
        queryTorchStatusNative();
        queryMediaSessionNative();

        float initD = cutoutRadius * 2.0f;

        springW = new Spring(initD, springStiffness, springDamping);
        springH = new Spring(initD, springStiffness, springDamping);
        springR = new Spring(cutoutRadius, springStiffness, springDamping);
        springContentAlpha = new Spring(0.0f, 520f, 1.05f);
        morphSpring = new Spring(0.0f, 320f, 0.82f);
        crossfadeSpring = new Spring(1.0f, 480f, 0.92f);

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
            if (defaultDisplay != null && defaultDisplay.getDisplayId() == Display.DEFAULT_DISPLAY) {
                defaultDisplay.getRealMetrics(realMetrics);
                if (realMetrics.widthPixels > 0 && realMetrics.heightPixels > 0) {
                    displayWidthPx = realMetrics.widthPixels;
                    displayHeightPx = realMetrics.heightPixels;
                    displayDensity = realMetrics.density;
                }
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
        if (cutoutRadius <= 0) {
            cutoutRadius = Math.round(11f * displayDensity);
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

        paintArtBitmap = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paintAmbientGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintProgress = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintProgress.setStyle(Paint.Style.FILL);

        paintFadeMask = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintFadeMask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

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
                            startCollapse();
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
                    int stream = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1);
                    // Only trigger for music stream (3), ignore system/ring/accessibility streams
                    if (stream != AudioManager.STREAM_MUSIC) return;
                    int val = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1);
                    int prevVal = intent.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -1);
                    // Ignore if value didn't change (spurious fire)
                    if (val < 0 || val == prevVal) return;
                    AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                    int max = am != null ? am.getStreamMaxVolume(stream) : 15;
                    volumePercent = Math.round((val / (float) Math.max(1, max)) * 100f);
                    lastVolumeLevel = val;
                    previewLock = false;
                    if (currentIsland != STATE_CALIBRATION) {
                        showIsland(STATE_VOLUME, 1800);
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

                        showIsland(STATE_RINGER, 2200);
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
                            showIsland(STATE_TORCH, 2600);
                        } else if (currentIsland == STATE_TORCH) {
                            startCollapse();
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

        // DisplayListener for Screen Recording, VirtualDisplay injection & orientation changes
        try {
            DisplayManager dm = (DisplayManager) sysContext.getSystemService(Context.DISPLAY_SERVICE);
            if (dm != null) {
                dm.registerDisplayListener(new DisplayManager.DisplayListener() {
                    @Override
                    public void onDisplayAdded(int displayId) {
                        scheduleRebindRetry();
                    }

                    @Override
                    public void onDisplayRemoved(int displayId) {
                        scheduleRebindRetry();
                    }

                    @Override
                    public void onDisplayChanged(int displayId) {
                        if (displayId == Display.DEFAULT_DISPLAY) {
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
                    }
                }, handler);
            }
        } catch (Throwable ignored) {}
    }

    private static final Runnable rebindRunnable = new Runnable() {
        @Override
        public void run() {
            checkDisplayRebind();
        }
    };


    private static void scheduleRebindRetry() {
        if (handler == null) return;
        handler.removeCallbacks(rebindRunnable);
        checkDisplayRebind();
        handler.postDelayed(rebindRunnable, 250);
    }

    private static void checkDisplayRebind() {
        if (handler == null) return;
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    DisplayManager dm = (DisplayManager) sysContext.getSystemService(Context.DISPLAY_SERVICE);
                    if (dm != null) {
                        Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
                        if (d != null) {
                            defaultDisplay = d;
                            Context dCtx = sysContext.createDisplayContext(defaultDisplay);
                            if (dCtx != null) context = dCtx;
                            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                            if (wm != null) windowManager = wm;
                        }
                    }
                    resolveDisplayMetrics();
                    if (ringView != null && windowManager != null) {
                        boolean attached = ringView.isAttachedToWindow() && ringView.getWindowToken() != null;
                        if (!attached) {
                            try {
                                windowManager.removeViewImmediate(ringView);
                            } catch (Throwable ignored) {}
                            boolean readded = false;
                            try {
                                if (ringView.getParent() == null && params != null) {
                                    params.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
                                    params.windowAnimations = 0;
                                    try {
                                        java.lang.reflect.Field pf = WindowManager.LayoutParams.class.getField("privateFlags");
                                        int curPf = pf.getInt(params);
                                        pf.setInt(params, curPf | 0x00000040); // PRIVATE_FLAG_NO_MOVE_ANIMATION
                                    } catch (Throwable ignored2) {}
                                    windowManager.addView(ringView, params);
                                    readded = true;
                                }
                            } catch (Throwable ignored) {}
                            if (!readded) {
                                attachWindow();
                                return;
                            }
                        } else if (params != null) {
                            params.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
                            params.windowAnimations = 0;
                            try {
                                java.lang.reflect.Field pf = WindowManager.LayoutParams.class.getField("privateFlags");
                                int curPf = pf.getInt(params);
                                pf.setInt(params, curPf | 0x00000040); // PRIVATE_FLAG_NO_MOVE_ANIMATION
                            } catch (Throwable ignored2) {}
                            try {
                                windowManager.updateViewLayout(ringView, params);
                            } catch (Throwable ignored) {}
                        }
                    } else if (ringView == null) {
                        attachWindow();
                        return;
                    }

                    if (ringView != null) {
                        boolean land = hideInLandscape && isLandscape() && currentIsland != STATE_CALIBRATION;
                        if (land) {
                            if (ringView.getVisibility() != View.GONE) {
                                ringView.setVisibility(View.GONE);
                            }
                            if (isExpanded) isExpanded = false;
                            if (params != null && windowManager != null) {
                                params.width = 1;
                                params.height = 1;
                                params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                                try { windowManager.updateViewLayout(ringView, params); } catch (Exception ignored) {}
                            }
                            return;
                        } else if (currentIsland != STATE_IDLE) {
                            if (ringView.getVisibility() != View.VISIBLE) {
                                ringView.setVisibility(View.VISIBLE);
                            }
                            prepareWindowForTarget();
                        } else if (!isCollapsing) {
                            if (ringView.getVisibility() != View.GONE) {
                                ringView.setVisibility(View.GONE);
                            }
                            prepareWindowForTarget();
                        }
                        ringView.requestLayout();
                        ringView.invalidate();
                    }
                    wakeEngineLoop();
                } catch (Throwable ignored) {}
            }
        });
    }

    private static void triggerChargingEvent() {
        if (autoExpandCharging) {
            showIsland(STATE_CHARGING, expandTimeoutMs);
            isExpanded = true;
        } else {
            showIsland(STATE_CHARGING, 4500);
        }
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
        if (defaultDisplay == null || defaultDisplay.getDisplayId() != Display.DEFAULT_DISPLAY) {
            DisplayManager dm = (DisplayManager) sysContext.getSystemService(Context.DISPLAY_SERVICE);
            if (dm != null) {
                defaultDisplay = dm.getDisplay(Display.DEFAULT_DISPLAY);
            }
        }
        if (defaultDisplay != null) {
            Context dCtx = sysContext.createDisplayContext(defaultDisplay);
            if (dCtx != null) context = dCtx;
        }
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) return;

        ringView = new RingView(context);
        ringView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                v.postInvalidate();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                scheduleRebindRetry();
            }
        });

        // TYPE_STATUS_BAR_SUB_PANEL = 2017: Primary overlay type supported cleanly on HyperOS root app_process
        // Falls back to TYPE_APPLICATION_OVERLAY (2038) and TYPE_ACCESSIBILITY_OVERLAY (2032)
        int type = 2017;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

        int initDiameter = Math.round(cutoutRadius * 2.0f);

        params = new WindowManager.LayoutParams(
                initDiameter,
                initDiameter,
                type,
                flags,
                PixelFormat.TRANSLUCENT
        );
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
                effCutoutX = (displayWidthPx - cutoutCenterY) + xOffset;
            } else {
                effCutoutX = cutoutCenterY + xOffset;
            }
            effCutoutY = (displayHeightPx / 2.0f) + yOffset;
        } else {
            effCutoutX = cutoutCenterX + xOffset;
            effCutoutY = cutoutCenterY + yOffset;
        }
        if (effCutoutX <= 0 && displayWidthPx > 0) {
            effCutoutX = displayWidthPx / 2.0f;
        }
        int compactH = (customPillHeight > 0) ? dpToPx(customPillHeight) : Math.max(Math.round(cutoutRadius * 2.0f), dpToPx(34));
        int topAnchorY = notchMode ? Math.max(0, yOffset) : Math.round(effCutoutY - (compactH / 2.0f));

        params.gravity = Gravity.TOP | Gravity.START;
        if ("left".equalsIgnoreCase(pillAlignment)) {
            params.x = Math.round(effCutoutX - (initDiameter / 2.0f));
        } else if ("right".equalsIgnoreCase(pillAlignment)) {
            params.x = Math.round(displayWidthPx - (effCutoutX + (initDiameter / 2.0f)));
        } else if ("freeform".equalsIgnoreCase(pillAlignment)) {
            params.x = Math.round(effCutoutX - (initDiameter / 2.0f));
        } else {
            params.x = Math.round(effCutoutX - (initDiameter / 2.0f));
        }
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
            params.type = 2017; // TYPE_STATUS_BAR_SUB_PANEL
            windowManager.addView(ringView, params);
        } catch (Throwable t1) {
            try {
                params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY; // 2038
                windowManager.addView(ringView, params);
            } catch (Throwable t2) {
                try {
                    params.type = 2032; // TYPE_ACCESSIBILITY_OVERLAY
                    windowManager.addView(ringView, params);
                } catch (Throwable ignored) {}
            }
        }
        if (currentIsland == STATE_IDLE) {
            ringView.setVisibility(View.GONE);
        }
    }

    private static float getPillRelX(float viewW, float pillW) {
        if ("left".equalsIgnoreCase(pillAlignment)) {
            return 0f;
        } else if ("right".equalsIgnoreCase(pillAlignment)) {
            return Math.max(0f, viewW - pillW);
        } else {
            return Math.max(0f, (viewW - pillW) / 2.0f);
        }
    }

    private static float getPillRelY(float viewH, float pillH) {
        if (currentIsland == STATE_CALIBRATION) {
            return Math.max(0f, (viewH - pillH) / 2.0f);
        }
        return 0f;
    }

    static class RingView extends View {
        public RingView(Context context) {
            super(context);
            setBackgroundColor(Color.TRANSPARENT);
            setClickable(true);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    if (springW == null || springH == null || springR == null) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dpToPx(18));
                        return;
                    }
                    float pillW = springW.current;
                    float pillH = springH.current;
                    float pillRelX = getPillRelX(view.getWidth(), pillW);
                    float pillRelY = getPillRelY(view.getHeight(), pillH);
                    outline.setRoundRect(
                            Math.round(pillRelX),
                            Math.round(pillRelY),
                            Math.round(pillRelX + pillW),
                            Math.round(pillRelY + pillH),
                            Math.max(dpToPx(4), springR.current)
                    );
                }
            });
            setClipToOutline(true);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (params == null) return false;

            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                if (isExpanded) {
                    collapseCard();
                    return true;
                }
                return false;
            }

            float x = event.getX();
            float y = event.getY();

            float pillW = springW.current;
            float pillH = springH.current;
            float pillRelX = getPillRelX(getWidth(), pillW);
            float pillRelY = getPillRelY(getHeight(), pillH);

            boolean inside = true;
            if (isExpanded) {
                float pad = dpToPx(8);
                inside = (x >= pillRelX - pad && x <= pillRelX + pillW + pad && y >= pillRelY - pad && y <= pillRelY + pillH + pad);
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (!inside) {
                        if (isExpanded) {
                            collapseCard();
                            return true;
                        }
                        return false;
                    }
                    touchDownX = x;
                    touchDownY = y;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dy = y - touchDownY;
                    if (dy > dpToPx(14) && !isExpanded && currentIsland != STATE_IDLE && currentIsland != STATE_CALIBRATION) {
                        expandCard();
                        return true;
                    } else if (dy < -dpToPx(14) && isExpanded) {
                        collapseCard();
                        return true;
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    float totalDistX = Math.abs(x - touchDownX);
                    float totalDistY = Math.abs(y - touchDownY);
                    if (totalDistX < dpToPx(24) && totalDistY < dpToPx(24)) {
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
            if (hideInLandscape && isLandscape() && currentIsland != STATE_CALIBRATION) {
                return;
            }
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            invalidateOutline();
            float pillW = springW.current;
            float pillH = springH.current;

            // Micro-squash & stretch effect: fluid volume conservation based on horizontal spring velocity
            float velX = springW.velocity;
            float hSquash = 0f;
            boolean isMorphFlight = morphSpring != null && (!morphSpring.isAtEquilibrium() || morphSpring.current > 0.0005f);
            if (!isMorphFlight && !isExpanded && Math.abs(velX) > 120f) {
                hSquash = Math.max(-dpToPx(2.0f), Math.min(dpToPx(3.5f), (velX / 1400f) * dpToPx(3.0f)));
            }
            float effH = Math.max(cutoutRadius * 2.0f, pillH - hSquash);

            float pillRelX = getPillRelX(getWidth(), pillW);
            float pillRelY = getPillRelY(getHeight(), effH);

            canvas.save();
            canvas.translate(pillRelX, pillRelY);
            renderIsland(canvas, pillW, effH);
            canvas.restore();
        }
    }

    private static void syncMediaPlaybackState() {
        try {
            if (activeMediaController != null) {
                PlaybackState ps = activeMediaController.getPlaybackState();
                if (ps != null) {
                    mediaTrackPosition = ps.getPosition();
                    mediaPositionUpdateTime = ps.getLastPositionUpdateTime();
                    isMediaPlaying = (ps.getState() == PlaybackState.STATE_PLAYING);
                }
                MediaMetadata md = activeMediaController.getMetadata();
                if (md != null) {
                    long dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
                    if (dur > 0) mediaTrackDuration = dur;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void expandCard() {
        if (isExpanded) return;
        isExpanded = true;
        if (handler != null) {
            handler.removeCallbacks(autoCollapseRunnable);
            if (expandTimeoutMs > 0 && !previewLock) {
                handler.postDelayed(autoCollapseRunnable, Math.max(6000, expandTimeoutMs));
            }
        }
        if (currentIsland == STATE_MEDIA) {
            syncMediaPlaybackState();
        }
        // Unified single-phase morphing physics: organic spring curve (stiffness ~320, damping ~0.82)
        if (morphSpring != null) {
            morphSpring.setParameters(320f, 0.82f);
            morphSpring.setTarget(1.0f);
        }
        if (springContentAlpha != null) {
            springContentAlpha.setParameters(420f, 0.88f);
            springContentAlpha.setTarget(1.0f);
        }
        resolveTargetState(SystemClock.uptimeMillis());
        prepareWindowForTarget();
        wakeEngineLoop();
    }

    private static void collapseCard() {
        if (!isExpanded) return;
        isExpanded = false;
        if (handler != null) {
            handler.removeCallbacks(autoCollapseRunnable);
        }
        // Unified single-phase morphing physics: fluid organic collapse curve (~240ms duration, stiffness ~340, damping ~0.88)
        if (morphSpring != null) {
            morphSpring.setParameters(340f, 0.88f);
            morphSpring.setTarget(0.0f);
        }
        if (springContentAlpha != null) {
            springContentAlpha.setParameters(340f, 0.88f);
            springContentAlpha.setTarget(0.0f);
        }
        resolveTargetState(SystemClock.uptimeMillis());
        wakeEngineLoop();
    }

    private static void handleTap(float x, float y) {
        if (!isExpanded) {
            if (currentIsland != STATE_IDLE && currentIsland != STATE_CALIBRATION) {
                expandCard();
            }
        } else {
            float viewW = springW.current;
            float viewH = springH.current;

            if (currentIsland == STATE_MEDIA) {
                float artSize = dpToPx(48);
                float holeRelY = (params != null) ? Math.max(0, (cutoutCenterY + yOffset) - params.y) : dpToPx(16);
                boolean isCutoutCenter = !"left".equalsIgnoreCase(pillAlignment) && !"right".equalsIgnoreCase(pillAlignment);
                boolean isFloatingBelow = !"cover".equalsIgnoreCase(cardPositionMode) && !notchMode;
                float baseTopY = isFloatingBelow ? dpToPx(20) : (isCutoutCenter ? Math.max(dpToPx(24), holeRelY + cutoutRadius + dpToPx(6)) : dpToPx(20));

                float barY = baseTopY + artSize + dpToPx(14);
                float barLeft = dpToPx(22);
                float barW = viewW - dpToPx(44);

                // Interactive Seekbar tap support
                if (mediaTrackDuration > 0 && Math.abs(y - barY) < dpToPx(14) && x >= barLeft - dpToPx(8) && x <= barLeft + barW + dpToPx(8)) {
                    float fraction = Math.max(0f, Math.min(1f, (x - barLeft) / barW));
                    long seekTarget = (long) (fraction * mediaTrackDuration);
                    seekMediaTo(seekTarget);
                    return;
                }

                float btnY = viewH - dpToPx(22);
                float centerX = viewW / 2.0f;
                float prevBtnX = centerX - dpToPx(65);
                float nextBtnX = centerX + dpToPx(65);
                float hitRadius = dpToPx(28);

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
                collapseCard();
                return;
            } else if (currentIsland == STATE_TORCH) {
                collapseCard();
                startCollapse();
                return;
            } else {
                collapseCard();
            }
        }
    }

    private static void renderIsland(Canvas canvas, float w, float h) {
        if (w <= 0 || h <= 0) return;

        // Calibration Reticle Overlay: strictly transparent background so physical camera lens is fully visible
        if (currentIsland == STATE_CALIBRATION) {
            float holeRelX = w / 2.0f;
            float holeRelY = h / 2.0f;

            // Reticle circle
            paintCalibRing.setColor(Color.parseColor("#00E5FF"));
            paintCalibRing.setStrokeWidth(dpToPx(2.0f));
            paintCalibRing.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(holeRelX, holeRelY, cutoutRadius, paintCalibRing);

            // Subtle outer guide margin (+4dp)
            paintCalibRing.setColor(Color.parseColor("#4400E5FF"));
            canvas.drawCircle(holeRelX, holeRelY, cutoutRadius + dpToPx(4), paintCalibRing);

            // Precision crosshairs
            paintCalibCross.setColor(Color.parseColor("#FF1744"));
            paintCalibCross.setStrokeWidth(dpToPx(1.5f));
            paintCalibCross.setStyle(Paint.Style.STROKE);
            canvas.drawLine(holeRelX - dpToPx(22), holeRelY, holeRelX + dpToPx(22), holeRelY, paintCalibCross);
            canvas.drawLine(holeRelX, holeRelY - dpToPx(22), holeRelX, holeRelY + dpToPx(22), paintCalibCross);

            // Center pinhole dot
            paintCalibCross.setStyle(Paint.Style.FILL);
            canvas.drawCircle(holeRelX, holeRelY, dpToPx(2.5f), paintCalibCross);
            return;
        }

        float curR = springR.current;
        tempRectF.set(0, 0, w, h);

        // OLED Black Surface
        canvas.drawRoundRect(tempRectF, curR, curR, paintOledBlack);

        float contentAlpha = springContentAlpha.current;

        // Specular ambient boundary (fades smoothly with content alpha)
        int borderAlpha = Math.min(60, Math.max(0, (int) (contentAlpha * 60)));
        if (borderAlpha > 0 && (currentIsland != STATE_IDLE || isExpanded || isCollapsing)) {
            paintBorder.setAlpha(borderAlpha);
            canvas.drawRoundRect(tempRectF, curR, curR, paintBorder);
        }

        if (contentAlpha > 0.04f) {
            canvas.save();
            tempClipPath.reset();
            tempClipPath.addRoundRect(tempRectF, curR, curR, Path.Direction.CW);
            canvas.clipPath(tempClipPath);

            float compactH = (customPillHeight > 0) ? dpToPx(customPillHeight) : Math.max(Math.round(cutoutRadius * 2.0f), dpToPx(34));
            float targetCardH = getDefaultCardHeight(currentIsland);
            float progress = Math.max(0.0f, Math.min(1.0f, (h - compactH) / Math.max(1.0f, targetCardH - compactH)));

            if (isExpanded) {
                // EXPANDING PHASE:
                if (progress > 0.40f) {
                    // FastOutSlowIn cubic-bezier curve approximation: (0.4, 0, 0.2, 1) -> t * t * (3 - 2t)
                    float t = (progress - 0.40f) / 0.60f;
                    float fastOutSlow = t * t * (3.0f - 2.0f * t);
                    renderExpandedContent(canvas, w, h, contentAlpha * fastOutSlow);
                } else {
                    // Smoothly cross-fade out compact content as pill expands
                    float compactFade = Math.max(0.0f, 1.0f - (progress / 0.40f));
                    if (compactFade > 0.02f) {
                        renderCompactContent(canvas, w, h, contentAlpha * compactFade);
                    }
                }
            } else {
                // COLLAPSING PHASE:
                // Instantly fade out internal children views (1.0 -> 0.0) within the first 28% of collapse
                // Shape bounds animate as pure OLED silhouette for the remainder of the shrink (anti-clipping)
                if (progress > 0.72f) {
                    float collapseAlpha = (progress - 0.72f) / 0.28f;
                    renderExpandedContent(canvas, w, h, contentAlpha * collapseAlpha);
                } else if (progress < 0.20f && !isCollapsing) {
                    // Compact content re-emerges smoothly as pill finishes settling into target
                    float pillFade = (0.20f - progress) / 0.20f;
                    renderCompactContent(canvas, w, h, contentAlpha * pillFade);
                }
            }

            canvas.restore();
        }
    }

    private static void renderCompactContent(Canvas canvas, float curW, float curH, float alpha) {
        float centerY = curH / 2.0f;
        float holeR = cutoutRadius;
        float iconCenterX;
        float textCenterX;
        Paint.Align textAlign;

        if ("left".equalsIgnoreCase(pillAlignment)) {
            iconCenterX = Math.max(dpToPx(16), holeR * 2.0f + dpToPx(14));
            textCenterX = curW - dpToPx(18);
            textAlign = Paint.Align.RIGHT;
        } else if ("right".equalsIgnoreCase(pillAlignment)) {
            iconCenterX = dpToPx(18);
            textCenterX = Math.min(curW - dpToPx(16), curW - holeR * 2.0f - dpToPx(14));
            textAlign = Paint.Align.RIGHT;
        } else {
            // center cutout: symmetric wings around curW / 2.0f
            float leftWingW = (curW / 2.0f) - holeR;
            float rightWingStart = (curW / 2.0f) + holeR;
            float rightWingW = curW - rightWingStart;

            iconCenterX = leftWingW / 2.0f;
            textCenterX = rightWingStart + (rightWingW / 2.0f);
            textAlign = Paint.Align.CENTER;
        }

        int curType = (activeIslandType != STATE_IDLE ? activeIslandType : currentIsland);
        boolean isCrossfading = (previousIsland != STATE_IDLE && previousIsland != curType && crossfadeSpring != null && crossfadeSpring.isMoving());

        if (isCrossfading) {
            float t = Math.max(0.0f, Math.min(1.0f, crossfadeSpring.current));
            float easeT = t * t * (3.0f - 2.0f * t);
            float outAlpha = alpha * (1.0f - easeT);
            float inAlpha = alpha * easeT;

            if (outAlpha > 0.02f) {
                drawCompactState(canvas, previousIsland, curW, curH, outAlpha, iconCenterX, textCenterX, centerY, textAlign);
            }
            if (inAlpha > 0.02f) {
                drawCompactState(canvas, curType, curW, curH, inAlpha, iconCenterX, textCenterX, centerY, textAlign);
            }
        } else {
            drawCompactState(canvas, curType, curW, curH, alpha, iconCenterX, textCenterX, centerY, textAlign);
        }
    }

    private static void drawCompactState(Canvas canvas, int renderType, float curW, float curH, float alpha,
                                         float iconCenterX, float textCenterX, float centerY, Paint.Align textAlign) {
        int intAlpha = Math.min(255, Math.max(0, (int) (alpha * 255)));
        if (intAlpha <= 0) return;

        if (renderType == STATE_CHARGING) {
            paintAccentGreen.setAlpha(intAlpha);
            drawBoltIcon(canvas, iconCenterX, centerY, dpToPx(12), paintAccentGreen);

            paintTextPrimary.setTextSize(spToPx(12.5f));
            paintTextPrimary.setTextAlign(textAlign);
            paintTextPrimary.setColor(Color.parseColor("#34D399"));
            paintTextPrimary.setAlpha(intAlpha);
            canvas.drawText(batteryPct + "%", textCenterX, centerY + dpToPx(4.5f), paintTextPrimary);
            paintTextPrimary.setColor(Color.WHITE);

        } else if (renderType == STATE_MEDIA) {
            if (mediaShowPillArt && currentPillArt != null && !currentPillArt.isRecycled()) {
                float artThumbSize = dpToPx(22);
                float artLeft = iconCenterX - artThumbSize / 2.0f;
                float artTop = centerY - artThumbSize / 2.0f;
                artRectF.set(artLeft, artTop, artLeft + artThumbSize, artTop + artThumbSize);

                canvas.save();
                artClipPath.reset();
                if ("circle".equalsIgnoreCase(mediaArtStyle)) {
                    artClipPath.addCircle(artRectF.centerX(), artRectF.centerY(), artThumbSize / 2.0f, Path.Direction.CW);
                } else if ("squircle".equalsIgnoreCase(mediaArtStyle)) {
                    artClipPath.addRoundRect(artRectF, artThumbSize * 0.38f, artThumbSize * 0.38f, Path.Direction.CW);
                } else { // "rounded" default
                    artClipPath.addRoundRect(artRectF, dpToPx(4.5f), dpToPx(4.5f), Path.Direction.CW);
                }
                canvas.clipPath(artClipPath);
                paintArtBitmap.setAlpha(intAlpha);
                canvas.drawBitmap(currentPillArt, null, artRectF, paintArtBitmap);
                canvas.restore();
            } else {
                paintAccentCyan.setColor(Color.parseColor("#38BDF8"));
                paintAccentCyan.setAlpha(intAlpha);
                drawMusicNoteIcon(canvas, iconCenterX, centerY, dpToPx(11), paintAccentCyan);
            }

            if (mediaShowWaveform) {
                boolean isCutoutCenter = !"left".equalsIgnoreCase(pillAlignment) && !"right".equalsIgnoreCase(pillAlignment);
                float barsStart = isCutoutCenter ? (textCenterX - dpToPx(10)) : (textCenterX - dpToPx(20));
                renderAudioBars(canvas, barsStart, centerY, alpha);
            } else {
                paintTextPrimary.setTextSize(spToPx(11f));
                paintTextPrimary.setTextAlign(textAlign);
                paintTextPrimary.setColor(Color.WHITE);
                paintTextPrimary.setAlpha(intAlpha);
                canvas.drawText(truncate(mediaTitle, 10), textCenterX, centerY + dpToPx(4), paintTextPrimary);
            }

        } else if (renderType == STATE_VOLUME) {
            paintAccentCyan.setAlpha(intAlpha);
            drawSpeakerIcon(canvas, iconCenterX, centerY, dpToPx(12), volumePercent, paintAccentCyan);

            paintTextPrimary.setTextSize(spToPx(11.5f));
            paintTextPrimary.setTextAlign(textAlign);
            paintTextPrimary.setAlpha(intAlpha);
            canvas.drawText(volumePercent + "%", textCenterX, centerY + dpToPx(4), paintTextPrimary);

        } else if (renderType == STATE_RINGER) {
            paintAccentAmber.setAlpha(intAlpha);
            drawBellIcon(canvas, iconCenterX, centerY, dpToPx(12), ringerLabel, paintAccentAmber);

            paintTextPrimary.setTextSize(spToPx(11.5f));
            paintTextPrimary.setTextAlign(textAlign);
            paintTextPrimary.setAlpha(intAlpha);
            canvas.drawText(ringerLabel, textCenterX, centerY + dpToPx(4), paintTextPrimary);

        } else if (renderType == STATE_NOTIFICATION) {
            paintAccentAmber.setAlpha(intAlpha);
            drawMessageIcon(canvas, iconCenterX, centerY, dpToPx(11), paintAccentAmber);

            paintTextPrimary.setTextSize(spToPx(11f));
            paintTextPrimary.setTextAlign(textAlign);
            paintTextPrimary.setAlpha(intAlpha);
            canvas.drawText(truncate(notifAppName, 10), textCenterX, centerY + dpToPx(4), paintTextPrimary);

        } else if (renderType == STATE_TORCH) {
            paintAccentAmber.setAlpha(intAlpha);
            drawTorchIcon(canvas, iconCenterX, centerY, dpToPx(11), paintAccentAmber);

            paintTextPrimary.setTextSize(spToPx(11.5f));
            paintTextPrimary.setTextAlign(textAlign);
            paintTextPrimary.setAlpha(intAlpha);
            canvas.drawText("Torch", textCenterX, centerY + dpToPx(4), paintTextPrimary);

        } else if (renderType == STATE_HYPERDL) {
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
            artRectF.set(bx, bTop, bx + barW, bBottom);
            canvas.drawRoundRect(artRectF, barW / 2f, barW / 2f, paintAccentCyan);
        }
    }

    private static void renderExpandedContent(Canvas canvas, float curW, float curH, float alpha) {
        int intAlpha = Math.min(255, Math.max(0, (int) (alpha * 255)));
        paintTextPrimary.setAlpha(intAlpha);
        paintTextSecondary.setAlpha(Math.min(255, (int) (alpha * 175)));
        paintTextTertiary.setAlpha(Math.min(255, (int) (alpha * 95)));
        paintIconFill.setAlpha(intAlpha);

        float effCutoutY = cutoutCenterY + yOffset;
        float holeRelY = (params != null) ? Math.max(0, effCutoutY - params.y) : dpToPx(16);
        float holeR = cutoutRadius;
        boolean isCutoutCenter = !"left".equalsIgnoreCase(pillAlignment) && !"right".equalsIgnoreCase(pillAlignment);
        boolean isFloatingBelow = !"cover".equalsIgnoreCase(cardPositionMode) && !notchMode;
        float baseTopY = isFloatingBelow ? dpToPx(16) : (isCutoutCenter ? Math.max(dpToPx(24), holeRelY + holeR + dpToPx(6)) : dpToPx(20));

        int renderType = (activeIslandType != STATE_IDLE ? activeIslandType : currentIsland);

        if (renderType == STATE_CHARGING) {
            // Elegant Native Layout: Safely clear of camera punch-hole
            float topY = isFloatingBelow ? dpToPx(16) : (isCutoutCenter ? Math.max(dpToPx(38), holeRelY + holeR + dpToPx(10)) : dpToPx(24));

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

        } else if (renderType == STATE_MEDIA) {
            float artSize = dpToPx(48);
            float artLeft = dpToPx(28);
            float artTop = isFloatingBelow ? dpToPx(20) : Math.max(dpToPx(20), baseTopY);
            float curR = (springR != null) ? springR.current : dpToPx(24);

            // 1. Dynamic Ambient Color Glow
            if (mediaAmbientGlow && mediaDominantColor != Color.TRANSPARENT) {
                int glowAlpha = (int) (255 * (mediaGlowOpacity / 100f) * alpha);
                if (glowAlpha > 0) {
                    float glowRadius = curW * 0.75f;
                    if (cachedGlowGradient == null || cachedGlowColor != mediaDominantColor || Math.abs(cachedGlowW - curW) > 1f) {
                        cachedGlowColor = mediaDominantColor;
                        cachedGlowW = curW;
                        cachedGlowGradient = new RadialGradient(
                            artLeft + artSize * 0.5f,
                            artTop + artSize * 0.5f,
                            glowRadius,
                            mediaDominantColor,
                            Color.TRANSPARENT,
                            Shader.TileMode.CLAMP
                        );
                    }
                    paintAmbientGlow.setShader(cachedGlowGradient);
                    paintAmbientGlow.setAlpha(glowAlpha);
                    tempRectF.set(0, 0, curW, curH);
                    canvas.drawRoundRect(tempRectF, curR, curR, paintAmbientGlow);
                    paintAmbientGlow.setShader(null);
                }
            }

            // 2. Shared Element Album Art (48dp, 11dp rounded corners, safe internal padding)
            artRectF.set(artLeft, artTop, artLeft + artSize, artTop + artSize);
            if (currentCardArt != null && !currentCardArt.isRecycled()) {
                canvas.save();
                artClipPath.reset();
                artClipPath.addRoundRect(artRectF, dpToPx(11), dpToPx(11), Path.Direction.CW);
                canvas.clipPath(artClipPath);
                paintArtBitmap.setAlpha(intAlpha);
                canvas.drawBitmap(currentCardArt, null, artRectF, paintArtBitmap);
                canvas.restore();
            } else {
                int discColor = (mediaDominantColor != Color.TRANSPARENT) ? mediaDominantColor : Color.parseColor("#38BDF8");
                paintAccentCyan.setColor(discColor);
                paintAccentCyan.setAlpha(Math.min(255, (int) (alpha * 38)));
                canvas.drawRoundRect(artRectF, dpToPx(11), dpToPx(11), paintAccentCyan);

                paintAccentCyan.setAlpha(intAlpha);
                canvas.drawCircle(artLeft + artSize / 2f, artTop + artSize / 2f, dpToPx(10), paintAccentCyan);
                paintOledBlack.setAlpha(intAlpha);
                canvas.drawCircle(artLeft + artSize / 2f, artTop + artSize / 2f, dpToPx(4), paintOledBlack);
            }

            // 3. Track Title & Artist with Horizontal Fading Edge Marquee
            float textLeft = artLeft + artSize + dpToPx(14);
            float textRight = curW - dpToPx(22);
            float maxTextW = textRight - textLeft;

            paintTextPrimary.setTextSize(spToPx(14));
            paintTextPrimary.setTextAlign(Paint.Align.LEFT);
            paintTextPrimary.setColor(Color.WHITE);
            paintTextPrimary.setAlpha(intAlpha);

            float titleW = paintTextPrimary.measureText(mediaTitle);
            if (mediaMarquee && titleW > maxTextW) {
                float gap = dpToPx(36);
                float span = titleW + gap;
                float offset = (SystemClock.uptimeMillis() / 25f) % span;

                marqueeBounds.set(textLeft, artTop, textRight, artTop + dpToPx(24));
                canvas.saveLayer(marqueeBounds, null);
                canvas.drawText(mediaTitle, textLeft - offset, artTop + dpToPx(18), paintTextPrimary);
                if (offset > gap) {
                    canvas.drawText(mediaTitle, textLeft - offset + span, artTop + dpToPx(18), paintTextPrimary);
                }

                // Horizontal fading edges (16dp length to match HyperOS marquee)
                float fadeLen = dpToPx(16);
                float totalW = textRight - textLeft;
                if (cachedMarqueeGradient == null || Math.abs(cachedMarqueeLeft - textLeft) > 1f || Math.abs(cachedMarqueeRight - textRight) > 1f) {
                    cachedMarqueeLeft = textLeft;
                    cachedMarqueeRight = textRight;
                    float pLeft = Math.min(0.25f, fadeLen / totalW);
                    float pRight = Math.max(0.75f, 1.0f - (fadeLen / totalW));
                    cachedMarqueeGradient = new LinearGradient(
                        textLeft, 0, textRight, 0,
                        new int[]{0x00FFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0x00FFFFFF},
                        new float[]{0.0f, pLeft, pRight, 1.0f},
                        Shader.TileMode.CLAMP
                    );
                    paintFadeMask.setShader(cachedMarqueeGradient);
                }
                canvas.drawRect(marqueeBounds, paintFadeMask);
                canvas.restore();
            } else {
                canvas.drawText(truncate(mediaTitle, 22), textLeft, artTop + dpToPx(18), paintTextPrimary);
            }

            paintTextSecondary.setTextSize(spToPx(12));
            paintTextSecondary.setTextAlign(Paint.Align.LEFT);
            paintTextSecondary.setColor(Color.WHITE);
            paintTextSecondary.setAlpha(Math.min(255, (int) (alpha * 175)));
            canvas.drawText(truncate(mediaArtist.isEmpty() ? "Media Playback" : mediaArtist, 24), textLeft, artTop + dpToPx(36), paintTextSecondary);

            // Calculate card expansion progress to synchronize seekbar & controls transition timing
            float compactH = (customPillHeight > 0) ? dpToPx(customPillHeight) : Math.max(Math.round(cutoutRadius * 2.0f), dpToPx(34));
            float targetCardH = getDefaultCardHeight(currentIsland);
            float expandProgress = Math.max(0.0f, Math.min(1.0f, (curH - compactH) / Math.max(1.0f, targetCardH - compactH)));

            // Seekbar alpha starts at 50% expansion progress and reaches full opacity exactly when the card spring settles
            float seekbarProgress = Math.max(0.0f, Math.min(1.0f, (expandProgress - 0.50f) / 0.50f));
            float seekbarEase = seekbarProgress * seekbarProgress * (3.0f - 2.0f * seekbarProgress);
            float seekbarAlpha = alpha * seekbarEase;
            int seekbarIntAlpha = Math.min(255, Math.max(0, (int) (seekbarAlpha * 255)));

            if (seekbarAlpha > 0.02f) {
                // 4. Interactive Progress Seekbar & Time labels
                float barY = artTop + artSize + dpToPx(14);
                float barLeft = dpToPx(22);
                float barW = curW - dpToPx(44);
                float barH = dpToPx(3.5f);

                long curPos = mediaTrackPosition;
                if (isMediaPlaying && mediaPositionUpdateTime > 0) {
                    long elapsed = SystemClock.elapsedRealtime() - mediaPositionUpdateTime;
                    if (elapsed > 0) curPos += elapsed;
                }
                if (mediaTrackDuration > 0 && curPos > mediaTrackDuration) curPos = mediaTrackDuration;
                float progressFraction = (mediaTrackDuration > 0) ? Math.max(0f, Math.min(1f, (float) curPos / (float) mediaTrackDuration)) : 0f;

                paintTrack.setAlpha(Math.min(255, (int) (seekbarAlpha * 45)));
                artRectF.set(barLeft, barY, barLeft + barW, barY + barH);
                canvas.drawRoundRect(artRectF, barH / 2f, barH / 2f, paintTrack);

                int progColor = (mediaDominantColor != Color.TRANSPARENT) ? mediaDominantColor : Color.parseColor("#38BDF8");
                paintProgress.setColor(progColor);
                paintProgress.setAlpha(seekbarIntAlpha);
                artRectF.set(barLeft, barY, barLeft + (barW * progressFraction), barY + barH);
                canvas.drawRoundRect(artRectF, barH / 2f, barH / 2f, paintProgress);

                // Stabilized progress thumb: render thumb at alpha 1.0 only once playback position is stabilized and card is fully expanded
                boolean isPositionStabilized = (mediaTrackDuration > 0 || !isMediaPlaying) && mediaPositionUpdateTime > 0;
                float thumbExpandFactor = Math.max(0.0f, Math.min(1.0f, (expandProgress - 0.85f) / 0.15f));
                float thumbAlpha = isPositionStabilized ? (seekbarAlpha * thumbExpandFactor * thumbExpandFactor) : 0f;
                int thumbIntAlpha = Math.min(255, Math.max(0, (int) (thumbAlpha * 255)));
                if (thumbIntAlpha > 0 && mediaTrackDuration > 0) {
                    paintProgress.setAlpha(thumbIntAlpha);
                    canvas.drawCircle(barLeft + (barW * progressFraction), barY + barH / 2f, dpToPx(4.5f), paintProgress);
                }

                if (mediaTrackDuration > 0) {
                    paintTextTertiary.setTextSize(spToPx(10));
                    paintTextTertiary.setTextAlign(Paint.Align.LEFT);
                    paintTextTertiary.setAlpha(Math.min(255, (int) (seekbarAlpha * 140)));
                    canvas.drawText(formatTimeMs(curPos), barLeft, barY + dpToPx(13), paintTextTertiary);

                    paintTextTertiary.setTextAlign(Paint.Align.RIGHT);
                    canvas.drawText("-" + formatTimeMs(Math.max(0, mediaTrackDuration - curPos)), barLeft + barW, barY + dpToPx(13), paintTextTertiary);
                }

                // 5. Playback Transport Controls
                float btnY = curH - dpToPx(22);
                float centerX = curW / 2.0f;
                float prevBtnX = centerX - dpToPx(65);
                float nextBtnX = centerX + dpToPx(65);

                paintIconFill.setAlpha(seekbarIntAlpha);
                drawPrevIcon(canvas, prevBtnX, btnY, dpToPx(13), paintIconFill);
                if (isMediaPlaying) {
                    drawPauseIcon(canvas, centerX, btnY, dpToPx(13), paintIconFill);
                } else {
                    drawPlayIcon(canvas, centerX, btnY, dpToPx(13), paintIconFill);
                }
                drawNextIcon(canvas, nextBtnX, btnY, dpToPx(13), paintIconFill);
            }

        } else if (renderType == STATE_VOLUME) {
            float topY = baseTopY;

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

        } else if (renderType == STATE_RINGER) {
            float topY = baseTopY;

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

        } else if (renderType == STATE_NOTIFICATION) {
            float topY = baseTopY;

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

        } else if (renderType == STATE_TORCH) {
            float topY = baseTopY;

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

        } else if (renderType == STATE_HYPERDL) {
            float topY = baseTopY;

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

    private static void drawMusicNoteIcon(Canvas canvas, float cx, float cy, float size, Paint paint) {
        float r = size * 0.20f;
        // Two note heads
        canvas.drawCircle(cx - size * 0.22f, cy + size * 0.22f, r, paint);
        canvas.drawCircle(cx + size * 0.22f, cy + size * 0.10f, r, paint);

        Paint strokeP = new Paint(paint);
        strokeP.setStyle(Paint.Style.STROKE);
        strokeP.setStrokeWidth(dpToPx(1.3f));
        // Stems
        canvas.drawLine(cx - size * 0.22f + r, cy + size * 0.22f, cx - size * 0.22f + r, cy - size * 0.35f, strokeP);
        canvas.drawLine(cx + size * 0.22f + r, cy + size * 0.10f, cx + size * 0.22f + r, cy - size * 0.47f, strokeP);
        // Beam
        strokeP.setStrokeWidth(dpToPx(2.0f));
        canvas.drawLine(cx - size * 0.22f + r, cy - size * 0.33f, cx + size * 0.22f + r, cy - size * 0.45f, strokeP);
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

            if (hideInLandscape && isLandscape() && currentIsland != STATE_CALIBRATION) {
                checkDisplayOrientation();
                isLoopRunning = false;
                return;
            }

            float dt = (frameTimeNanos - lastFrameNanos) * 1e-9f;
            lastFrameNanos = frameTimeNanos;
            if (dt <= 0f || dt > 0.04f) dt = 0.016f;

            long now = SystemClock.uptimeMillis();

            checkDisplayOrientation();
            resolveTargetState(now);

            boolean anyMoving = false;
            boolean isCardMorphing = morphSpring != null && (!morphSpring.isAtEquilibrium() || (isExpanded && morphSpring.current < 0.999f) || (!isExpanded && morphSpring.current > 0.0005f));

            if (isCardMorphing && currentIsland != STATE_IDLE && currentIsland != STATE_CALIBRATION && !isCollapsing) {
                boolean mMov = morphSpring.update(dt);
                float t = Math.max(0.0f, morphSpring.current);

                float compactW = (customPillWidth > 0) ? dpToPx(customPillWidth) : getDefaultPillWidth(currentIsland);
                float compactH = (customPillHeight > 0) ? dpToPx(customPillHeight) : Math.max(Math.round(cutoutRadius * 2.0f), dpToPx(34));
                float compactR = compactH / 2.0f;

                float defaultCardW = (customCardWidth > 0) ? dpToPx(customCardWidth) : dpToPx(320);
                float cardW = Math.min(displayWidthPx - dpToPx(16), defaultCardW);
                float cardH = getDefaultCardHeight(currentIsland);
                float cardR = dpToPx(cardRadius > 0 ? cardRadius : 24);

                // Unified single-phase morphing: width, height, and corner radius driven synchronously by progress t
                springW.current = compactW + (cardW - compactW) * t;
                springH.current = compactH + (cardH - compactH) * t;
                springR.current = compactR + (cardR - compactR) * t;

                springW.target = isExpanded ? cardW : compactW;
                springH.target = isExpanded ? cardH : compactH;
                springR.target = isExpanded ? cardR : compactR;

                springW.velocity = (cardW - compactW) * morphSpring.velocity;
                springH.velocity = (cardH - compactH) * morphSpring.velocity;
                springR.velocity = (cardR - compactR) * morphSpring.velocity;

                boolean saMoving = springContentAlpha.update(dt);
                anyMoving = mMov || saMoving;

                if (!mMov && !isExpanded && morphSpring.isAtEquilibrium() && morphSpring.current <= 0.0005f) {
                    prepareWindowForTarget();
                }
            } else {
                boolean swMoving = springW.update(dt);
                boolean shMoving = springH.update(dt);
                boolean srMoving = springR.update(dt);
                boolean saMoving = springContentAlpha.update(dt);
                anyMoving = swMoving || shMoving || srMoving || saMoving;
            }

            boolean cfMoving = false;
            if (crossfadeSpring != null && crossfadeSpring.isMoving()) {
                cfMoving = crossfadeSpring.update(dt);
                if (!cfMoving || crossfadeSpring.isAtEquilibrium()) {
                    previousIsland = STATE_IDLE;
                }
            }
            anyMoving = anyMoving || cfMoving;

            if (currentIsland == STATE_MEDIA && isMediaPlaying && !isExpanded && mediaShowWaveform && (now - lastWaveStep > 60)) {
                stepAudioBars();
                lastWaveStep = now;
            }

            if (ringView != null) ringView.invalidate();

            if (now - lastStatusPersist > 2500) {
                persistStatusAsync();
                lastStatusPersist = now;
            }

            // Continue loop only if springs are still in motion or media audio bars / marquee / seekbar are animating
            boolean mediaNeedsAnim = currentIsland == STATE_MEDIA && isMediaPlaying && !isCollapsing && ((!isExpanded && mediaShowWaveform) || isExpanded);
            boolean marqueeNeedsAnim = isExpanded && currentIsland == STATE_MEDIA && mediaMarquee && !isCollapsing;
            boolean needNextFrame = anyMoving || mediaNeedsAnim;

            if (needNextFrame) {
                if (mediaNeedsAnim && !anyMoving) {
                    // Throttle updates: ~30fps for expanded marquee, ~11fps for compact audio bars, 1fps for static seekbar
                    int throttleDelay = marqueeNeedsAnim ? 33 : (isExpanded ? 1000 : 90);
                    if (handler != null) {
                        handler.removeCallbacks(audioThrottleRunnable);
                        handler.postDelayed(audioThrottleRunnable, throttleDelay);
                    }
                } else {
                    if (handler != null) handler.removeCallbacks(audioThrottleRunnable);
                    Choreographer.getInstance().postFrameCallback(this);
                }
            } else {
                isLoopRunning = false;
                if (handler != null) handler.removeCallbacks(audioThrottleRunnable);
                if (ringView != null && ringView.getLayerType() != View.LAYER_TYPE_NONE) {
                    ringView.setLayerType(View.LAYER_TYPE_NONE, null);
                }
                if (isCollapsing) {
                    onAnimationSettled();
                }
            }
        }
    };

    private static final Runnable audioThrottleRunnable = new Runnable() {
        @Override
        public void run() {
            if (isLoopRunning && checkScreenInteractive()) {
                Choreographer.getInstance().postFrameCallback(vsyncCallback);
            } else {
                isLoopRunning = false;
            }
        }
    };

    private static final Runnable hunUntuckRunnable = new Runnable() {
        @Override
        public void run() {
            isHUNTucked = false;
            if (currentIsland != STATE_IDLE || isMediaPlaying || isHyperDLActive) {
                resolveTargetState(SystemClock.uptimeMillis());
                prepareWindowForTarget();
                wakeEngineLoop();
            }
        }
    };

    private static void tuckForHUN(final long durationMs) {
        if (handler == null) return;
        handler.post(new Runnable() {
            @Override
            public void run() {
                isHUNTucked = true;
                if (handler != null) {
                    handler.removeCallbacks(hunUntuckRunnable);
                    handler.postDelayed(hunUntuckRunnable, durationMs > 0 ? durationMs : 4500);
                }
                if (isExpanded) {
                    isExpanded = false;
                }
                if (springContentAlpha != null) {
                    springContentAlpha.setTarget(0.0f);
                }
                if (springW != null && springH != null) {
                    float initD = cutoutRadius * 2.0f;
                    springW.setTarget(initD);
                    springH.setTarget(initD);
                }
                prepareWindowForTarget();
                wakeEngineLoop();
            }
        });
    }

    private static final Runnable autoCollapseRunnable = new Runnable() {
        @Override
        public void run() {
            if (!previewLock && currentIsland != STATE_IDLE && !isCollapsing) {
                if (isExpanded) {
                    collapseCard();
                    if (handler != null && expandTimeoutMs > 0) handler.postDelayed(this, expandTimeoutMs);
                } else if (currentIsland == STATE_VOLUME && isMediaPlaying && enableMedia) {
                    // Smoothly morph cross-fade back to active media pill instead of collapsing into hole and reopening
                    showIsland(STATE_MEDIA, 0);
                } else {
                    startCollapse();
                }
            }
        }
    };

    private static boolean isLandscape() {
        try {
            if (defaultDisplay != null) {
                int rot = defaultDisplay.getRotation();
                if (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270) {
                    return true;
                }
                DisplayMetrics dm = new DisplayMetrics();
                defaultDisplay.getRealMetrics(dm);
                if (dm.widthPixels > dm.heightPixels) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static void showIsland(final int state, final long timeoutMs) {
        if (!masterEnabled && state != STATE_CALIBRATION) return;
        if (hideInLandscape && isLandscape() && state != STATE_CALIBRATION) return;
        if (isHUNTucked && state != STATE_CALIBRATION) return;
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (hideInLandscape && isLandscape() && state != STATE_CALIBRATION) return;
                if (isHUNTucked && state != STATE_CALIBRATION) return;

                boolean wakingFromIdle = (currentIsland == STATE_IDLE || isCollapsing);
                int oldState = currentIsland;
                if (!wakingFromIdle && oldState != state && oldState != STATE_IDLE) {
                    previousIsland = oldState;
                    if (crossfadeSpring != null) {
                        crossfadeSpring.snapTo(0.0f);
                        crossfadeSpring.setParameters(480f, 0.92f); // fast morph cross-fade (~160ms)
                        crossfadeSpring.setTarget(1.0f);
                    }
                } else if (wakingFromIdle) {
                    previousIsland = STATE_IDLE;
                    if (crossfadeSpring != null) {
                        crossfadeSpring.snapTo(1.0f);
                    }
                }

                if (state != STATE_CALIBRATION && timeoutMs > 0) {
                    previewLock = false;
                }
                currentIsland = state;
                activeIslandType = state;
                isCollapsing = false;
                isExpanded = false;

                if (handler != null) handler.removeCallbacks(autoCollapseRunnable);
                if (timeoutMs > 0 && !previewLock) {
                    if (handler != null) handler.postDelayed(autoCollapseRunnable, timeoutMs);
                }

                if (morphSpring != null) {
                    morphSpring.snapTo(0.0f);
                }

                if (springW != null) {
                    springW.setParameters(springStiffness, springDamping);
                    springH.setParameters(springStiffness, springDamping);
                    springR.setParameters(springStiffness, springDamping);
                    springContentAlpha.setParameters(springStiffness, springDamping);

                    // Eliminates hardcoded dark rectangular placeholder on volume or wake from idle
                    if (wakingFromIdle) {
                        float initD = cutoutRadius * 2.0f;
                        springW.snapTo(initD);
                        springH.snapTo(initD);
                        springR.snapTo(cutoutRadius);
                        springContentAlpha.snapTo(0.0f);
                    }
                }

                resolveTargetState(SystemClock.uptimeMillis());
                prepareWindowForTarget();
                if (ringView != null && ringView.getVisibility() != View.VISIBLE) {
                    ringView.setVisibility(View.VISIBLE);
                }

                wakeEngineLoop();
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else if (handler != null) {
            handler.post(r);
        }
    }

    public static void startCollapse() {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (handler != null) {
                    handler.removeCallbacks(autoCollapseRunnable);
                    handler.removeCallbacks(audioThrottleRunnable);
                }
                if (isCollapsing || currentIsland == STATE_IDLE) return;
                if (!isExpanded && currentIsland == STATE_VOLUME && isMediaPlaying && enableMedia) {
                    // Smoothly morph cross-fade back to active media pill instead of collapsing into hole and reopening
                    showIsland(STATE_MEDIA, 0);
                    return;
                }
                previewLock = false;
                isCollapsing = true;
                isExpanded = false;

                if (morphSpring != null) {
                    morphSpring.snapTo(0.0f);
                }

                float initD = cutoutRadius * 2.0f;
                if (springW != null) {
                    springW.setTarget(initD);
                    springH.setTarget(initD);
                    springR.setTarget(cutoutRadius);
                    springContentAlpha.setTarget(0.0f);

                    // Snappy suction collapse into camera cutout (~220ms, critically damped with zero overshoot)
                    springContentAlpha.setParameters(680f, 1.05f);
                    springW.setParameters(520f, 0.96f);
                    springH.setParameters(540f, 0.96f);
                    springR.setParameters(540f, 0.98f);
                }

                if (checkScreenInteractive()) {
                    isLoopRunning = true;
                    lastFrameNanos = System.nanoTime();
                    Choreographer.getInstance().removeFrameCallback(vsyncCallback);
                    Choreographer.getInstance().postFrameCallback(vsyncCallback);
                }
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else if (handler != null) {
            handler.post(r);
        }
    }

    public static void wakeEngineLoop() {
        if (handler == null) return;
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (checkScreenInteractive()) {
                    if (handler != null) handler.removeCallbacks(audioThrottleRunnable);
                    lastFrameNanos = System.nanoTime();
                    resolveTargetState(SystemClock.uptimeMillis());
                    prepareWindowForTarget();
                    if (ringView != null && ringView.getLayerType() != View.LAYER_TYPE_HARDWARE) {
                        ringView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                    }
                    isLoopRunning = true;
                    Choreographer.getInstance().removeFrameCallback(vsyncCallback);
                    Choreographer.getInstance().postFrameCallback(vsyncCallback);
                }
            }
        });
    }

    private static void checkDisplayOrientation() {
        if (defaultDisplay == null || ringView == null) return;
        if (hideInLandscape && isLandscape() && currentIsland != STATE_CALIBRATION) {
            if (ringView.getVisibility() != View.GONE) {
                ringView.setVisibility(View.GONE);
                if (isExpanded) isExpanded = false;
            }
            if (params != null && windowManager != null) {
                if (params.width > 1 || params.height > 1 || (params.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0) {
                    params.width = 1;
                    params.height = 1;
                    params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    try { windowManager.updateViewLayout(ringView, params); } catch (Exception ignored) {}
                }
            }
        }
    }

    private static void resolveTargetState(long now) {
        if (!previewLock) {
            if (currentIsland == STATE_CALIBRATION && calibrationEndTime > 0 && now > calibrationEndTime) {
                startCollapse();
            }
        }

        if (isCollapsing) {
            // Target is already locked to cutout circle by startCollapse()
            return;
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

            if (!stealthRingIdle && springContentAlpha.current < 0.02f && Math.abs(springW.current - targetW) < 1f) {
                if (ringView != null && ringView.getVisibility() != View.GONE) {
                    ringView.setVisibility(View.GONE);
                }
            }
        } else if (currentIsland == STATE_CALIBRATION) {
            if (ringView != null && ringView.getVisibility() != View.VISIBLE) {
                ringView.setVisibility(View.VISIBLE);
            }
            targetW = Math.max(cutoutRadius * 2.4f, dpToPx(72));
            targetH = targetW;
            targetR = targetW / 2.0f;
            targetContentAlpha = 1.0f;

        } else if (!isExpanded) {
            // Compact pill
            if (ringView != null && ringView.getVisibility() != View.VISIBLE) {
                ringView.setVisibility(View.VISIBLE);
            }

            int baseH = (customPillHeight > 0) ? dpToPx(customPillHeight) : Math.max(Math.round(cutoutRadius * 2.0f), dpToPx(34));
            targetH = baseH;
            targetR = targetH / 2.0f;
            targetContentAlpha = 1.0f;
            int defaultW = getDefaultPillWidth(currentIsland);
            targetW = (customPillWidth > 0) ? dpToPx(customPillWidth) : defaultW;

        } else {
            // Expanded card
            if (ringView != null && ringView.getVisibility() != View.VISIBLE) {
                ringView.setVisibility(View.VISIBLE);
            }

            int defaultCardW = (customCardWidth > 0) ? dpToPx(customCardWidth) : dpToPx(320);
            targetW = Math.min(displayWidthPx - dpToPx(16), defaultCardW);
            targetR = dpToPx(cardRadius > 0 ? cardRadius : 24);
            targetContentAlpha = 1.0f;
            targetH = getDefaultCardHeight(currentIsland);
        }

        springW.setTarget(targetW);
        springH.setTarget(targetH);
        springR.setTarget(targetR);
        springContentAlpha.setTarget(targetContentAlpha);
    }

    private static int getDefaultPillWidth(int state) {
        switch (state) {
            case STATE_CHARGING:     return dpToPx(138);
            case STATE_MEDIA:        return dpToPx(148);
            case STATE_VOLUME:       return dpToPx(144);
            case STATE_RINGER:       return dpToPx(136);
            case STATE_NOTIFICATION: return dpToPx(150);
            case STATE_TORCH:        return dpToPx(132);
            case STATE_HYPERDL:      return dpToPx(142);
            default:                 return dpToPx(140);
        }
    }

    private static int getDefaultCardHeight(int state) {
        if (customCardHeight > 0) return dpToPx(customCardHeight);
        switch (state) {
            case STATE_MEDIA:    return dpToPx(138);
            case STATE_CHARGING: return dpToPx(116);
            case STATE_VOLUME:   return dpToPx(92);
            default:             return dpToPx(100);
        }
    }

    /**
     * Pre-allocates window bounds to contain upcoming animations.
     * Keeps surface sizing stable and coordinates centered over punch hole.
     */
    private static void prepareWindowForTarget() {
        if (params == null || windowManager == null || ringView == null) return;

        float effCutoutX;
        float effCutoutY;
        if (isLandscape()) {
            int rot = (defaultDisplay != null) ? defaultDisplay.getRotation() : Surface.ROTATION_90;
            if (rot == Surface.ROTATION_270) {
                effCutoutX = (displayWidthPx - cutoutCenterY) + xOffset;
            } else {
                effCutoutX = cutoutCenterY + xOffset;
            }
            effCutoutY = (displayHeightPx / 2.0f) + yOffset;
        } else {
            effCutoutX = cutoutCenterX + xOffset;
            effCutoutY = cutoutCenterY + yOffset;
        }
        if (effCutoutX <= 0 && displayWidthPx > 0) {
            effCutoutX = displayWidthPx / 2.0f;
        }

        int compactH = (customPillHeight > 0) ? dpToPx(customPillHeight) : Math.max(Math.round(cutoutRadius * 2.0f), dpToPx(34));
        int topAnchorY = notchMode ? Math.max(0, yOffset) : Math.round(effCutoutY - (compactH / 2.0f));

        if (hideInLandscape && isLandscape() && currentIsland != STATE_CALIBRATION) {
            if (ringView.getVisibility() != View.GONE) {
                ringView.setVisibility(View.GONE);
            }
            if (isExpanded) isExpanded = false;
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

        if (isHUNTucked && currentIsland != STATE_CALIBRATION) {
            if (ringView.getVisibility() != View.GONE) {
                ringView.setVisibility(View.GONE);
            }
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

        if (currentIsland == STATE_IDLE && !isCollapsing) {
            targetFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            if (ringView.getVisibility() != View.GONE) {
                ringView.setVisibility(View.GONE);
            }
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

        boolean isMorphFlight = morphSpring != null && (!morphSpring.isAtEquilibrium() || morphSpring.current > 0.0005f);

        if (currentIsland == STATE_CALIBRATION) {
            reqW = Math.round(Math.max(cutoutRadius * 3.0f, dpToPx(72)));
            reqH = reqW;
            reqY = Math.round(effCutoutY - (reqH / 2.0f));
        } else if (isExpanded || isMorphFlight) {
            int defaultCardW = (customCardWidth > 0) ? dpToPx(customCardWidth) : dpToPx(320);
            reqW = Math.min(displayWidthPx - dpToPx(16), defaultCardW);
            int rawCardH = (customCardHeight > 0) ? dpToPx(customCardHeight) : getDefaultCardHeight(currentIsland);
            reqH = Math.min(displayHeightPx - topAnchorY - dpToPx(16), rawCardH);
            reqY = topAnchorY;
        } else {
            int defaultW = getDefaultPillWidth(currentIsland);
            reqW = (customPillWidth > 0) ? dpToPx(customPillWidth) : defaultW;
            reqH = compactH;
            reqY = topAnchorY;
        }

        int newW = reqW;
        int newH = reqH;

        int targetGravity = Gravity.TOP | Gravity.START;
        int targetX;
        if ("left".equalsIgnoreCase(pillAlignment)) {
            targetX = Math.round(effCutoutX - (newW / 2.0f));
        } else if ("right".equalsIgnoreCase(pillAlignment)) {
            targetX = Math.round(displayWidthPx - (effCutoutX + (newW / 2.0f)));
        } else if ("freeform".equalsIgnoreCase(pillAlignment)) {
            targetX = Math.round(effCutoutX - (newW / 2.0f));
        } else {
            // Strictly clamped to physical camera cutout center: effCutoutX - half-width
            targetX = Math.round(effCutoutX - (newW / 2.0f));
        }

        // Under no circumstances should targetX reset to 0 or re-evaluate empty
        if (targetX <= 0 && effCutoutX > 0 && displayWidthPx > newW) {
            targetX = Math.max(0, Math.round(effCutoutX - (newW / 2.0f)));
        }
        if (displayWidthPx > newW) {
            targetX = Math.max(0, Math.min(displayWidthPx - newW, targetX));
        }

        boolean changed = false;
        if (params.width != newW || params.height != newH || params.y != reqY || params.flags != targetFlags) {
            params.width = newW;
            params.height = newH;
            params.y = reqY;
            params.flags = targetFlags;
            changed = true;
        }

        if (params.gravity != targetGravity || params.x != targetX) {
            params.gravity = targetGravity;
            params.x = targetX;
            changed = true;
        }

        if (params.windowAnimations != 0) {
            params.windowAnimations = 0;
            changed = true;
        }
        try {
            java.lang.reflect.Field pf = WindowManager.LayoutParams.class.getField("privateFlags");
            int curPf = pf.getInt(params);
            if ((curPf & 0x00000040) == 0) {
                pf.setInt(params, curPf | 0x00000040); // PRIVATE_FLAG_NO_MOVE_ANIMATION
                changed = true;
            }
        } catch (Throwable ignored) {}

        if (ringView.getVisibility() != View.VISIBLE) {
            ringView.setVisibility(View.VISIBLE);
        }

        if (changed) {
            try { windowManager.updateViewLayout(ringView, params); } catch (Exception ignored) {}
        }
    }

    /**
     * Called once when all springs reach stationary equilibrium.
     * Completes collapse cleanly without surface resize flicker.
     */
    private static void onAnimationSettled() {
        if (params == null || windowManager == null || ringView == null) return;
        if (ringView.getLayerType() != View.LAYER_TYPE_NONE) {
            ringView.setLayerType(View.LAYER_TYPE_NONE, null);
        }

        if (isCollapsing || currentIsland == STATE_IDLE) {
            isCollapsing = false;
            isExpanded = false;
            if (isMediaPlaying && enableMedia) {
                showIsland(STATE_MEDIA, 0);
                return;
            } else if (isHyperDLActive && enableHyperDL) {
                showIsland(STATE_HYPERDL, 0);
                return;
            } else {
                currentIsland = STATE_IDLE;
                activeIslandType = STATE_IDLE;
                previewLock = false;
            }

            // Instantly hide view on idle settlement to avoid any surface transform artifacts
            ringView.setVisibility(View.GONE);
            params.width = 1;
            params.height = 1;
            float effCutoutX = cutoutCenterX + xOffset;
            params.x = Math.round(effCutoutX - 0.5f);
            params.gravity = Gravity.TOP | Gravity.START;
            params.windowAnimations = 0;
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            try { windowManager.updateViewLayout(ringView, params); } catch (Exception ignored) {}
            persistStatusAsync();
        } else {
            boolean isMorphFlight = morphSpring != null && (!morphSpring.isAtEquilibrium() || morphSpring.current > 0.0005f);
            if (!isExpanded && !isMorphFlight) {
                prepareWindowForTarget();
            } else if (isExpanded) {
                int reqW = Math.round(springW.current);
                int reqH = Math.round(springH.current);
                if (params.width != reqW || params.height != reqH) {
                    params.width = reqW;
                    params.height = reqH;
                    float effCutoutX = cutoutCenterX + xOffset;
                    params.x = Math.round(effCutoutX - (reqW / 2.0f));
                    params.gravity = Gravity.TOP | Gravity.START;
                    params.windowAnimations = 0;
                    try { windowManager.updateViewLayout(ringView, params); } catch (Exception ignored) {}
                }
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
        // Worker 1: Periodic hardware synchronization & ecosystem telemetry
        workerPool.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!checkScreenInteractive()) return;

                    if (new File(triggerPath).exists()) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                processTriggerCmd();
                            }
                        });
                    }

                    if (enableCharging) {
                        queryBatteryHardware();
                    }
                    if (enableMedia) {
                        long nowTick = SystemClock.uptimeMillis();
                        if (nowTick - lastMediaPoll > 4000) {
                            lastMediaPoll = nowTick;
                            queryMediaSessionNative();
                        }
                    }
                    if (enableTorch) {
                        queryTorchStatusNative();
                    }
                    if (enableVolume) {
                        queryVolumeFallback();
                    }
                    if (enableRinger) {
                        queryRingerFallback();
                    }
                    if (enableHyperDL) {
                        queryHyperDLStatusNative();
                    }
                    if (enableHyperCore && isCharging) {
                        readHyperCoreStatusNative();
                    }

                    // Landscape guard check
                    if (hideInLandscape && currentIsland != STATE_CALIBRATION) {
                        boolean land = isLandscape();
                        if (land) {
                            if (ringView != null && (ringView.getVisibility() == View.VISIBLE || (params != null && params.width > 1))) {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        prepareWindowForTarget();
                                    }
                                });
                            }
                            return;
                        } else if (!land && ringView != null && ringView.getVisibility() != View.VISIBLE && currentIsland != STATE_IDLE && masterEnabled) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    resolveTargetState(SystemClock.uptimeMillis());
                                    prepareWindowForTarget();
                                    if (ringView != null) ringView.setVisibility(View.VISIBLE);
                                    wakeEngineLoop();
                                }
                            });
                        }
                    }

                    // Auto state transitions (priority-based)
                    if (!masterEnabled || isCollapsing) {
                        return;
                    }
                    if (currentIsland == STATE_CHARGING && isCharging) {
                        // Maintain charging state
                    } else if (currentIsland == STATE_VOLUME || currentIsland == STATE_RINGER 
                            || currentIsland == STATE_TORCH || currentIsland == STATE_NOTIFICATION) {
                        // Transient states wait for their timeouts
                    } else if (isHyperDLActive && enableHyperDL) {
                        if (currentIsland != STATE_HYPERDL && currentIsland != STATE_CALIBRATION) {
                            showIsland(STATE_HYPERDL, 0);
                        }
                    } else if (isMediaPlaying && enableMedia) {
                        if ((currentIsland != STATE_MEDIA && currentIsland != STATE_CALIBRATION)
                                || (currentIsland == STATE_MEDIA && ringView != null && ringView.getVisibility() != View.VISIBLE && (!hideInLandscape || !isLandscape()))) {
                            showIsland(STATE_MEDIA, 0);
                        }
                    } else if (currentIsland != STATE_CHARGING && currentIsland != STATE_CALIBRATION) {
                        if (currentIsland != STATE_IDLE) {
                            startCollapse();
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }, 300, 1200, TimeUnit.MILLISECONDS);

        // Worker 2: Real-time event streaming via logcat (Volume, Media Keys, Notifications)
        workerPool.execute(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    java.lang.Process p = null;
                    try {
                        p = Runtime.getRuntime().exec(new String[]{
                                "logcat", "-b", "main", "-b", "system", "-b", "events", "-v", "brief"
                        });
                        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                        String line;
                        Pattern flagPattern = Pattern.compile("flags=0x([0-9a-fA-F]+)");

                        while ((line = reader.readLine()) != null) {
                            if (!masterEnabled || !checkScreenInteractive()) continue;

                            if (enableVolume && (line.contains("Volume controller visible: true")
                                    || line.contains("vol.MiuiVolumeDialog")
                                    || line.contains("MediaVolumeConr")
                                    || line.contains("dispatchVolumeKeyEvent"))) {
                                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                                if (am != null) {
                                    int stream = AudioManager.STREAM_MUSIC;
                                    int cur = am.getStreamVolume(stream);
                                    // Only show if music volume actually changed
                                    if (lastVolumeLevel != cur) {
                                        int max = am.getStreamMaxVolume(stream);
                                        volumePercent = Math.round((cur / (float) Math.max(1, max)) * 100f);
                                        lastVolumeLevel = cur;

                                        previewLock = false;
                                        if (currentIsland != STATE_CALIBRATION) {
                                            showIsland(STATE_VOLUME, 2000);
                                        }
                                    }
                                }
                            }

                            // Media Playback State Event Detection (targeted session events with 1500ms debounce)
                            if (enableMedia && (line.contains("MediaSessionRecord") || line.contains("updatePlaybackState") || line.contains("dispatchMediaKeyEvent"))) {
                                long nowTick = SystemClock.uptimeMillis();
                                if (nowTick - lastMediaPoll > 1500) {
                                    lastMediaPoll = nowTick;
                                    queryMediaSessionNative();
                                }
                            }

                            // Heads-Up Notification (HUN) banner alert detection & collision avoidance
                            if (line.contains("notification_alert") || line.contains("heads_up") || line.contains("sysui_heads_up")) {
                                tuckForHUN(4500);
                            }

                            // Screen recording & VirtualDisplay lifecycle resilience
                            if (line.contains("screenrecorder") || line.contains("ScreenRecorder") || line.contains("MediaProjection")) {
                                scheduleRebindRetry();
                            }

                            // Notification Event Detection — only match events log notification_enqueue
                            if (enableNotifications && line.contains("notification_enqueue")) {
                                // Parse: [uid,pid,pkg,id,tag,uid,Notification(... flags=0xNN ...),vis]
                                Matcher fm = flagPattern.matcher(line);
                                if (fm.find()) {
                                    try {
                                        int flags = Integer.parseInt(fm.group(1), 16);
                                        // Skip ongoing (0x02) and foreground service (0x40) notifications
                                        if ((flags & (0x02 | 0x40)) != 0) continue;
                                    } catch (Throwable ignored) {}
                                }

                                int startIdx = line.indexOf("[");
                                if (startIdx != -1) {
                                    String body = line.substring(startIdx + 1);
                                    String[] parts = body.split(",");
                                    if (parts.length >= 3) {
                                        String pkg = parts[2].trim();
                                        if (isUserFacingPackage(pkg)) {
                                            // Debounce: ignore same package within 3s
                                            long nowMs = SystemClock.uptimeMillis();
                                            if (nowMs - lastNotifTime > 3000 || !pkg.equals(lastNotifPkg)) {
                                                lastNotifTime = nowMs;
                                                lastNotifPkg = pkg;
                                                notifAppName = resolveFriendlyAppName(pkg);
                                                notifTitle = notifAppName;
                                                notifContent = "New notification";
                                                queryNotificationDetailsAsync(pkg);
                                                if (!previewLock && currentIsland != STATE_CALIBRATION) {
                                                    // If incoming notification has high-priority or alert flag, tuck out of the way
                                                    if (line.contains("alert=1") || line.contains("high") || line.contains("heads")) {
                                                        tuckForHUN(4500);
                                                    } else {
                                                        showIsland(STATE_NOTIFICATION, 3500);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        if (p != null) {
                            try { p.destroy(); } catch (Throwable ignored) {}
                        }
                    }
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {}
                }
            }
        });
    }

    private static String readFirstLineFromFile(String path) {
        File f = new File(path);
        if (!f.exists()) return null;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            return br.readLine();
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean checkScreenInteractive() {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                boolean active = pm.isInteractive();
                if (active != isScreenInteractive) {
                    isScreenInteractive = active;
                    if (active) {
                        lastFrameNanos = System.nanoTime();
                        wakeEngineLoop();
                    }
                }
                return active;
            }
        } catch (Throwable ignored) {}
        return true;
    }

    private static void initAudioTelemetry() {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                volumePercent = Math.round((cur / (float) Math.max(1, max)) * 100f);
                lastVolumeLevel = cur;

                lastRingerLevel = am.getRingerMode();
                if (lastRingerLevel == AudioManager.RINGER_MODE_SILENT) ringerLabel = "Silent";
                else if (lastRingerLevel == AudioManager.RINGER_MODE_VIBRATE) ringerLabel = "Vibrate";
                else ringerLabel = "Ring";
            }
        } catch (Throwable ignored) {}
    }

    private static void queryBatteryHardware() {
        try {
            long cap = readLongFromFile("/sys/class/power_supply/battery/capacity");
            if (cap > 0 && cap <= 100) {
                batteryPct = (int) cap;
            } else {
                long bmsCap = readLongFromFile("/sys/class/power_supply/bms/capacity");
                if (bmsCap > 0 && bmsCap <= 100) {
                    batteryPct = (int) bmsCap;
                }
            }

            String status = readFirstLineFromFile("/sys/class/power_supply/battery/status");
            long usbOnline = readLongFromFile("/sys/class/power_supply/usb/online");
            boolean chargingNow = (status != null && (status.equalsIgnoreCase("Charging") || status.equalsIgnoreCase("Full"))) || usbOnline == 1;

            if (chargingNow && !isCharging && enableCharging) {
                isCharging = true;
                readBatteryHardwareTelemetry();
                triggerChargingEvent();
            } else if (!chargingNow && isCharging) {
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
            } else if (chargingNow && isCharging) {
                readBatteryHardwareTelemetry();
                if (currentIsland == STATE_CHARGING) wakeEngineLoop();
            }
        } catch (Throwable ignored) {}
    }

    private static void queryTorchStatusNative() {
        try {
            boolean active = false;
            // 1. MediaTek sysfs fast path
            File mtkTorch = new File("/sys/devices/virtual/flashlight_core/flashlight/flashlight_torch");
            if (mtkTorch.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(mtkTorch))) {
                    br.readLine(); // skip header
                    String line = br.readLine();
                    if (line != null) {
                        String[] tok = line.trim().split("\\s+");
                        if (tok.length >= 4 && !"0".equals(tok[3])) {
                            active = true;
                        }
                    }
                } catch (Throwable ignored) {}
            } else {
                // 2. Generic sysfs nodes
                for (String p : new String[]{
                        "/sys/class/leds/flashlight/brightness",
                        "/sys/class/leds/torch-light/brightness",
                        "/sys/class/leds/torch-light0/brightness"}) {
                    File lf = new File(p);
                    if (lf.exists() && readLongFromFile(p) > 0) {
                        active = true;
                        break;
                    }
                }
            }

            if (active && !isTorchActive) {
                isTorchActive = true;
                if (!previewLock && currentIsland != STATE_CALIBRATION) {
                    showIsland(STATE_TORCH, 3000);
                }
            } else if (!active && isTorchActive) {
                isTorchActive = false;
                if (currentIsland == STATE_TORCH) {
                    startCollapse();
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void queryVolumeFallback() {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int stream = AudioManager.STREAM_MUSIC;
                int cur = am.getStreamVolume(stream);
                if (lastVolumeLevel != -1 && cur != lastVolumeLevel) {
                    int max = am.getStreamMaxVolume(stream);
                    volumePercent = Math.round((cur / (float) Math.max(1, max)) * 100f);
                    if (!previewLock && currentIsland != STATE_CALIBRATION) {
                        showIsland(STATE_VOLUME, 2000);
                    }
                }
                lastVolumeLevel = cur;
            }
        } catch (Throwable ignored) {}
    }

    private static void queryRingerFallback() {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int mode = am.getRingerMode();
                if (lastRingerLevel != -1 && mode != lastRingerLevel) {
                    if (mode == AudioManager.RINGER_MODE_SILENT) ringerLabel = "Silent";
                    else if (mode == AudioManager.RINGER_MODE_VIBRATE) ringerLabel = "Vibrate";
                    else ringerLabel = "Ring";

                    if (!previewLock && currentIsland != STATE_CALIBRATION) {
                        showIsland(STATE_RINGER, 2200);
                    }
                }
                lastRingerLevel = mode;
            }
        } catch (Throwable ignored) {}
    }

    private static boolean isUserFacingPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        String p = pkg.toLowerCase(Locale.US);
        // Block system and MIUI infrastructure packages
        if (p.equals("android")
                || p.startsWith("com.android.")
                || p.startsWith("com.miui.")
                || p.startsWith("com.xiaomi.")
                || p.startsWith("com.qualcomm.")
                || p.startsWith("com.mediatek.")
                || p.contains("systemui")
                || p.contains("misound")
                || p.contains("securitycenter")
                || p.contains("powerkeeper")
                || p.contains("googlequicksearchbox")
                || p.contains("gms")
                || p.contains("gsf")
                || p.contains("provider")
                || p.contains("launcher")
                || p.contains("inputmethod")
                || p.contains("keyboard")
                || p.contains("carrier")
                || p.contains("telephony")
                || p.contains("bluetooth")
                || p.contains("backup")
                || p.contains("overlay")
                || p.contains("hyperring")
                || p.contains("termux")) {
            return false;
        }
        return true;
    }

    private static String resolveFriendlyAppName(String pkg) {
        String p = pkg.toLowerCase(Locale.US);
        if (p.contains("whatsapp")) return "WhatsApp";
        if (p.contains("telegram")) return "Telegram";
        if (p.contains("instagram")) return "Instagram";
        if (p.contains("twitter") || p.contains("x.android")) return "X";
        if (p.contains("discord")) return "Discord";
        if (p.contains("youtube")) return "YouTube";
        if (p.contains("spotify")) return "Spotify";
        if (p.contains("tiktok") || p.contains("trill")) return "TikTok";
        if (p.contains("gmail") || p.contains("email")) return "Mail";
        if (p.contains("messaging") || p.contains("mms")) return "Messages";
        if (p.contains("reddit")) return "Reddit";
        if (p.contains("facebook") || p.contains("katana")) return "Facebook";
        if (p.contains("orca")) return "Messenger";
        if (p.contains("netflix")) return "Netflix";
        int dot = pkg.lastIndexOf(".");
        String name = dot != -1 ? pkg.substring(dot + 1) : pkg;
        if (name.length() > 0) {
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        return name;
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
        if (Looper.myLooper() != Looper.getMainLooper()) {
            queryMediaSessionInternal();
        } else if (backgroundHandler != null) {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    queryMediaSessionInternal();
                }
            });
        }
    }

    private static void queryMediaSessionInternal() {
        // High-fidelity Android Framework ISessionManager query (MediaController + High-Res Album Art + Seek/Duration)
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getServiceMethod = smClass.getMethod("getService", String.class);
            android.os.IBinder binder = (android.os.IBinder) getServiceMethod.invoke(null, "media_session");
            if (binder != null) {
                Class<?> stubClass = Class.forName("android.media.session.ISessionManager$Stub");
                Method asInterfaceMethod = stubClass.getMethod("asInterface", android.os.IBinder.class);
                Object ism = asInterfaceMethod.invoke(null, binder);
                if (ism != null) {
                    Method getSessionsMethod = ism.getClass().getMethod("getSessions", android.content.ComponentName.class, int.class);
                    List<?> tokens = (List<?>) getSessionsMethod.invoke(ism, null, 0);
                    if (tokens != null && !tokens.isEmpty()) {
                        MediaController chosen = null;
                        for (Object item : tokens) {
                            if (item instanceof MediaSession.Token) {
                                MediaController mc = new MediaController(context != null ? context : sysContext, (MediaSession.Token) item);
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

        // Fallback: dumpsys media_session
        queryMediaSessionDumpsysFallback();
    }

    private static void processMediaController(MediaController mc) {
        try {
            activeMediaController = mc;
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

            mediaTrackPosition = pos;
            mediaPositionUpdateTime = lastUpdate;
            mediaTrackDuration = duration;

            String artKey = mc.getPackageName() + ":" + title + ":" + artist;
            boolean trackChanged = !artKey.equals(lastArtKey);

            if (trackChanged) {
                lastArtKey = artKey;
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
                    int pillSize = dpToPx(24);
                    int cardSize = dpToPx(56);
                    Bitmap newPill = Bitmap.createScaledBitmap(rawArt, pillSize, pillSize, true);
                    Bitmap newCard = Bitmap.createScaledBitmap(rawArt, cardSize, cardSize, true);
                    int dominant = extractDominantVibrantColor(rawArt);

                    Bitmap oldPill = currentPillArt;
                    Bitmap oldCard = currentCardArt;
                    currentPillArt = newPill;
                    currentCardArt = newCard;
                    mediaDominantColor = dominant;

                    if (oldPill != null && oldPill != newPill && !oldPill.isRecycled()) {
                        oldPill.recycle();
                    }
                    if (oldCard != null && oldCard != newCard && !oldCard.isRecycled()) {
                        oldCard.recycle();
                    }
                } else {
                    if (currentPillArt != null && !currentPillArt.isRecycled()) {
                        currentPillArt.recycle();
                    }
                    if (currentCardArt != null && !currentCardArt.isRecycled()) {
                        currentCardArt.recycle();
                    }
                    currentPillArt = null;
                    currentCardArt = null;
                    mediaDominantColor = Color.parseColor("#38BDF8");
                }
            }

            final boolean finalPlaying = isPlaying;
            final String finalTitle = title;
            final String finalArtist = artist;
            final boolean finalTrackChanged = trackChanged;

            handler.post(new Runnable() {
                @Override
                public void run() {
                    boolean prevPlay = isMediaPlaying;
                    isMediaPlaying = finalPlaying;
                    mediaTitle = finalTitle;
                    mediaArtist = finalArtist;

                    if (isMediaPlaying != prevPlay) {
                        if (isMediaPlaying && enableMedia) {
                            if (!previewLock && currentIsland != STATE_CALIBRATION) {
                                showIsland(STATE_MEDIA, 0);
                            }
                        } else if (!isMediaPlaying) {
                            if (currentIsland == STATE_MEDIA) {
                                startCollapse();
                            }
                        }
                    } else if (isMediaPlaying && enableMedia && currentIsland == STATE_IDLE && !isCollapsing) {
                        if (!previewLock && currentIsland != STATE_CALIBRATION) {
                            showIsland(STATE_MEDIA, 0);
                        }
                    }

                    if (finalTrackChanged && autoExpandMedia && isMediaPlaying && !isExpanded && !previewLock) {
                        expandCard();
                    }
                    if (isMediaPlaying != prevPlay || finalTrackChanged || currentIsland == STATE_IDLE) {
                        wakeEngineLoop();
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    private static Bitmap decodeBitmapFromUri(String uriStr) {
        InputStream is = null;
        try {
            Uri uri = Uri.parse(uriStr);
            if (context != null) {
                is = context.getContentResolver().openInputStream(uri);
                if (is != null) {
                    return BitmapFactory.decodeStream(is);
                }
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
            if (thumb != bmp && !thumb.isRecycled()) {
                thumb.recycle();
                thumb = null;
            }

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
            if (thumb != null && thumb != bmp && !thumb.isRecycled()) {
                thumb.recycle();
            }
        }
    }

    private static String formatTimeMs(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = ms / 1000;
        long m = totalSec / 60;
        long s = totalSec % 60;
        return String.format(Locale.US, "%d:%02d", m, s);
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

                // Session header starts with 4 spaces (not 6)
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
                    if (!desc.isEmpty() && !desc.equals("null")) {
                        String[] parts = desc.split(",");
                        if (parts.length > 0 && !parts[0].trim().isEmpty()) {
                            curSessionTitle = parts[0].trim();
                        }
                        if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                            curSessionArtist = parts[1].trim();
                        }
                    }
                }
            }
            if (curSessionPlaying && !foundPlaying) {
                foundPlaying = true;
                playingTitle = curSessionTitle;
                playingArtist = curSessionArtist;
            }
            reader.close();
            p.destroy();

            final boolean finalFoundPlaying = foundPlaying;
            final String finalPlayingTitle = playingTitle;
            final String finalPlayingArtist = playingArtist;

            handler.post(new Runnable() {
                @Override
                public void run() {
                    boolean prevPlay = isMediaPlaying;
                    isMediaPlaying = finalFoundPlaying;

                    if (finalFoundPlaying) {
                        if (!finalPlayingTitle.isEmpty()) {
                            mediaTitle = finalPlayingTitle;
                        }
                        if (!finalPlayingArtist.isEmpty()) {
                            mediaArtist = finalPlayingArtist;
                        }
                    }

                    if (isMediaPlaying != prevPlay) {
                        if (isMediaPlaying && enableMedia) {
                            if (!previewLock && currentIsland != STATE_CALIBRATION) {
                                showIsland(STATE_MEDIA, 0);
                            }
                        } else if (!isMediaPlaying) {
                            if (currentIsland == STATE_MEDIA) {
                                startCollapse();
                            }
                        }
                    } else if (isMediaPlaying && enableMedia && currentIsland == STATE_IDLE && !isCollapsing) {
                        if (!previewLock && currentIsland != STATE_CALIBRATION) {
                            showIsland(STATE_MEDIA, 0);
                        }
                    }
                    if (isMediaPlaying != prevPlay || currentIsland == STATE_IDLE) {
                        wakeEngineLoop();
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    private static void seekMediaTo(final long posMs) {
        mediaTrackPosition = posMs;
        mediaPositionUpdateTime = SystemClock.elapsedRealtime();
        if (backgroundHandler != null) {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (activeMediaController != null) {
                        try {
                            activeMediaController.getTransportControls().seekTo(posMs);
                        } catch (Throwable ignored) {}
                    }
                }
            });
        }
        wakeEngineLoop();
    }

    private static void toggleMediaPlayback() {
        if (activeMediaController != null) {
            try {
                if (isMediaPlaying) {
                    activeMediaController.getTransportControls().pause();
                } else {
                    activeMediaController.getTransportControls().play();
                }
                isMediaPlaying = !isMediaPlaying;
                wakeEngineLoop();
                return;
            } catch (Throwable ignored) {}
        }
        dispatchKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        isMediaPlaying = !isMediaPlaying;
        wakeEngineLoop();
    }

    private static void skipMediaNext() {
        if (activeMediaController != null) {
            try {
                activeMediaController.getTransportControls().skipToNext();
                wakeEngineLoop();
                return;
            } catch (Throwable ignored) {}
        }
        dispatchKey(KeyEvent.KEYCODE_MEDIA_NEXT);
        wakeEngineLoop();
    }

    private static void skipMediaPrevious() {
        if (activeMediaController != null) {
            try {
                activeMediaController.getTransportControls().skipToPrevious();
                wakeEngineLoop();
                return;
            } catch (Throwable ignored) {}
        }
        dispatchKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        wakeEngineLoop();
    }

    private static void dispatchKey(final int keyCode) {
        if (backgroundHandler != null) {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Runtime.getRuntime().exec(new String[]{"input", "keyevent", String.valueOf(keyCode)});
                    } catch (Throwable ignored) {}
                }
            });
        }
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
            long cap = readLongFromFile("/sys/class/power_supply/battery/capacity");
            if (cap > 0 && cap <= 100) {
                batteryPct = (int) cap;
            }

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

                if (watt > 0.1) {
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
        if (backgroundHandler != null) {
            backgroundHandler.post(new Runnable() {
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
                        sb.append("  \"torch_active\": ").append(isTorchActive).append(",\n");
                        sb.append("  \"charge_watt\": \"").append(chargeWattStr).append("\",\n");
                        sb.append("  \"hypercore_profile\": \"").append(hyperCoreProfile).append("\",\n");
                        sb.append("  \"hyperdl_active\": ").append(isHyperDLActive).append(",\n");
                        sb.append("  \"hyperdl_speed\": \"").append(hyperDLSpeed).append("\",\n");
                        sb.append("  \"hyperdl_progress\": ").append(hyperDLProgress).append(",\n");
                        sb.append("  \"preview_lock\": ").append(previewLock).append("\n");
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
            });
        }
    }

    private static void processTriggerCmd() {
        File trig = new File(triggerPath);
        if (!trig.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(trig))) {
            String cmd = br.readLine();
            if (cmd != null) {
                cmd = cmd.trim();
                System.out.println("Processing trigger: " + cmd);
                if (cmd.startsWith("charge")) {
                    readBatteryHardwareTelemetry();
                    triggerChargingEvent();
                } else if (cmd.startsWith("media")) {
                    currentIsland = STATE_MEDIA;
                    isMediaPlaying = true;
                    mediaTitle = "Starboy";
                    mediaArtist = "The Weeknd";
                    showIsland(STATE_MEDIA, 0);
                } else if (cmd.startsWith("volume")) {
                    String[] parts = cmd.split(":");
                    if (parts.length > 1) {
                        try { volumePercent = Integer.parseInt(parts[1].trim()); } catch (Exception ignored) {}
                    } else {
                        volumePercent = 75;
                    }
                    showIsland(STATE_VOLUME, 2000);
                } else if (cmd.startsWith("ringer")) {
                    String[] parts = cmd.split(":");
                    if (parts.length > 1) {
                        ringerLabel = parts[1].trim();
                    } else {
                        ringerLabel = "Silent";
                    }
                    showIsland(STATE_RINGER, 2200);
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
                    showIsland(STATE_NOTIFICATION, 3200);
                } else if (cmd.startsWith("torch")) {
                    if (currentIsland == STATE_TORCH) {
                        startCollapse();
                    } else {
                        showIsland(STATE_TORCH, 2600);
                    }
                } else if (cmd.startsWith("hyperdl") || cmd.startsWith("download")) {
                    isHyperDLActive = true;
                    hyperDLSpeed = "12.4 MB/s";
                    hyperDLProgress = 68;
                    showIsland(STATE_HYPERDL, 0);
                } else if (cmd.startsWith("preview:pill") || cmd.startsWith("preview:compact") || "preview-pill".equalsIgnoreCase(cmd)) {
                    previewLock = true;
                    showIsland(STATE_CHARGING, 0);
                    calibrationEndTime = 0L;
                } else if (cmd.startsWith("preview:expanded") || cmd.startsWith("preview:card") || "preview-card".equalsIgnoreCase(cmd)) {
                    previewLock = true;
                    currentIsland = STATE_MEDIA;
                    activeIslandType = STATE_MEDIA;
                    calibrationEndTime = 0L;
                    expandCard();
                    if (handler != null) handler.removeCallbacks(autoCollapseRunnable);
                } else if (cmd.startsWith("preview:reticle") || "calibrate".equalsIgnoreCase(cmd)) {
                    previewLock = true;
                    showIsland(STATE_CALIBRATION, 0);
                    calibrationEndTime = Long.MAX_VALUE;
                } else if (cmd.startsWith("preview:off") || "calibrate_off".equalsIgnoreCase(cmd) || "preview-off".equalsIgnoreCase(cmd)) {
                    previewLock = false;
                    startCollapse();
                } else if (cmd.startsWith("expand")) {
                    if (currentIsland == STATE_IDLE) {
                        currentIsland = STATE_MEDIA;
                        activeIslandType = STATE_MEDIA;
                    }
                    expandCard();
                } else if (cmd.startsWith("collapse")) {
                    previewLock = false;
                    collapseCard();
                } else if (cmd.startsWith("idle")) {
                    previewLock = false;
                    isMediaPlaying = false;
                    startCollapse();
                }
                persistStatusAsync();
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

            boolean prevEnabled = masterEnabled;
            masterEnabled       = parseBool(json, "enabled", masterEnabled);
            if (prevEnabled && !masterEnabled) {
                startCollapse();
            }

            cutoutCenterX       = parseInt(json, "cutout_x", cutoutCenterX);
            cutoutCenterY       = parseInt(json, "cutout_y", cutoutCenterY);
            cutoutRadius        = parseInt(json, "cutout_radius", cutoutRadius);
            xOffset             = parseInt(json, "x_offset", xOffset);
            yOffset             = parseInt(json, "y_offset", yOffset);
            customPillWidth     = parseInt(json, "pill_width", customPillWidth);
            customPillHeight    = parseInt(json, "pill_height", customPillHeight);
            customCardWidth     = parseInt(json, "card_width", customCardWidth);
            customCardHeight    = parseInt(json, "card_height", customCardHeight);
            cardRadius          = parseInt(json, "card_radius", cardRadius);
            cardYOffset         = parseInt(json, "card_y_offset", cardYOffset);
            cardPositionMode    = parseStr(json, "card_position_mode", cardPositionMode);
            pillAlignment       = parseStr(json, "pill_alignment", pillAlignment);
            notchMode           = parseBool(json, "notch_mode", notchMode);
            enableMedia         = parseBool(json, "enable_media", enableMedia);
            mediaShowPillArt    = parseBool(json, "media_show_pill_art", mediaShowPillArt);
            mediaArtStyle       = parseStr(json, "media_art_style", mediaArtStyle);
            mediaShowWaveform   = parseBool(json, "media_show_waveform", mediaShowWaveform);
            mediaAmbientGlow    = parseBool(json, "media_ambient_glow", mediaAmbientGlow);
            mediaGlowOpacity    = parseInt(json, "media_glow_opacity", mediaGlowOpacity);
            mediaMarquee        = parseBool(json, "media_marquee", mediaMarquee);
            enableCharging      = parseBool(json, "enable_charging", enableCharging);
            enableVolume        = parseBool(json, "enable_volume", enableVolume);
            enableRinger        = parseBool(json, "enable_ringer", enableRinger);
            enableNotifications = parseBool(json, "enable_notifications", enableNotifications);
            enableTorch         = parseBool(json, "enable_torch", enableTorch);
            enableHyperDL       = parseBool(json, "enable_hyperdl", enableHyperDL);
            enableHyperCore     = parseBool(json, "enable_hypercore", enableHyperCore);
            stealthRingIdle     = parseBool(json, "stealth_ring_idle", stealthRingIdle);
            hideInLandscape     = parseBool(json, "hide_in_landscape", hideInLandscape);
            springStiffness     = parseFloat(json, "spring_stiffness", springStiffness);
            springDamping       = parseFloat(json, "spring_damping", springDamping);
            autoExpandCharging  = parseBool(json, "auto_expand_charging", autoExpandCharging);
            autoExpandMedia     = parseBool(json, "auto_expand_media", autoExpandMedia);
            autoExpandNotif     = parseBool(json, "auto_expand_notification", autoExpandNotif);
            expandTimeoutMs     = parseInt(json, "expand_timeout_ms", expandTimeoutMs);

            if (previewLock) {
                calibrationEndTime = Long.MAX_VALUE;
            } else if (currentIsland == STATE_CALIBRATION) {
                calibrationEndTime = SystemClock.uptimeMillis() + 60000;
            }

            if (springW != null) {
                springW.setParameters(springStiffness, springDamping);
                springH.setParameters(springStiffness, springDamping);
                springR.setParameters(springStiffness, springDamping);
                springContentAlpha.setParameters(springStiffness, springDamping);
            }

            if (currentIsland == STATE_CALIBRATION) {
                float targetW = Math.round(Math.max(cutoutRadius * 3.0f, dpToPx(72)));
                if (springW != null) {
                    springW.snapTo(targetW);
                    springH.snapTo(targetW);
                    springR.snapTo(cutoutRadius);
                    springContentAlpha.snapTo(1.0f);
                }
            }
            prepareWindowForTarget();
            if (ringView != null) ringView.invalidate();
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
