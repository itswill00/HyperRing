package com.hyperring;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.View;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HyperRingOverlay {

    public static Context sysContext;
    public static Context context;
    public static Handler handler;
    public static Handler backgroundHandler;
    public static HandlerThread backgroundWorkerThread;
    public static ScheduledExecutorService workerPool;

    public static volatile boolean isLoopRunning = false;
    public static volatile boolean previewLock = false;
    public static volatile long calibrationEndTime = 0L;

    private static FileObserver stateObserver;

    public static final Runnable autoCollapseRunnable = new Runnable() {
        @Override
        public void run() {
            if (!previewLock && IslandState.currentIsland != IslandState.STATE_IDLE && !IslandState.isCollapsing) {
                boolean isTransient = (IslandState.currentIsland == IslandState.STATE_VOLUME
                        || IslandState.currentIsland == IslandState.STATE_RINGER
                        || IslandState.currentIsland == IslandState.STATE_NOTIFICATION
                        || IslandState.currentIsland == IslandState.STATE_CHARGING
                        || IslandState.currentIsland == IslandState.STATE_TORCH
                        || (IslandState.currentIsland == IslandState.STATE_PROGRESS && IslandState.isProgressComplete));

                if (IslandState.isExpanded) {
                    collapseCard();
                    if (isTransient && handler != null && IslandConfig.expandTimeoutMs > 0) {
                        handler.postDelayed(this, IslandConfig.expandTimeoutMs);
                    }
                } else if (isTransient) {
                    if (IslandState.isProgressActive && !IslandState.isProgressComplete && IslandConfig.enableProgress) {
                        IslandState.currentIsland = IslandState.STATE_PROGRESS;
                        IslandState.activeIslandType = IslandState.STATE_PROGRESS;
                        resetSpringsForMorph();
                        WindowManagerController.prepareWindowForTarget();
                        wakeEngineLoop();
                        IslandConfig.persistStatusAsync();
                    } else if (IslandState.isMediaPlaying && IslandConfig.enableMedia && !IslandState.userDismissedMedia) {
                        IslandState.currentIsland = IslandState.STATE_MEDIA;
                        IslandState.activeIslandType = IslandState.STATE_MEDIA;
                        resetSpringsForMorph();
                        WindowManagerController.prepareWindowForTarget();
                        wakeEngineLoop();
                        IslandConfig.persistStatusAsync();
                    } else {
                        startCollapse();
                    }
                } else {
                    if (IslandState.currentIsland != IslandState.STATE_MEDIA && IslandState.currentIsland != IslandState.STATE_PROGRESS) {
                        startCollapse();
                    }
                }
            }
        }
    };

    public static final Runnable collapseSafetyRunnable = new Runnable() {
        @Override
        public void run() {
            if (IslandState.isCollapsing) onAnimationSettled();
        }
    };

    public static final Runnable mediaPauseTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!IslandState.isMediaPlaying && IslandState.currentIsland == IslandState.STATE_MEDIA && !IslandState.isExpanded && !previewLock) {
                startCollapse();
            }
        }
    };

    public static final Runnable hunUntuckRunnable = new Runnable() {
        @Override
        public void run() {
            IslandState.isHUNTucked = false;
            if (IslandState.currentIsland != IslandState.STATE_IDLE) {
                if (WindowManagerController.ringView != null && WindowManagerController.ringView.getVisibility() != View.VISIBLE) {
                    WindowManagerController.ringView.setVisibility(View.VISIBLE);
                }
                resolveTargetState(SystemClock.uptimeMillis());
                WindowManagerController.prepareWindowForTarget();
                wakeEngineLoop();
            } else if (IslandConfig.enableMedia && !IslandState.userDismissedMedia && IslandState.isMediaPlaying) {
                showIsland(IslandState.STATE_MEDIA, 0);
            }
        }
    };

    public static void main(String[] args) {
        if (args.length > 0 && args[0] != null && !args[0].isEmpty()) {
            IslandConfig.stateDir = args[0];
            IslandConfig.configPath = IslandConfig.stateDir + "/config.json";
            IslandConfig.statusPath = IslandConfig.stateDir + "/status.json";
            IslandConfig.triggerPath = IslandConfig.stateDir + "/trigger.cmd";
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

        grantOverlayPermissions();
        IslandConfig.readConfig();
        WindowManagerController.init(sysContext, context, handler);

        // Hardware monitors standby in position
        BatteryMonitor.init(context);
        AudioMonitor.init(context);
        TorchMonitor.init(context);
        MediaMonitor.init();
        NotificationMonitor.startStreaming();

        registerScreenLifecycleReceiver();
        initStateFileObserver();
        startFastWatchdog();

        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    WindowManagerController.attachWindow();
                    wakeEngineLoop();
                } catch (Throwable t) {
                    System.err.println("Window mount failed: " + t.getMessage());
                }
            }
        });

        Looper.loop();
    }

    private static void grantOverlayPermissions() {
        try {
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method getServiceMethod = serviceManagerClass.getMethod("getService", String.class);
            android.os.IBinder appOpsBinder = (android.os.IBinder) getServiceMethod.invoke(null, "appops");
            if (appOpsBinder != null) {
                Class<?> stubClass = Class.forName("com.android.internal.app.IAppOpsService$Stub");
                Method asInterfaceMethod = stubClass.getMethod("asInterface", android.os.IBinder.class);
                Object appOps = asInterfaceMethod.invoke(null, appOpsBinder);
                Method setUidModeMethod = null;
                for (Method m : appOps.getClass().getMethods()) {
                    if ("setUidMode".equals(m.getName()) && m.getParameterTypes().length == 4) {
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

    private static void registerScreenLifecycleReceiver() {
        try {
            IntentFilter screenFilter = new IntentFilter();
            screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
            screenFilter.addAction(Intent.ACTION_SCREEN_ON);
            screenFilter.addAction(Intent.ACTION_USER_PRESENT);
            context.registerReceiver(new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent intent) {
                    if (intent == null) return;
                    String act = intent.getAction();
                    if (Intent.ACTION_SCREEN_OFF.equals(act)) {
                        IslandState.isScreenInteractive = false;
                    } else if (Intent.ACTION_SCREEN_ON.equals(act) || Intent.ACTION_USER_PRESENT.equals(act)) {
                        IslandState.isScreenInteractive = true;
                        if (handler != null) {
                            handler.postDelayed(new Runnable() {
                                @Override public void run() {
                                    WindowManagerController.checkDisplayRebind();
                                    wakeEngineLoop();
                                    if (IslandConfig.enableCharging) {
                                        BatteryMonitor.queryBatteryHardware();
                                    }
                                }
                            }, 150);
                        }
                    }
                }
            }, screenFilter);
        } catch (Throwable ignored) {}
    }

    private static void initStateFileObserver() {
        try {
            File dir = new File(IslandConfig.stateDir);
            if (!dir.exists()) dir.mkdirs();

            stateObserver = new FileObserver(IslandConfig.stateDir, FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO | FileObserver.CREATE | FileObserver.MODIFY) {
                @Override
                public void onEvent(int event, String path) {
                    if (path == null) return;
                    if ("trigger.cmd".equalsIgnoreCase(path)) {
                        handler.post(new Runnable() {
                            @Override public void run() { processTriggerCmd(); }
                        });
                    } else if ("config.json".equalsIgnoreCase(path)) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                IslandConfig.readConfig();
                                WindowManagerController.prepareWindowForTarget();
                                wakeEngineLoop();
                            }
                        });
                    }
                }
            };
            stateObserver.startWatching();
        } catch (Throwable ignored) {}
    }

    private static void startFastWatchdog() {
        // Fast watchdog running at 300ms interval for trigger check and recovery
        workerPool.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!checkScreenInteractive()) return;

                    if (new File(IslandConfig.triggerPath).exists()) {
                        handler.post(new Runnable() {
                            @Override public void run() { processTriggerCmd(); }
                        });
                    }

                    if (IslandConfig.hideInLandscape && WindowManagerController.isLandscape() && IslandState.currentIsland != IslandState.STATE_CALIBRATION) {
                        if (WindowManagerController.ringView != null && WindowManagerController.ringView.getVisibility() == View.VISIBLE) {
                            handler.post(new Runnable() {
                                @Override public void run() { WindowManagerController.prepareWindowForTarget(); }
                            });
                        }
                        return;
                    }

                    // Auto priority fallback
                    if (!IslandConfig.masterEnabled) return;

                    if (IslandState.currentIsland == IslandState.STATE_CHARGING && IslandState.isCharging) {
                        // Keep charging
                    } else if (IslandState.currentIsland == IslandState.STATE_VOLUME || IslandState.currentIsland == IslandState.STATE_RINGER
                            || IslandState.currentIsland == IslandState.STATE_TORCH || IslandState.currentIsland == IslandState.STATE_NOTIFICATION) {
                        // Transient states wait for their timeouts
                    } else if (IslandState.isProgressActive && !IslandState.isProgressComplete && IslandConfig.enableProgress) {
                        if (IslandState.currentIsland != IslandState.STATE_PROGRESS && IslandState.currentIsland != IslandState.STATE_CALIBRATION) {
                            showIsland(IslandState.STATE_PROGRESS, 0);
                        }
                    } else if (IslandConfig.enableMedia && !IslandState.userDismissedMedia && (IslandState.isMediaPlaying || IslandState.currentIsland == IslandState.STATE_MEDIA)) {
                        if (IslandState.isMediaPlaying) {
                            if ((IslandState.currentIsland != IslandState.STATE_MEDIA && IslandState.currentIsland != IslandState.STATE_CALIBRATION)
                                    || (IslandState.currentIsland == IslandState.STATE_MEDIA && WindowManagerController.ringView != null && WindowManagerController.ringView.getVisibility() != View.VISIBLE)) {
                                IslandState.isCollapsing = false;
                                showIsland(IslandState.STATE_MEDIA, 0);
                            }
                        }
                    } else if (IslandState.currentIsland != IslandState.STATE_CHARGING && IslandState.currentIsland != IslandState.STATE_CALIBRATION) {
                        if (IslandState.currentIsland != IslandState.STATE_IDLE && !IslandState.isCollapsing) {
                            startCollapse();
                        }
                    }

                    if (!isLoopRunning && IslandState.currentIsland != IslandState.STATE_IDLE && !IslandState.isCollapsing && IslandConfig.masterEnabled) {
                        wakeEngineLoop();
                    }
                } catch (Throwable ignored) {}
            }
        }, 200, 300, TimeUnit.MILLISECONDS);
    }

    public static boolean checkScreenInteractive() {
        try {
            Context c = context != null ? context : sysContext;
            if (c != null) {
                PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    IslandState.isScreenInteractive = pm.isInteractive();
                    return IslandState.isScreenInteractive;
                }
            }
        } catch (Throwable ignored) {}
        return true;
    }

    public static void wakeEngineLoop() {
        if (!IslandState.isScreenInteractive) return;
        if (WindowManagerController.ringView != null) {
            WindowManagerController.ringView.wakeLoop();
        }
    }

    public static void showIsland(final int state, final long timeoutMs) {
        if (!IslandConfig.masterEnabled && state != IslandState.STATE_CALIBRATION) return;
        if (IslandConfig.hideInLandscape && WindowManagerController.isLandscape() && state != IslandState.STATE_CALIBRATION) return;

        if (IslandState.isHUNTucked && state != IslandState.STATE_CALIBRATION) {
            if (state == IslandState.STATE_VOLUME || state == IslandState.STATE_CHARGING || state == IslandState.STATE_RINGER) {
                IslandState.isHUNTucked = false;
                if (handler != null) handler.removeCallbacks(hunUntuckRunnable);
            } else {
                return;
            }
        }

        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (handler != null) handler.removeCallbacks(collapseSafetyRunnable);
                IslandState.isCollapsing = false;

                // Priority Stack Check
                if (state != IslandState.STATE_CALIBRATION && IslandState.currentIsland != IslandState.STATE_IDLE && !IslandState.isCollapsing) {
                    int incomingPri = IslandState.statePriority(state);
                    int activePri   = IslandState.statePriority(IslandState.currentIsland);
                    if (incomingPri > activePri) return;
                }

                RingView rv = WindowManagerController.ringView;
                boolean wasExpanded = IslandState.isExpanded || (rv != null && rv.isMorphInFlight());
                boolean wasSameStateExpanded = (IslandState.currentIsland == state && IslandState.isExpanded);
                boolean wakingFromIdle = (IslandState.currentIsland == IslandState.STATE_IDLE || IslandState.isCollapsing);
                int oldState = IslandState.currentIsland;

                if (rv != null) {
                    if (wasExpanded && !wasSameStateExpanded) {
                        IslandState.previousIsland = oldState;
                        rv.morphSpring.setParameters(340f, 0.88f);
                        rv.morphSpring.setTarget(0.0f);
                        rv.crossfadeSpring.snapTo(1.0f);
                    } else if (!wasSameStateExpanded) {
                        rv.morphSpring.snapTo(0.0f);
                        if (!wakingFromIdle && oldState != state && oldState != IslandState.STATE_IDLE) {
                            IslandState.previousIsland = oldState;
                            rv.crossfadeSpring.snapTo(0.0f);
                            rv.crossfadeSpring.setParameters(480f, 0.92f);
                            rv.crossfadeSpring.setTarget(1.0f);
                        } else if (wakingFromIdle) {
                            IslandState.previousIsland = IslandState.STATE_IDLE;
                            rv.crossfadeSpring.snapTo(1.0f);
                        }
                    }
                }

                if (state != IslandState.STATE_CALIBRATION && timeoutMs > 0) previewLock = false;
                IslandState.currentIsland = state;
                IslandState.activeIslandType = state;
                IslandState.isCollapsing = false;
                if (!wasSameStateExpanded) IslandState.isExpanded = false;

                if (handler != null) handler.removeCallbacks(autoCollapseRunnable);
                if (timeoutMs > 0 && !previewLock) {
                    if (handler != null) handler.postDelayed(autoCollapseRunnable, timeoutMs);
                }

                if (rv != null) {
                    rv.springW.setParameters(IslandConfig.springStiffness, IslandConfig.springDamping);
                    rv.springH.setParameters(IslandConfig.springStiffness, IslandConfig.springDamping);
                    rv.springR.setParameters(IslandConfig.springStiffness, IslandConfig.springDamping);
                    rv.springContentAlpha.setParameters(IslandConfig.springStiffness, IslandConfig.springDamping);

                    if (wakingFromIdle) {
                        float initD = IslandConfig.cutoutRadius * 2.0f;
                        rv.springW.snapTo(initD);
                        rv.springH.snapTo(initD);
                        rv.springR.snapTo(IslandConfig.cutoutRadius);
                        rv.springContentAlpha.snapTo(0.0f);
                    }
                }

                resolveTargetState(SystemClock.uptimeMillis());
                WindowManagerController.prepareWindowForTarget();
                if (WindowManagerController.ringView != null && WindowManagerController.ringView.getVisibility() != View.VISIBLE) {
                    WindowManagerController.ringView.setVisibility(View.VISIBLE);
                }

                wakeEngineLoop();
                IslandConfig.persistStatusAsync();
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
                if (previewLock) return;
                previewLock = false;
                if (handler != null) handler.removeCallbacks(autoCollapseRunnable);

                if (IslandState.currentIsland == IslandState.STATE_IDLE) {
                    IslandState.isCollapsing = false;
                    return;
                }
                IslandState.isCollapsing = true;
                if (IslandState.isExpanded) IslandState.isExpanded = false;

                if (handler != null) {
                    handler.removeCallbacks(collapseSafetyRunnable);
                    handler.postDelayed(collapseSafetyRunnable, 350);
                }

                RingView rv = WindowManagerController.ringView;
                if (rv != null) {
                    rv.morphSpring.setParameters(360f, 0.90f);
                    rv.morphSpring.setTarget(0.0f);
                    rv.springContentAlpha.setParameters(380f, 0.92f);
                    rv.springContentAlpha.setTarget(0.0f);

                    float initD = IslandConfig.cutoutRadius * 2.0f;
                    rv.springW.setParameters(IslandConfig.springStiffness, IslandConfig.springDamping);
                    rv.springH.setParameters(IslandConfig.springStiffness, IslandConfig.springDamping);
                    rv.springR.setParameters(IslandConfig.springStiffness, IslandConfig.springDamping);
                    rv.springW.setTarget(initD);
                    rv.springH.setTarget(initD);
                    rv.springR.setTarget(IslandConfig.cutoutRadius);
                }

                wakeEngineLoop();
                IslandConfig.persistStatusAsync();
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else if (handler != null) handler.post(r);
    }

    public static void expandCard() {
        if (IslandState.isExpanded) return;
        IslandState.isExpanded = true;
        if (handler != null) {
            handler.removeCallbacks(autoCollapseRunnable);
            if (IslandConfig.expandTimeoutMs > 0 && !previewLock) {
                handler.postDelayed(autoCollapseRunnable, Math.max(6000, IslandConfig.expandTimeoutMs));
            }
        }
        RingView rv = WindowManagerController.ringView;
        if (rv != null) {
            rv.morphSpring.setParameters(320f, 0.82f);
            rv.morphSpring.setTarget(1.0f);
            rv.springContentAlpha.setParameters(420f, 0.88f);
            rv.springContentAlpha.setTarget(1.0f);
        }
        resolveTargetState(SystemClock.uptimeMillis());
        WindowManagerController.prepareWindowForTarget();
        wakeEngineLoop();
        IslandConfig.persistStatusAsync();
    }

    public static void collapseCard() {
        if (!IslandState.isExpanded) return;
        IslandState.isExpanded = false;
        if (handler != null) handler.removeCallbacks(autoCollapseRunnable);

        RingView rv = WindowManagerController.ringView;
        if (rv != null) {
            rv.morphSpring.setParameters(340f, 0.88f);
            rv.morphSpring.setTarget(0.0f);
            rv.springContentAlpha.setParameters(340f, 0.88f);
            rv.springContentAlpha.setTarget(0.0f);
        }
        resolveTargetState(SystemClock.uptimeMillis());
        wakeEngineLoop();
        IslandConfig.persistStatusAsync();
    }

    public static void resolveTargetState(long nowMs) {
        RingView rv = WindowManagerController.ringView;
        if (rv == null) return;

        float targetW;
        float targetH;
        float targetR;
        float targetContentAlpha;

        if (IslandState.currentIsland == IslandState.STATE_CALIBRATION) {
            float calibD = Math.max(IslandConfig.cutoutRadius * 3.0f, IslandConfig.dpToPx(72));
            targetW = calibD;
            targetH = calibD;
            targetR = calibD / 2.0f;
            targetContentAlpha = 1.0f;
        } else if (IslandState.isExpanded) {
            int defaultCardW = (IslandConfig.customCardWidth > 0) ? Math.round(IslandConfig.dpToPx(IslandConfig.customCardWidth)) : Math.round(IslandConfig.dpToPx(320));
            int defaultCardH = WindowManagerController.getDefaultCardHeight(IslandState.currentIsland);
            targetW = Math.min(IslandConfig.displayWidthPx - IslandConfig.dpToPx(16), defaultCardW);
            targetH = defaultCardH;
            targetR = IslandConfig.dpToPx(IslandConfig.cardRadius);
            targetContentAlpha = 1.0f;
        } else if (IslandState.isCollapsing || IslandState.currentIsland == IslandState.STATE_IDLE) {
            float initD = IslandConfig.cutoutRadius * 2.0f;
            targetW = initD;
            targetH = initD;
            targetR = IslandConfig.cutoutRadius;
            targetContentAlpha = 0.0f;
        } else {
            int defaultPillW = WindowManagerController.getDefaultPillWidth(IslandState.currentIsland);
            targetW = (IslandConfig.customPillWidth > 0) ? IslandConfig.dpToPx(IslandConfig.customPillWidth) : defaultPillW;
            targetH = (IslandConfig.customPillHeight > 0) ? IslandConfig.dpToPx(IslandConfig.customPillHeight) : Math.max(IslandConfig.cutoutRadius * 2.0f, IslandConfig.dpToPx(34));
            targetR = targetH / 2.0f;
            targetContentAlpha = 1.0f;
        }

        rv.springW.setTarget(targetW);
        rv.springH.setTarget(targetH);
        rv.springR.setTarget(targetR);
        rv.springContentAlpha.setTarget(targetContentAlpha);
    }

    public static void onAnimationSettled() {
        IslandState.isCollapsing = false;
        previewLock = false;

        if (WindowManagerController.ringView != null) {
            WindowManagerController.ringView.setVisibility(View.GONE);
        }
        IslandState.currentIsland = IslandState.STATE_IDLE;
        IslandState.activeIslandType = IslandState.STATE_IDLE;
        IslandState.previousIsland = IslandState.STATE_IDLE;
        IslandState.isExpanded = false;
        if (IslandState.isProgressComplete) {
            IslandState.isProgressActive = false;
            IslandState.isProgressComplete = false;
            IslandState.progressPct = 0;
            IslandState.progressTitle = "";
            IslandState.progressSubtitle = "";
            IslandState.progressAppName = "";
        }

        WindowManagerController.prepareWindowForTarget();
        wakeEngineLoop();
        IslandConfig.persistStatusAsync();
    }

    public static void tuckForHUN(final long durationMs) {
        if (handler == null) return;
        handler.post(new Runnable() {
            @Override
            public void run() {
                IslandState.isHUNTucked = true;
                if (handler != null) {
                    handler.removeCallbacks(hunUntuckRunnable);
                    handler.postDelayed(hunUntuckRunnable, durationMs > 0 ? durationMs : 4500);
                }
                if (IslandState.isExpanded) IslandState.isExpanded = false;
                RingView rv = WindowManagerController.ringView;
                if (rv != null) {
                    rv.springContentAlpha.setTarget(0.0f);
                    float initD = IslandConfig.cutoutRadius * 2.0f;
                    rv.springW.setTarget(initD);
                    rv.springH.setTarget(initD);
                }
                WindowManagerController.prepareWindowForTarget();
                wakeEngineLoop();
            }
        });
    }

    public static void triggerChargingEvent() {
        if (!IslandConfig.enableCharging) return;
        long timeout = IslandConfig.autoExpandCharging ? Math.max(IslandConfig.expandTimeoutMs, 4500) : 4500;
        showIsland(IslandState.STATE_CHARGING, timeout);
        if (IslandConfig.autoExpandCharging && !previewLock) {
            expandCard();
        }
    }

    private static void resetSpringsForMorph() {
        RingView rv = WindowManagerController.ringView;
        if (rv != null) {
            rv.crossfadeSpring.snapTo(0.0f);
            rv.crossfadeSpring.setParameters(480f, 0.92f);
            rv.crossfadeSpring.setTarget(1.0f);
            rv.morphSpring.snapTo(0.0f);
        }
    }

    public static void processTriggerCmd() {
        File trig = new File(IslandConfig.triggerPath);
        if (!trig.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(trig))) {
            String cmd = br.readLine();
            if (cmd != null) {
                cmd = cmd.trim();
                if (cmd.startsWith("charge")) {
                    BatteryMonitor.readBatteryHardwareTelemetry();
                    triggerChargingEvent();
                } else if (cmd.startsWith("media")) {
                    IslandState.userDismissedMedia = false;
                    IslandState.currentIsland = IslandState.STATE_MEDIA;
                    IslandState.isMediaPlaying = true;
                    IslandState.mediaTitle = "Starboy";
                    IslandState.mediaArtist = "The Weeknd";
                    showIsland(IslandState.STATE_MEDIA, 0);
                } else if (cmd.startsWith("volume")) {
                    String[] parts = cmd.split(":");
                    if (parts.length > 1) {
                        try { IslandState.volumePercent = Integer.parseInt(parts[1].trim()); } catch (Exception ignored) {}
                    } else {
                        IslandState.volumePercent = 75;
                    }
                    showIsland(IslandState.STATE_VOLUME, 2000);
                } else if (cmd.startsWith("ringer")) {
                    String[] parts = cmd.split(":");
                    if (parts.length > 1) IslandState.ringerLabel = parts[1].trim();
                    else IslandState.ringerLabel = "Silent";
                    showIsland(IslandState.STATE_RINGER, 2200);
                } else if (cmd.startsWith("notif") || cmd.startsWith("notification")) {
                    String[] parts = cmd.split(":");
                    String app = "Telegram";
                    if (parts.length > 3) {
                        app = parts[1].trim();
                        IslandState.notifTitle = parts[2].trim();
                        IslandState.notifContent = parts[3].trim();
                    } else if (parts.length > 2) {
                        app = parts[1].trim();
                        IslandState.notifTitle = parts[2].trim();
                        IslandState.notifContent = "New message received";
                    } else if (parts.length > 1) {
                        app = parts[1].trim();
                        IslandState.notifTitle = "New notification";
                        IslandState.notifContent = "New update available";
                    } else {
                        app = "Telegram";
                        IslandState.notifTitle = "Alex";
                        IslandState.notifContent = "Build completed successfully";
                    }
                    IslandState.notifAppName = app;
                    Context c = context != null ? context : sysContext;
                    IslandState.notifAppIcon = AppIconLoader.getAppIcon(c, app, Math.round(IslandConfig.dpToPx(28)));
                    showIsland(IslandState.STATE_NOTIFICATION, 3200);
                } else if (cmd.startsWith("download-done") || cmd.startsWith("progress-done")) {
                    IslandState.isProgressActive = true;
                    IslandState.isProgressComplete = true;
                    IslandState.progressPct = 100;
                    IslandState.progressSubtitle = "Download complete";
                    showIsland(IslandState.STATE_PROGRESS, 2500);
                } else if (cmd.startsWith("download") || cmd.startsWith("progress")) {
                    String[] parts = cmd.split(":");
                    IslandState.isProgressActive = true;
                    IslandState.isProgressComplete = false;
                    if (parts.length > 3) {
                        IslandState.progressAppName = parts[1].trim();
                        IslandState.progressTitle = parts[2].trim();
                        try { IslandState.progressPct = Integer.parseInt(parts[3].trim()); } catch (Exception ignored) {}
                        IslandState.progressSubtitle = IslandState.progressPct + "% · 12.4 MB/s";
                    } else if (parts.length > 2) {
                        IslandState.progressAppName = parts[1].trim();
                        IslandState.progressTitle = parts[2].trim();
                        IslandState.progressPct = 55;
                        IslandState.progressSubtitle = "55% · 8.2 MB/s";
                    } else {
                        IslandState.progressAppName = "Chrome";
                        IslandState.progressTitle = "update.zip";
                        IslandState.progressPct = 68;
                        IslandState.progressSubtitle = "82 MB / 120 MB · 12.4 MB/s";
                    }
                    Context c = context != null ? context : sysContext;
                    IslandState.progressAppIcon = AppIconLoader.getAppIcon(c, IslandState.progressAppName, Math.round(IslandConfig.dpToPx(28)));
                    showIsland(IslandState.STATE_PROGRESS, 0);
                } else if (cmd.startsWith("torch")) {
                    if (IslandState.currentIsland == IslandState.STATE_TORCH) {
                        startCollapse();
                    } else {
                        showIsland(IslandState.STATE_TORCH, 2600);
                    }
                } else if (cmd.startsWith("preview:pill") || cmd.startsWith("preview:compact") || "preview-pill".equalsIgnoreCase(cmd)) {
                    previewLock = true;
                    showIsland(IslandState.STATE_CHARGING, 0);
                    calibrationEndTime = 0L;
                } else if (cmd.startsWith("preview:expanded") || cmd.startsWith("preview:card") || "preview-card".equalsIgnoreCase(cmd)) {
                    previewLock = true;
                    IslandState.currentIsland = IslandState.STATE_MEDIA;
                    IslandState.activeIslandType = IslandState.STATE_MEDIA;
                    calibrationEndTime = 0L;
                    expandCard();
                    if (handler != null) handler.removeCallbacks(autoCollapseRunnable);
                } else if (cmd.startsWith("preview:reticle") || "calibrate".equalsIgnoreCase(cmd)) {
                    previewLock = true;
                    showIsland(IslandState.STATE_CALIBRATION, 0);
                    calibrationEndTime = Long.MAX_VALUE;
                } else if (cmd.startsWith("preview:off") || "calibrate_off".equalsIgnoreCase(cmd) || "preview-off".equalsIgnoreCase(cmd)) {
                    previewLock = false;
                    startCollapse();
                } else if (cmd.startsWith("expand")) {
                    IslandState.userDismissedMedia = false;
                    if (IslandState.currentIsland == IslandState.STATE_IDLE) {
                        IslandState.currentIsland = IslandState.STATE_MEDIA;
                        IslandState.activeIslandType = IslandState.STATE_MEDIA;
                    }
                    expandCard();
                } else if (cmd.startsWith("collapse")) {
                    previewLock = false;
                    collapseCard();
                } else if (cmd.startsWith("idle")) {
                    previewLock = false;
                    IslandState.isMediaPlaying = false;
                    startCollapse();
                }
                IslandConfig.persistStatusAsync();
            }
        } catch (Throwable ignored) {}
        trig.delete();
    }
}
