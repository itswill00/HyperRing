package com.hyperring;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

public class RingView extends View {

    public Spring springW;
    public Spring springH;
    public Spring springR;
    public Spring springContentAlpha;
    public Spring morphSpring;
    public Spring crossfadeSpring;

    private Paint paintOledBlack;
    private Paint paintBorder;
    private Paint paintTextPrimary;
    private Paint paintTextSecondary;
    private Paint paintTextTertiary;
    private Paint paintAccentGreen;
    private Paint paintAccentAmber;
    private Paint paintAccentRed;
    private Paint paintAccentCyan;
    private Paint paintTrackBg;
    private Paint paintAudioPulse;
    private Paint paintArtBitmap;
    private Paint paintCalibRing;
    private Paint paintCalibCross;

    private Path smoothSquirclePath = new Path();
    private Path artClipPath = new Path();
    private RectF tempRectF = new RectF();
    private RectF artRectF = new RectF();

    private float[] barHeights = new float[]{0.3f, 0.7f, 0.5f, 0.9f};
    private float[] barTargets = new float[]{0.3f, 0.7f, 0.5f, 0.9f};
    private long lastWaveStep = 0L;

    private float touchDownX = 0f;
    private float touchDownY = 0f;
    private long touchDownTime = 0L;
    private long lastFrameNanos = 0L;

    public final Choreographer.FrameCallback vsyncCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!HyperRingOverlay.isLoopRunning) return;

            float dt = 0.016f;
            if (lastFrameNanos > 0L) {
                dt = (frameTimeNanos - lastFrameNanos) / 1_000_000_000.0f;
                if (dt <= 0f || dt > 0.1f) dt = 0.016f;
            }
            lastFrameNanos = frameTimeNanos;

            boolean movingW = springW.update(dt);
            boolean movingH = springH.update(dt);
            boolean movingR = springR.update(dt);
            boolean movingA = springContentAlpha.update(dt);
            boolean movingMorph = morphSpring.update(dt);
            boolean movingCross = crossfadeSpring.update(dt);

            boolean anyMoving = movingW || movingH || movingR || movingA || movingMorph || movingCross;

            if (WindowManagerController.ringView != null) {
                WindowManagerController.ringView.invalidate();
            }

            if (morphSpring.isAtEquilibrium() && morphSpring.current <= 0.0005f && !IslandState.isExpanded) {
                WindowManagerController.prepareWindowForTarget();
            }

            boolean mediaNeedsAnim = IslandState.currentIsland == IslandState.STATE_MEDIA && IslandState.isMediaPlaying
                    && !IslandState.isCollapsing && ((!IslandState.isExpanded && IslandConfig.mediaShowWaveform) || IslandState.isExpanded);
            boolean marqueeNeedsAnim = IslandState.isExpanded && IslandState.currentIsland == IslandState.STATE_MEDIA && IslandConfig.mediaMarquee && !IslandState.isCollapsing;
            boolean needNextFrame = anyMoving || mediaNeedsAnim;

            if (needNextFrame) {
                if (mediaNeedsAnim && !anyMoving) {
                    int throttleDelay = marqueeNeedsAnim ? 33 : (IslandState.isExpanded ? 1000 : 90);
                    if (HyperRingOverlay.handler != null) {
                        HyperRingOverlay.handler.removeCallbacks(audioThrottleRunnable);
                        HyperRingOverlay.handler.postDelayed(audioThrottleRunnable, throttleDelay);
                    }
                } else {
                    if (HyperRingOverlay.handler != null) HyperRingOverlay.handler.removeCallbacks(audioThrottleRunnable);
                    Choreographer.getInstance().postFrameCallback(this);
                }
            } else {
                HyperRingOverlay.isLoopRunning = false;
                if (HyperRingOverlay.handler != null) HyperRingOverlay.handler.removeCallbacks(audioThrottleRunnable);
                if (WindowManagerController.ringView != null && WindowManagerController.ringView.getLayerType() != View.LAYER_TYPE_NONE) {
                    WindowManagerController.ringView.setLayerType(View.LAYER_TYPE_NONE, null);
                }
                if (IslandState.isCollapsing) {
                    HyperRingOverlay.onAnimationSettled();
                }
            }
        }
    };

    private final Runnable audioThrottleRunnable = new Runnable() {
        @Override
        public void run() {
            if (!HyperRingOverlay.isLoopRunning || !IslandState.isScreenInteractive) {
                HyperRingOverlay.isLoopRunning = false;
                return;
            }
            boolean mediaNeedsAnim = IslandState.currentIsland == IslandState.STATE_MEDIA && IslandState.isMediaPlaying && !IslandState.isCollapsing
                    && ((!IslandState.isExpanded && IslandConfig.mediaShowWaveform) || IslandState.isExpanded);
            if (!mediaNeedsAnim) {
                HyperRingOverlay.isLoopRunning = false;
                return;
            }
            Choreographer.getInstance().postFrameCallback(vsyncCallback);
        }
    };

    public RingView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        setClickable(true);

        float initD = IslandConfig.cutoutRadius * 2.0f;
        springW = new Spring(initD, IslandConfig.springStiffness, IslandConfig.springDamping);
        springH = new Spring(initD, IslandConfig.springStiffness, IslandConfig.springDamping);
        springR = new Spring(IslandConfig.cutoutRadius, IslandConfig.springStiffness, IslandConfig.springDamping);
        springContentAlpha = new Spring(0.0f, 520f, 1.05f);
        morphSpring = new Spring(0.0f, 320f, 0.82f);
        crossfadeSpring = new Spring(1.0f, 480f, 0.92f);

        initGraphics();
    }

    private void initGraphics() {
        paintOledBlack = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintOledBlack.setColor(Color.BLACK);
        paintOledBlack.setStyle(Paint.Style.FILL);

        paintBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBorder.setColor(Color.argb(32, 255, 255, 255));
        paintBorder.setStyle(Paint.Style.STROKE);
        paintBorder.setStrokeWidth(IslandConfig.dpToPx(0.75f));

        paintTextPrimary = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTextPrimary.setColor(Color.WHITE);
        paintTextPrimary.setFakeBoldText(true);
        try { paintTextPrimary.setFontFeatureSettings("'tnum' 1"); } catch (Throwable ignored) {}

        paintTextSecondary = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTextSecondary.setColor(Color.argb(175, 255, 255, 255));
        try { paintTextSecondary.setFontFeatureSettings("'tnum' 1"); } catch (Throwable ignored) {}

        paintTextTertiary = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTextTertiary.setColor(Color.argb(120, 255, 255, 255));
        try { paintTextTertiary.setFontFeatureSettings("'tnum' 1"); } catch (Throwable ignored) {}

        Typeface tfBold = Typeface.DEFAULT_BOLD;
        Typeface tfNormal = Typeface.DEFAULT;
        try {
            Typeface t = Typeface.create("sans-serif-medium", Typeface.BOLD);
            if (t != null) tfBold = t;
        } catch (Throwable ignored) {}
        try {
            Typeface t = Typeface.create("sans-serif", Typeface.NORMAL);
            if (t != null) tfNormal = t;
        } catch (Throwable ignored) {}

        if (tfBold != null) paintTextPrimary.setTypeface(tfBold);
        if (tfNormal != null) {
            paintTextSecondary.setTypeface(tfNormal);
            paintTextTertiary.setTypeface(tfNormal);
        }

        paintAccentGreen = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAccentGreen.setColor(Color.parseColor("#34D399"));
        paintAccentGreen.setStyle(Paint.Style.FILL);

        paintAccentAmber = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAccentAmber.setColor(Color.parseColor("#FBBF24"));
        paintAccentAmber.setStyle(Paint.Style.FILL);

        paintAccentRed = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAccentRed.setColor(Color.parseColor("#F87171"));
        paintAccentRed.setStyle(Paint.Style.FILL);

        paintAccentCyan = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAccentCyan.setColor(Color.parseColor("#38BDF8"));
        paintAccentCyan.setStyle(Paint.Style.FILL);

        paintTrackBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTrackBg.setColor(Color.argb(45, 255, 255, 255));
        paintTrackBg.setStyle(Paint.Style.FILL);

        paintArtBitmap = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        paintCalibRing = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintCalibCross = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    public boolean isMorphInFlight() {
        return morphSpring != null && (!morphSpring.isAtEquilibrium() || morphSpring.current > 0.0005f);
    }

    public void wakeLoop() {
        if (HyperRingOverlay.isLoopRunning) return;
        HyperRingOverlay.isLoopRunning = true;
        lastFrameNanos = 0L;
        if (HyperRingOverlay.handler != null) {
            HyperRingOverlay.handler.removeCallbacks(audioThrottleRunnable);
        }
        Choreographer.getInstance().postFrameCallback(vsyncCallback);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (WindowManagerController.params == null) return false;

        if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            if (IslandState.isExpanded) {
                HyperRingOverlay.collapseCard();
                return true;
            }
            return false;
        }

        float x = event.getX();
        float y = event.getY();
        float pillW = springW.current;
        float pillH = springH.current;
        float pillRelX = WindowManagerController.getPillRelX(getWidth(), pillW);
        float pillRelY = WindowManagerController.getPillRelY(getHeight(), pillH);

        boolean inside = true;
        if (IslandState.isExpanded) {
            float pad = IslandConfig.dpToPx(8);
            inside = (x >= pillRelX - pad && x <= pillRelX + pillW + pad && y >= pillRelY - pad && y <= pillRelY + pillH + pad);
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!inside) {
                    if (IslandState.isExpanded) {
                        HyperRingOverlay.collapseCard();
                        return true;
                    }
                    return false;
                }
                touchDownX = x;
                touchDownY = y;
                touchDownTime = SystemClock.uptimeMillis();
                return true;

            case MotionEvent.ACTION_MOVE:
                float dy = y - touchDownY;
                if (dy > IslandConfig.dpToPx(14) && !IslandState.isExpanded && IslandState.currentIsland != IslandState.STATE_IDLE && IslandState.currentIsland != IslandState.STATE_CALIBRATION) {
                    HyperRingOverlay.expandCard();
                    return true;
                } else if (dy < -IslandConfig.dpToPx(14) && IslandState.isExpanded) {
                    HyperRingOverlay.collapseCard();
                    return true;
                } else if (dy < -IslandConfig.dpToPx(16) && !IslandState.isExpanded && IslandState.currentIsland != IslandState.STATE_IDLE && IslandState.currentIsland != IslandState.STATE_CALIBRATION) {
                    if (IslandState.currentIsland == IslandState.STATE_MEDIA) {
                        IslandState.userDismissedMedia = true;
                    }
                    HyperRingOverlay.startCollapse();
                    return true;
                }
                return true;

            case MotionEvent.ACTION_UP:
                float totalDistX = Math.abs(x - touchDownX);
                float totalDistY = Math.abs(y - touchDownY);
                long duration = SystemClock.uptimeMillis() - touchDownTime;

                if (!IslandState.isExpanded && IslandState.currentIsland != IslandState.STATE_IDLE && IslandState.currentIsland != IslandState.STATE_CALIBRATION) {
                    float deltaX = x - touchDownX;
                    if (Math.abs(deltaX) > IslandConfig.dpToPx(28) && totalDistY < IslandConfig.dpToPx(24) && duration < 500) {
                        if (IslandState.currentIsland == IslandState.STATE_MEDIA) {
                            performHaptic(0);
                            if (deltaX > 0) MediaMonitor.skipNext();
                            else MediaMonitor.skipPrev();
                            return true;
                        } else {
                            performHaptic(0);
                            HyperRingOverlay.startCollapse();
                            return true;
                        }
                    }
                }

                if (totalDistX < IslandConfig.dpToPx(24) && totalDistY < IslandConfig.dpToPx(24)) {
                    handleTap(x - pillRelX, y - pillRelY);
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void handleTap(float x, float y) {
        if (!IslandState.isExpanded) {
            if (IslandState.currentIsland != IslandState.STATE_IDLE && IslandState.currentIsland != IslandState.STATE_CALIBRATION) {
                HyperRingOverlay.expandCard();
            }
        } else {
            float viewW = springW.current;

            if (IslandState.currentIsland == IslandState.STATE_MEDIA) {
                float artSize = IslandConfig.dpToPx(48);
                float artTop = IslandConfig.dpToPx(20);

                if (y >= artTop && y <= artTop + artSize && x >= IslandConfig.dpToPx(16) && x <= viewW - IslandConfig.dpToPx(56)) {
                    performHaptic(0);
                    try {
                        if (IslandState.activeMediaController != null) {
                            PendingIntent pi = IslandState.activeMediaController.getSessionActivity();
                            if (pi != null) {
                                pi.send();
                                HyperRingOverlay.collapseCard();
                                return;
                            }
                        }
                    } catch (Throwable ignored) {}
                    HyperRingOverlay.collapseCard();
                    return;
                }

                // Row 2: Playback buttons
                float btnY = artTop + artSize + IslandConfig.dpToPx(24);
                float centerX = viewW / 2.0f;
                float b2X = centerX - IslandConfig.dpToPx(52);
                float b3X = centerX;
                float b4X = centerX + IslandConfig.dpToPx(52);
                float btnHitRadius = IslandConfig.dpToPx(22);

                if (Math.abs(y - btnY) < btnHitRadius) {
                    performHaptic(0);
                    if (Math.abs(x - b3X) < IslandConfig.dpToPx(24)) {
                        MediaMonitor.togglePlayPause();
                        return;
                    } else if (Math.abs(x - b2X) < btnHitRadius) {
                        MediaMonitor.skipPrev();
                        return;
                    } else if (Math.abs(x - b4X) < btnHitRadius) {
                        MediaMonitor.skipNext();
                        return;
                    }
                }

                // Row 3: Seekbar
                float curH = springH.current;
                float barY = curH - IslandConfig.dpToPx(24);
                float barLeft = IslandConfig.dpToPx(62);
                float barW = viewW - IslandConfig.dpToPx(124);

                if (IslandState.mediaTrackDuration > 0 && Math.abs(y - barY) < IslandConfig.dpToPx(14) && x >= barLeft - IslandConfig.dpToPx(8) && x <= barLeft + barW + IslandConfig.dpToPx(8)) {
                    performHaptic(0);
                    float fraction = Math.max(0f, Math.min(1f, (x - barLeft) / barW));
                    long seekTarget = (long) (fraction * IslandState.mediaTrackDuration);
                    MediaMonitor.seekTo(seekTarget);
                    return;
                }
            } else if (IslandState.currentIsland == IslandState.STATE_PROGRESS) {
                // Tapping download card opens application or collapses
                performHaptic(0);
                Context c = HyperRingOverlay.context != null ? HyperRingOverlay.context : HyperRingOverlay.sysContext;
                if (c != null && !IslandState.progressPkg.isEmpty()) {
                    try {
                        Intent li = c.getPackageManager().getLaunchIntentForPackage(IslandState.progressPkg);
                        if (li != null) {
                            li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            c.startActivity(li);
                            HyperRingOverlay.collapseCard();
                            return;
                        }
                    } catch (Throwable ignored) {}
                }
                HyperRingOverlay.collapseCard();
                return;
            }

            HyperRingOverlay.collapseCard();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (WindowManagerController.params == null) return;
        if (IslandConfig.hideInLandscape && WindowManagerController.isLandscape() && IslandState.currentIsland != IslandState.STATE_CALIBRATION) {
            return;
        }
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        float pillW = springW.current;
        float pillH = springH.current;

        float velX = springW.velocity;
        float hSquash = 0f;
        boolean isMorphFlight = morphSpring != null && (!morphSpring.isAtEquilibrium() || morphSpring.current > 0.0005f);
        if (!isMorphFlight && !IslandState.isExpanded && Math.abs(velX) > 120f) {
            hSquash = Math.max(-IslandConfig.dpToPx(2.0f), Math.min(IslandConfig.dpToPx(3.5f), (velX / 1400f) * IslandConfig.dpToPx(3.0f)));
        }
        float effH = Math.max(IslandConfig.cutoutRadius * 2.0f, pillH - hSquash);

        float pillRelX = WindowManagerController.getPillRelX(getWidth(), pillW);
        float pillRelY = WindowManagerController.getPillRelY(getHeight(), effH);

        canvas.save();
        canvas.translate(pillRelX, pillRelY);
        renderIsland(canvas, pillW, effH);
        canvas.restore();
    }

    private void renderIsland(Canvas canvas, float w, float h) {
        if (w <= 0 || h <= 0) return;

        if (IslandState.currentIsland == IslandState.STATE_CALIBRATION) {
            float holeRelX = w / 2.0f;
            float holeRelY = h / 2.0f;

            paintCalibRing.setColor(Color.parseColor("#00E5FF"));
            paintCalibRing.setStrokeWidth(IslandConfig.dpToPx(2.0f));
            paintCalibRing.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(holeRelX, holeRelY, IslandConfig.cutoutRadius, paintCalibRing);

            paintCalibRing.setColor(Color.parseColor("#4400E5FF"));
            canvas.drawCircle(holeRelX, holeRelY, IslandConfig.cutoutRadius + IslandConfig.dpToPx(4), paintCalibRing);

            paintCalibCross.setColor(Color.parseColor("#FF1744"));
            paintCalibCross.setStrokeWidth(IslandConfig.dpToPx(1.5f));
            paintCalibCross.setStyle(Paint.Style.STROKE);
            canvas.drawLine(holeRelX - IslandConfig.dpToPx(22), holeRelY, holeRelX + IslandConfig.dpToPx(22), holeRelY, paintCalibCross);
            canvas.drawLine(holeRelX, holeRelY - IslandConfig.dpToPx(22), holeRelX, holeRelY + IslandConfig.dpToPx(22), paintCalibCross);

            paintCalibCross.setStyle(Paint.Style.FILL);
            canvas.drawCircle(holeRelX, holeRelY, IslandConfig.dpToPx(2.5f), paintCalibCross);
            return;
        }

        float curR = springR.current;
        tempRectF.set(0, 0, w, h);

        buildSmoothSquirclePath(smoothSquirclePath, w, h, curR, 0.65f);
        canvas.drawPath(smoothSquirclePath, paintOledBlack);

        float contentAlpha = springContentAlpha.current;
        int borderAlpha = Math.min(65, Math.max(0, (int) (contentAlpha * 65)));
        if (borderAlpha > 0 && (IslandState.currentIsland != IslandState.STATE_IDLE || IslandState.isExpanded || IslandState.isCollapsing)) {
            int accentTint = getActiveAccentColor();
            if (accentTint != Color.TRANSPARENT) {
                int r = (Color.red(accentTint) + 255 * 2) / 3;
                int g = (Color.green(accentTint) + 255 * 2) / 3;
                int b = (Color.blue(accentTint) + 255 * 2) / 3;
                paintBorder.setColor(Color.argb(borderAlpha, r, g, b));
            } else {
                paintBorder.setColor(Color.argb(borderAlpha, 255, 255, 255));
            }
            canvas.drawPath(smoothSquirclePath, paintBorder);
        }

        if (contentAlpha > 0.04f) {
            canvas.save();
            canvas.clipPath(smoothSquirclePath);

            float compactH = (IslandConfig.customPillHeight > 0) ? IslandConfig.dpToPx(IslandConfig.customPillHeight) : Math.max(Math.round(IslandConfig.cutoutRadius * 2.0f), IslandConfig.dpToPx(34));
            int cardState = (IslandState.previousIsland != IslandState.STATE_IDLE && !IslandState.isExpanded) ? IslandState.previousIsland : IslandState.currentIsland;
            float targetCardH = (IslandConfig.customCardHeight > 0) ? IslandConfig.dpToPx(IslandConfig.customCardHeight) : WindowManagerController.getDefaultCardHeight(cardState);
            float progress = Math.max(0.0f, Math.min(1.0f, (h - compactH) / Math.max(1.0f, targetCardH - compactH)));

            if (IslandState.isExpanded) {
                if (progress > 0.40f) {
                    float t = (progress - 0.40f) / 0.60f;
                    float fastOutSlow = t * t * (3.0f - 2.0f * t);
                    renderExpandedContent(canvas, w, h, contentAlpha * fastOutSlow);
                } else {
                    float compactFade = Math.max(0.0f, 1.0f - (progress / 0.40f));
                    if (compactFade > 0.02f) {
                        renderCompactContent(canvas, w, h, contentAlpha * compactFade);
                    }
                }
            } else {
                if (progress > 0.72f) {
                    float collapseAlpha = (progress - 0.72f) / 0.28f;
                    renderExpandedContent(canvas, w, h, contentAlpha * collapseAlpha);
                } else if (progress < 0.20f && !IslandState.isCollapsing) {
                    float pillFade = (0.20f - progress) / 0.20f;
                    renderCompactContent(canvas, w, h, contentAlpha * pillFade);
                }
            }

            canvas.restore();
        }
    }

    private void renderCompactContent(Canvas canvas, float curW, float curH, float alpha) {
        float centerY = curH / 2.0f;
        float artSize = IslandConfig.dpToPx(22);
        float artLeft = IslandConfig.dpToPx(12);
        float iconCenterX = artLeft + (artSize / 2.0f);

        float totalWaveW = (4 * IslandConfig.dpToPx(2.2f)) + (3 * IslandConfig.dpToPx(2.0f));
        float barsStartX = curW - IslandConfig.dpToPx(12) - totalWaveW;
        float textCenterX = curW - IslandConfig.dpToPx(12) - totalWaveW / 2.0f;

        Paint.Align textAlign = Paint.Align.CENTER;
        if ("left".equalsIgnoreCase(IslandConfig.pillAlignment)) {
            iconCenterX = Math.max(IslandConfig.dpToPx(16), IslandConfig.cutoutRadius * 2.0f + IslandConfig.dpToPx(14));
            textCenterX = curW - IslandConfig.dpToPx(18);
            barsStartX  = curW - IslandConfig.dpToPx(12) - totalWaveW;
            textAlign   = Paint.Align.RIGHT;
        } else if ("right".equalsIgnoreCase(IslandConfig.pillAlignment)) {
            iconCenterX = IslandConfig.dpToPx(18);
            textCenterX = Math.min(curW - IslandConfig.dpToPx(16), curW - IslandConfig.cutoutRadius * 2.0f - IslandConfig.dpToPx(14));
            barsStartX  = textCenterX - totalWaveW / 2.0f;
            textAlign   = Paint.Align.RIGHT;
        }

        int curType = (IslandState.activeIslandType != IslandState.STATE_IDLE ? IslandState.activeIslandType : IslandState.currentIsland);
        boolean isCrossfading = (IslandState.previousIsland != IslandState.STATE_IDLE && IslandState.previousIsland != curType && crossfadeSpring != null && crossfadeSpring.isMoving());

        if (isCrossfading) {
            float t = Math.max(0.0f, Math.min(1.0f, crossfadeSpring.current));
            float easeT = t * t * (3.0f - 2.0f * t);
            float outAlpha = alpha * (1.0f - easeT);
            float inAlpha = alpha * easeT;

            if (outAlpha > 0.02f) {
                drawCompactState(canvas, IslandState.previousIsland, curW, curH, outAlpha, iconCenterX, textCenterX, centerY, textAlign, barsStartX);
            }
            if (inAlpha > 0.02f) {
                drawCompactState(canvas, curType, curW, curH, inAlpha, iconCenterX, textCenterX, centerY, textAlign, barsStartX);
            }
        } else {
            drawCompactState(canvas, curType, curW, curH, alpha, iconCenterX, textCenterX, centerY, textAlign, barsStartX);
        }
    }

    private void drawCompactState(Canvas canvas, int renderType, float curW, float curH, float alpha,
                                  float iconCenterX, float textCenterX, float centerY, Paint.Align textAlign,
                                  float barsStartX) {
        int intAlpha = Math.min(255, Math.max(0, (int) (alpha * 255)));
        if (intAlpha <= 0) return;

        if (renderType == IslandState.STATE_CHARGING) {
            paintAccentGreen.setAlpha(intAlpha);
            drawBoltIcon(canvas, iconCenterX, centerY, IslandConfig.dpToPx(12), paintAccentGreen);

            paintTextPrimary.setTextSize(IslandConfig.spToPx(12.5f));
            paintTextPrimary.setTextAlign(textAlign);
            paintTextPrimary.setColor(Color.parseColor("#34D399"));
            paintTextPrimary.setAlpha(intAlpha);
            canvas.drawText(IslandState.batteryPct + "%", textCenterX, centerY + IslandConfig.dpToPx(4.5f), paintTextPrimary);
            paintTextPrimary.setColor(Color.WHITE);

        } else if (renderType == IslandState.STATE_MEDIA) {
            float artThumbSize = IslandConfig.dpToPx(22);
            float artLeft = iconCenterX - artThumbSize / 2.0f;
            float artTop = centerY - artThumbSize / 2.0f;
            artRectF.set(artLeft, artTop, artLeft + artThumbSize, artTop + artThumbSize);

            if (IslandConfig.mediaShowPillArt && IslandState.currentPillArt != null && !IslandState.currentPillArt.isRecycled()) {
                canvas.save();
                artClipPath.reset();
                if ("circle".equalsIgnoreCase(IslandConfig.mediaArtStyle)) {
                    artClipPath.addCircle(artRectF.centerX(), artRectF.centerY(), artThumbSize / 2.0f, Path.Direction.CW);
                } else {
                    artClipPath.addRoundRect(artRectF, IslandConfig.dpToPx(4.5f), IslandConfig.dpToPx(4.5f), Path.Direction.CW);
                }
                canvas.clipPath(artClipPath);
                paintArtBitmap.setAlpha(intAlpha);
                canvas.drawBitmap(IslandState.currentPillArt, null, artRectF, paintArtBitmap);
                canvas.restore();
            } else {
                canvas.save();
                artClipPath.reset();
                artClipPath.addRoundRect(artRectF, IslandConfig.dpToPx(4.5f), IslandConfig.dpToPx(4.5f), Path.Direction.CW);
                canvas.clipPath(artClipPath);
                paintOledBlack.setAlpha(intAlpha);
                canvas.drawRoundRect(artRectF, IslandConfig.dpToPx(4.5f), IslandConfig.dpToPx(4.5f), paintOledBlack);
                int discColor = (IslandState.mediaDominantColor != Color.TRANSPARENT) ? IslandState.mediaDominantColor : Color.parseColor("#38BDF8");
                if ("#FFFFFF".equalsIgnoreCase(IslandConfig.mediaPulseColor)) discColor = Color.WHITE;
                else if ("#A1A1AA".equalsIgnoreCase(IslandConfig.mediaPulseColor)) discColor = Color.parseColor("#A1A1AA");
                paintAccentCyan.setColor(discColor);
                paintAccentCyan.setAlpha(Math.min(255, (int) (alpha * 60)));
                canvas.drawRoundRect(artRectF, IslandConfig.dpToPx(4.5f), IslandConfig.dpToPx(4.5f), paintAccentCyan);
                paintAccentCyan.setAlpha(intAlpha);
                drawMusicNoteIcon(canvas, iconCenterX, centerY, IslandConfig.dpToPx(11), paintAccentCyan);
                canvas.restore();
            }

            if (IslandConfig.mediaShowWaveform) {
                renderAudioBars(canvas, barsStartX, centerY, alpha);
            } else {
                paintTextPrimary.setTextSize(IslandConfig.spToPx(11f));
                paintTextPrimary.setTextAlign(textAlign);
                paintTextPrimary.setColor(Color.WHITE);
                paintTextPrimary.setAlpha(intAlpha);
                canvas.drawText(truncate(IslandState.mediaTitle, 10), textCenterX, centerY + IslandConfig.dpToPx(4), paintTextPrimary);
            }

        } else if (renderType == IslandState.STATE_VOLUME) {
            paintAccentCyan.setAlpha(intAlpha);
            drawSpeakerIcon(canvas, iconCenterX, centerY, IslandConfig.dpToPx(12), IslandState.volumePercent, paintAccentCyan);

            paintTextPrimary.setTextSize(IslandConfig.spToPx(11.5f));
            paintTextPrimary.setTextAlign(textAlign);
            paintTextPrimary.setAlpha(intAlpha);
            canvas.drawText(IslandState.volumePercent + "%", textCenterX, centerY + IslandConfig.dpToPx(4), paintTextPrimary);

        } else if (renderType == IslandState.STATE_RINGER) {
            paintAccentAmber.setAlpha(intAlpha);
            drawBellIcon(canvas, iconCenterX, centerY, IslandConfig.dpToPx(12), IslandState.ringerLabel, paintAccentAmber);

            paintTextPrimary.setTextSize(IslandConfig.spToPx(11.5f));
            paintTextPrimary.setTextAlign(textAlign);
            paintTextPrimary.setAlpha(intAlpha);
            canvas.drawText(IslandState.ringerLabel, textCenterX, centerY + IslandConfig.dpToPx(4), paintTextPrimary);

        } else if (renderType == IslandState.STATE_NOTIFICATION) {
            float iconSize = IslandConfig.dpToPx(19);
            float iconLeft = iconCenterX - iconSize / 2.0f;
            float iconTop = centerY - iconSize / 2.0f;
            artRectF.set(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);

            Bitmap icon = IslandState.notifAppIcon;
            if (icon != null && !icon.isRecycled()) {
                paintArtBitmap.setAlpha(intAlpha);
                canvas.drawBitmap(icon, null, artRectF, paintArtBitmap);
            } else {
                paintAccentAmber.setAlpha(intAlpha);
                drawMessageIcon(canvas, iconCenterX, centerY, IslandConfig.dpToPx(11), paintAccentAmber);
            }

            // Available width for content on the other side of the punch hole
            float availW = Math.abs(curW - iconCenterX) - IslandConfig.dpToPx(34);
            String title = IslandState.notifTitle;
            if (title != null && !title.isEmpty() && !title.equalsIgnoreCase("Notification") && availW >= IslandConfig.dpToPx(32)) {
                paintTextPrimary.setTextSize(IslandConfig.spToPx(11f));
                paintTextPrimary.setTextAlign(textAlign);
                paintTextPrimary.setColor(Color.WHITE);
                paintTextPrimary.setAlpha(intAlpha);
                int fitCount = paintTextPrimary.breakText(title, true, availW, null);
                String displayTitle = title;
                if (fitCount < title.length() && fitCount > 1) {
                    displayTitle = title.substring(0, Math.max(1, fitCount - 1)) + "…";
                }
                canvas.drawText(displayTitle, textCenterX, centerY + IslandConfig.dpToPx(4), paintTextPrimary);
            } else {
                // Subtle alert notification dot when space is compact
                paintAccentAmber.setAlpha(intAlpha);
                canvas.drawCircle(textCenterX, centerY, IslandConfig.dpToPx(3.2f), paintAccentAmber);
            }

        } else if (renderType == IslandState.STATE_PROGRESS) {
            float iconSize = IslandConfig.dpToPx(19);
            float iconLeft = iconCenterX - iconSize / 2.0f;
            float iconTop = centerY - iconSize / 2.0f;
            artRectF.set(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);

            if (IslandState.isProgressComplete) {
                paintAccentGreen.setAlpha(intAlpha);
                drawCheckmarkIcon(canvas, iconCenterX, centerY, IslandConfig.dpToPx(12), paintAccentGreen);

                paintTextPrimary.setTextSize(IslandConfig.spToPx(11.5f));
                paintTextPrimary.setTextAlign(textAlign);
                paintTextPrimary.setColor(Color.parseColor("#34D399"));
                paintTextPrimary.setAlpha(intAlpha);
                canvas.drawText("Done", textCenterX, centerY + IslandConfig.dpToPx(4), paintTextPrimary);
                paintTextPrimary.setColor(Color.WHITE);
            } else {
                Bitmap icon = IslandState.progressAppIcon;
                if (icon != null && !icon.isRecycled()) {
                    paintArtBitmap.setAlpha(intAlpha);
                    canvas.drawBitmap(icon, null, artRectF, paintArtBitmap);
                } else {
                    paintAccentCyan.setAlpha(intAlpha);
                    drawDownloadIcon(canvas, iconCenterX, centerY, IslandConfig.dpToPx(12), paintAccentCyan);
                }

                paintTextPrimary.setTextSize(IslandConfig.spToPx(12.0f));
                paintTextPrimary.setTextAlign(textAlign);
                paintTextPrimary.setColor(Color.parseColor("#38BDF8"));
                paintTextPrimary.setAlpha(intAlpha);
                canvas.drawText(IslandState.progressPct + "%", textCenterX, centerY + IslandConfig.dpToPx(4), paintTextPrimary);
                paintTextPrimary.setColor(Color.WHITE);
            }

        } else if (renderType == IslandState.STATE_TORCH) {
            paintAccentAmber.setAlpha(intAlpha);
            drawTorchIcon(canvas, iconCenterX, centerY, IslandConfig.dpToPx(11), paintAccentAmber);

            paintTextPrimary.setTextSize(IslandConfig.spToPx(11.5f));
            paintTextPrimary.setTextAlign(textAlign);
            paintTextPrimary.setAlpha(intAlpha);
            canvas.drawText("Torch", textCenterX, centerY + IslandConfig.dpToPx(4), paintTextPrimary);
        }
    }

    private void renderAudioBars(Canvas canvas, float startX, float centerY, float alpha) {
        int baseColor = Color.parseColor("#38BDF8");
        if ("auto".equalsIgnoreCase(IslandConfig.mediaPulseColor)) {
            if (IslandState.mediaDominantColor != Color.TRANSPARENT) {
                baseColor = IslandState.mediaDominantColor;
            }
        } else if (IslandConfig.mediaPulseColor != null && !IslandConfig.mediaPulseColor.isEmpty()) {
            try { baseColor = Color.parseColor(IslandConfig.mediaPulseColor); } catch (Throwable ignored) {}
        }
        if (paintAudioPulse == null) {
            paintAudioPulse = new Paint(Paint.ANTI_ALIAS_FLAG);
            paintAudioPulse.setStyle(Paint.Style.FILL);
        }
        paintAudioPulse.setColor(baseColor);
        paintAudioPulse.setAlpha(Math.min(255, Math.max(0, (int) (alpha * 255))));

        long now = SystemClock.uptimeMillis();
        if (now - lastWaveStep > 85 && IslandState.isMediaPlaying) {
            lastWaveStep = now;
            for (int i = 0; i < 4; i++) {
                barTargets[i] = 0.25f + (float) (Math.random() * 0.75f);
            }
        }

        float barW = IslandConfig.dpToPx(2.2f);
        float barGap = IslandConfig.dpToPx(2.0f);
        float maxH = IslandConfig.dpToPx(13f);

        for (int i = 0; i < 4; i++) {
            if (IslandState.isMediaPlaying) {
                barHeights[i] += (barTargets[i] - barHeights[i]) * 0.35f;
            } else {
                barHeights[i] += (0.20f - barHeights[i]) * 0.2f;
            }
            float bH = Math.max(IslandConfig.dpToPx(2.5f), barHeights[i] * maxH);
            float bx = startX + i * (barW + barGap);
            float by = centerY - (bH / 2.0f);
            canvas.drawRoundRect(new RectF(bx, by, bx + barW, by + bH), IslandConfig.dpToPx(1.2f), IslandConfig.dpToPx(1.2f), paintAudioPulse);
        }
    }

    private void renderExpandedContent(Canvas canvas, float curW, float curH, float alpha) {
        int intAlpha = Math.min(255, Math.max(0, (int) (alpha * 255)));
        if (intAlpha <= 0) return;

        paintTextPrimary.setAlpha(intAlpha);
        paintTextSecondary.setAlpha(intAlpha);
        paintTextTertiary.setAlpha(intAlpha);

        int renderType = (IslandState.activeIslandType != IslandState.STATE_IDLE ? IslandState.activeIslandType : IslandState.currentIsland);

        if (renderType == IslandState.STATE_CHARGING) {
            paintAccentGreen.setAlpha(intAlpha);
            drawBoltIcon(canvas, IslandConfig.dpToPx(26), IslandConfig.dpToPx(32), IslandConfig.dpToPx(14), paintAccentGreen);

            paintTextPrimary.setTextSize(IslandConfig.spToPx(14f));
            paintTextPrimary.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("Charging", IslandConfig.dpToPx(44), IslandConfig.dpToPx(36), paintTextPrimary);

            paintTextPrimary.setTextAlign(Paint.Align.RIGHT);
            paintTextPrimary.setColor(Color.parseColor("#34D399"));
            canvas.drawText(IslandState.batteryPct + "%", curW - IslandConfig.dpToPx(20), IslandConfig.dpToPx(36), paintTextPrimary);
            paintTextPrimary.setColor(Color.WHITE);

            float barY = IslandConfig.dpToPx(56);
            float barW = curW - IslandConfig.dpToPx(40);
            paintTrackBg.setColor(Color.argb(45, 255, 255, 255));
            canvas.drawRoundRect(new RectF(IslandConfig.dpToPx(20), barY, IslandConfig.dpToPx(20) + barW, barY + IslandConfig.dpToPx(6)), IslandConfig.dpToPx(3), IslandConfig.dpToPx(3), paintTrackBg);

            float fillRatio = Math.max(0.04f, Math.min(1.0f, IslandState.batteryPct / 100f));
            float fillW = barW * fillRatio;
            canvas.drawRoundRect(new RectF(IslandConfig.dpToPx(20), barY, IslandConfig.dpToPx(20) + fillW, barY + IslandConfig.dpToPx(6)), IslandConfig.dpToPx(3), IslandConfig.dpToPx(3), paintAccentGreen);

            float bottomY = barY + IslandConfig.dpToPx(24);
            paintTextSecondary.setTextSize(IslandConfig.spToPx(11.5f));
            paintTextSecondary.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(IslandState.chargeWattStr + " · " + IslandState.chargeCurrentStr, IslandConfig.dpToPx(20), bottomY, paintTextSecondary);

            paintTextTertiary.setTextSize(IslandConfig.spToPx(11f));
            paintTextTertiary.setTextAlign(Paint.Align.RIGHT);
            String rightSub = !IslandState.batteryTempStr.isEmpty() ? IslandState.batteryTempStr : "";
            canvas.drawText(rightSub, curW - IslandConfig.dpToPx(20), bottomY, paintTextTertiary);

        } else if (renderType == IslandState.STATE_MEDIA) {
            float artSize = IslandConfig.dpToPx(48);
            float artTop = IslandConfig.dpToPx(20);
            artRectF.set(IslandConfig.dpToPx(16), artTop, IslandConfig.dpToPx(16) + artSize, artTop + artSize);

            if (IslandState.currentCardArt != null && !IslandState.currentCardArt.isRecycled()) {
                canvas.save();
                artClipPath.reset();
                artClipPath.addRoundRect(artRectF, IslandConfig.dpToPx(10), IslandConfig.dpToPx(10), Path.Direction.CW);
                canvas.clipPath(artClipPath);
                paintArtBitmap.setAlpha(intAlpha);
                canvas.drawBitmap(IslandState.currentCardArt, null, artRectF, paintArtBitmap);
                canvas.restore();
            } else {
                paintTrackBg.setColor(Color.argb(55, 255, 255, 255));
                canvas.drawRoundRect(artRectF, IslandConfig.dpToPx(10), IslandConfig.dpToPx(10), paintTrackBg);
                paintAccentCyan.setAlpha(intAlpha);
                drawMusicNoteIcon(canvas, artRectF.centerX(), artRectF.centerY(), IslandConfig.dpToPx(18), paintAccentCyan);
            }

            paintTextPrimary.setTextSize(IslandConfig.spToPx(13.5f));
            paintTextPrimary.setTextAlign(Paint.Align.LEFT);
            float textLeft = IslandConfig.dpToPx(74);
            canvas.drawText(truncate(IslandState.mediaTitle, 24), textLeft, artTop + IslandConfig.dpToPx(20), paintTextPrimary);

            paintTextSecondary.setTextSize(IslandConfig.spToPx(11.5f));
            canvas.drawText(truncate(IslandState.mediaArtist, 26), textLeft, artTop + IslandConfig.dpToPx(38), paintTextSecondary);

            float btnY = artTop + artSize + IslandConfig.dpToPx(24);
            float centerX = curW / 2.0f;
            paintAccentCyan.setAlpha(intAlpha);
            drawMediaPrevIcon(canvas, centerX - IslandConfig.dpToPx(52), btnY, IslandConfig.dpToPx(14), paintAccentCyan);
            drawMediaPlayPauseIcon(canvas, centerX, btnY, IslandConfig.dpToPx(18), IslandState.isMediaPlaying, paintAccentCyan);
            drawMediaNextIcon(canvas, centerX + IslandConfig.dpToPx(52), btnY, IslandConfig.dpToPx(14), paintAccentCyan);

            float barY = curH - IslandConfig.dpToPx(24);
            float barLeft = IslandConfig.dpToPx(62);
            float barW = curW - IslandConfig.dpToPx(124);

            long pos = IslandState.mediaTrackPosition;
            if (IslandState.isMediaPlaying && IslandState.mediaPositionUpdateTime > 0) {
                pos += (SystemClock.elapsedRealtime() - IslandState.mediaPositionUpdateTime);
            }
            if (IslandState.mediaTrackDuration > 0 && pos > IslandState.mediaTrackDuration) pos = IslandState.mediaTrackDuration;

            paintTextTertiary.setTextSize(IslandConfig.spToPx(10f));
            paintTextTertiary.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(MediaMonitor.formatTimeMs(pos), IslandConfig.dpToPx(16), barY + IslandConfig.dpToPx(3.5f), paintTextTertiary);

            paintTextTertiary.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(MediaMonitor.formatTimeMs(IslandState.mediaTrackDuration), curW - IslandConfig.dpToPx(16), barY + IslandConfig.dpToPx(3.5f), paintTextTertiary);

            paintTrackBg.setColor(Color.argb(45, 255, 255, 255));
            canvas.drawRoundRect(new RectF(barLeft, barY, barLeft + barW, barY + IslandConfig.dpToPx(4)), IslandConfig.dpToPx(2), IslandConfig.dpToPx(2), paintTrackBg);

            float prog = (IslandState.mediaTrackDuration > 0) ? Math.max(0f, Math.min(1f, pos / (float) IslandState.mediaTrackDuration)) : 0f;
            float fillW = barW * prog;
            int pulseTint = (IslandState.mediaDominantColor != Color.TRANSPARENT) ? IslandState.mediaDominantColor : Color.parseColor("#38BDF8");
            paintAccentCyan.setColor(pulseTint);
            paintAccentCyan.setAlpha(intAlpha);
            canvas.drawRoundRect(new RectF(barLeft, barY, barLeft + fillW, barY + IslandConfig.dpToPx(4)), IslandConfig.dpToPx(2), IslandConfig.dpToPx(2), paintAccentCyan);

        } else if (renderType == IslandState.STATE_PROGRESS) {
            float iconSize = IslandConfig.dpToPx(24);
            float iconLeft = IslandConfig.dpToPx(20);
            float iconTop = IslandConfig.dpToPx(22);
            artRectF.set(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);

            Bitmap progIcon = IslandState.progressAppIcon;
            if (progIcon != null && !progIcon.isRecycled()) {
                paintArtBitmap.setAlpha(intAlpha);
                canvas.drawBitmap(progIcon, null, artRectF, paintArtBitmap);
            } else if (IslandState.isProgressComplete) {
                drawCheckmarkIcon(canvas, iconLeft + iconSize / 2f, iconTop + iconSize / 2f, IslandConfig.dpToPx(13), paintAccentGreen);
            } else {
                drawDownloadIcon(canvas, iconLeft + iconSize / 2f, iconTop + iconSize / 2f, IslandConfig.dpToPx(13), paintAccentCyan);
            }

            float textLeft = iconLeft + iconSize + IslandConfig.dpToPx(10);
            paintTextPrimary.setTextSize(IslandConfig.spToPx(13.5f));
            paintTextPrimary.setTextAlign(Paint.Align.LEFT);
            paintTextPrimary.setColor(Color.WHITE);
            paintTextPrimary.setAlpha(intAlpha);
            canvas.drawText(truncate(IslandState.progressTitle, 24), textLeft, iconTop + IslandConfig.dpToPx(16), paintTextPrimary);

            paintTextPrimary.setTextAlign(Paint.Align.RIGHT);
            paintTextPrimary.setColor(IslandState.isProgressComplete ? Color.parseColor("#34D399") : Color.parseColor("#38BDF8"));
            canvas.drawText(IslandState.isProgressComplete ? "Done" : (IslandState.progressPct + "%"), curW - IslandConfig.dpToPx(20), iconTop + IslandConfig.dpToPx(16), paintTextPrimary);
            paintTextPrimary.setColor(Color.WHITE);

            float barY = iconTop + iconSize + IslandConfig.dpToPx(14);
            float barW = curW - IslandConfig.dpToPx(40);
            paintTrackBg.setColor(Color.argb(45, 255, 255, 255));
            canvas.drawRoundRect(new RectF(IslandConfig.dpToPx(20), barY, IslandConfig.dpToPx(20) + barW, barY + IslandConfig.dpToPx(6)), IslandConfig.dpToPx(3), IslandConfig.dpToPx(3), paintTrackBg);

            float fillRatio = Math.max(0.04f, Math.min(1.0f, IslandState.progressPct / 100f));
            float fillW = barW * fillRatio;
            paintAccentCyan.setColor(IslandState.isProgressComplete ? Color.parseColor("#34D399") : Color.parseColor("#38BDF8"));
            canvas.drawRoundRect(new RectF(IslandConfig.dpToPx(20), barY, IslandConfig.dpToPx(20) + fillW, barY + IslandConfig.dpToPx(6)), IslandConfig.dpToPx(3), IslandConfig.dpToPx(3), paintAccentCyan);

            float bottomY = barY + IslandConfig.dpToPx(18);
            paintTextTertiary.setTextSize(IslandConfig.spToPx(11f));
            paintTextTertiary.setTextAlign(Paint.Align.LEFT);
            paintTextTertiary.setColor(Color.parseColor("#A1A1AA"));
            paintTextTertiary.setAlpha(intAlpha);
            canvas.drawText(IslandState.progressSubtitle, IslandConfig.dpToPx(20), bottomY, paintTextTertiary);

        } else if (renderType == IslandState.STATE_NOTIFICATION) {
            float iconSize = IslandConfig.dpToPx(24);
            float iconLeft = IslandConfig.dpToPx(20);
            float iconTop = IslandConfig.dpToPx(22);
            artRectF.set(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);

            Bitmap icon = IslandState.notifAppIcon;
            if (icon != null && !icon.isRecycled()) {
                paintArtBitmap.setAlpha(intAlpha);
                canvas.drawBitmap(icon, null, artRectF, paintArtBitmap);
            } else {
                paintAccentAmber.setAlpha(intAlpha);
                drawMessageIcon(canvas, iconLeft + iconSize / 2f, iconTop + iconSize / 2f, IslandConfig.dpToPx(13), paintAccentAmber);
            }

            float textLeft = iconLeft + iconSize + IslandConfig.dpToPx(10);
            paintTextPrimary.setTextSize(IslandConfig.spToPx(13.5f));
            paintTextPrimary.setTextAlign(Paint.Align.LEFT);
            paintTextPrimary.setColor(Color.WHITE);
            paintTextPrimary.setAlpha(intAlpha);
            String title = (IslandState.notifTitle != null && !IslandState.notifTitle.isEmpty())
                    ? IslandState.notifTitle : "Notification";
            canvas.drawText(truncate(title, 24), textLeft, iconTop + IslandConfig.dpToPx(16), paintTextPrimary);

            paintTextTertiary.setTextSize(IslandConfig.spToPx(10.5f));
            paintTextTertiary.setTextAlign(Paint.Align.RIGHT);
            paintTextTertiary.setColor(Color.parseColor("#71717A"));
            paintTextTertiary.setAlpha(intAlpha);
            canvas.drawText("now", curW - IslandConfig.dpToPx(20), iconTop + IslandConfig.dpToPx(16), paintTextTertiary);

            float contentY = iconTop + iconSize + IslandConfig.dpToPx(18);
            paintTextSecondary.setTextSize(IslandConfig.spToPx(11.5f));
            paintTextSecondary.setTextAlign(Paint.Align.LEFT);
            paintTextSecondary.setColor(Color.parseColor("#E4E4E7"));
            paintTextSecondary.setAlpha(intAlpha);
            String content = (IslandState.notifContent != null && !IslandState.notifContent.isEmpty())
                    ? IslandState.notifContent : "New message received";
            canvas.drawText(truncate(content, 36), IslandConfig.dpToPx(20), contentY, paintTextSecondary);

        } else if (renderType == IslandState.STATE_VOLUME) {
            paintAccentCyan.setAlpha(intAlpha);
            drawSpeakerIcon(canvas, IslandConfig.dpToPx(26), IslandConfig.dpToPx(32), IslandConfig.dpToPx(14), IslandState.volumePercent, paintAccentCyan);

            paintTextPrimary.setTextSize(IslandConfig.spToPx(14f));
            paintTextPrimary.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("Media Volume", IslandConfig.dpToPx(44), IslandConfig.dpToPx(36), paintTextPrimary);

            paintTextPrimary.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(IslandState.volumePercent + "%", curW - IslandConfig.dpToPx(20), IslandConfig.dpToPx(36), paintTextPrimary);

            float barY = IslandConfig.dpToPx(56);
            float barW = curW - IslandConfig.dpToPx(40);
            paintTrackBg.setColor(Color.argb(45, 255, 255, 255));
            canvas.drawRoundRect(new RectF(IslandConfig.dpToPx(20), barY, IslandConfig.dpToPx(20) + barW, barY + IslandConfig.dpToPx(6)), IslandConfig.dpToPx(3), IslandConfig.dpToPx(3), paintTrackBg);

            float fillRatio = Math.max(0.04f, Math.min(1.0f, IslandState.volumePercent / 100f));
            float fillW = barW * fillRatio;
            canvas.drawRoundRect(new RectF(IslandConfig.dpToPx(20), barY, IslandConfig.dpToPx(20) + fillW, barY + IslandConfig.dpToPx(6)), IslandConfig.dpToPx(3), IslandConfig.dpToPx(3), paintAccentCyan);
        }
    }

    private int getActiveAccentColor() {
        switch (IslandState.currentIsland) {
            case IslandState.STATE_MEDIA:
                return (IslandState.mediaDominantColor != Color.TRANSPARENT) ? IslandState.mediaDominantColor : Color.parseColor("#38BDF8");
            case IslandState.STATE_CHARGING:
                return Color.parseColor("#34D399");
            case IslandState.STATE_VOLUME:
                return Color.parseColor("#A855F7");
            case IslandState.STATE_NOTIFICATION:
                return Color.parseColor("#60A5FA");
            case IslandState.STATE_PROGRESS:
                return IslandState.isProgressComplete ? Color.parseColor("#34D399") : Color.parseColor("#38BDF8");
            default:
                return Color.TRANSPARENT;
        }
    }

    private void buildSmoothSquirclePath(Path path, float w, float h, float r, float smoothing) {
        path.reset();
        float minDim = Math.min(w, h);
        r = Math.min(r, minDim / 2.0f);
        float s = Math.min(Math.max(smoothing, 0.0f), 1.0f);
        float a = Math.min(minDim / 2.0f, r * (1.0f + s * 0.45f));
        float k = 0.55228475f;
        float m = a * (1.0f - k);

        path.moveTo(a, 0);
        path.lineTo(w - a, 0);
        path.cubicTo(w - m, 0, w, m, w, a);
        path.lineTo(w, h - a);
        path.cubicTo(w, h - m, w - m, h, w - a, h);
        path.lineTo(a, h);
        path.cubicTo(m, h, 0, h - m, 0, h - a);
        path.lineTo(0, a);
        path.cubicTo(0, m, m, 0, a, 0);
        path.close();
    }

    private void drawBoltIcon(Canvas canvas, float cx, float cy, float size, Paint paint) {
        float h = size / 2.0f;
        Path p = new Path();
        p.moveTo(cx + h * 0.15f, cy - h);
        p.lineTo(cx - h * 0.65f, cy + h * 0.05f);
        p.lineTo(cx - h * 0.05f, cy + h * 0.05f);
        p.lineTo(cx - h * 0.25f, cy + h);
        p.lineTo(cx + h * 0.65f, cy - h * 0.15f);
        p.lineTo(cx + h * 0.05f, cy - h * 0.15f);
        p.close();
        canvas.drawPath(p, paint);
    }

    private void drawMusicNoteIcon(Canvas canvas, float cx, float cy, float size, Paint paint) {
        float h = size / 2.0f;
        Path p = new Path();
        p.moveTo(cx - h * 0.3f, cy + h * 0.4f);
        p.lineTo(cx - h * 0.3f, cy - h * 0.7f);
        p.lineTo(cx + h * 0.7f, cy - h * 0.3f);
        p.lineTo(cx + h * 0.7f, cy + h * 0.6f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(IslandConfig.dpToPx(1.6f));
        canvas.drawPath(p, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx - h * 0.5f, cy + h * 0.4f, IslandConfig.dpToPx(2.4f), paint);
        canvas.drawCircle(cx + h * 0.5f, cy + h * 0.6f, IslandConfig.dpToPx(2.4f), paint);
    }

    private void drawSpeakerIcon(Canvas canvas, float cx, float cy, float size, int vol, Paint paint) {
        float h = size / 2.0f;
        Path p = new Path();
        p.moveTo(cx - h * 0.6f, cy - h * 0.35f);
        p.lineTo(cx - h * 0.2f, cy - h * 0.35f);
        p.lineTo(cx + h * 0.3f, cy - h * 0.8f);
        p.lineTo(cx + h * 0.3f, cy + h * 0.8f);
        p.lineTo(cx - h * 0.2f, cy + h * 0.35f);
        p.lineTo(cx - h * 0.6f, cy + h * 0.35f);
        p.close();
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(p, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(IslandConfig.dpToPx(1.5f));
        if (vol > 35) {
            canvas.drawArc(new RectF(cx + h * 0.1f, cy - h * 0.5f, cx + h * 0.8f, cy + h * 0.5f), -45, 90, false, paint);
        }
        if (vol > 70) {
            canvas.drawArc(new RectF(cx + h * 0.3f, cy - h * 0.85f, cx + h * 1.2f, cy + h * 0.85f), -45, 90, false, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawBellIcon(Canvas canvas, float cx, float cy, float size, String label, Paint paint) {
        float h = size / 2.0f;
        Path p = new Path();
        p.moveTo(cx - h * 0.55f, cy + h * 0.45f);
        p.lineTo(cx + h * 0.55f, cy + h * 0.45f);
        p.lineTo(cx + h * 0.45f, cy + h * 0.2f);
        p.cubicTo(cx + h * 0.45f, cy - h * 0.4f, cx - h * 0.45f, cy - h * 0.4f, cx - h * 0.45f, cy + h * 0.2f);
        p.close();
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(p, paint);
        canvas.drawCircle(cx, cy + h * 0.7f, IslandConfig.dpToPx(1.8f), paint);

        if ("Silent".equalsIgnoreCase(label)) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(IslandConfig.dpToPx(1.6f));
            canvas.drawLine(cx - h * 0.7f, cy - h * 0.7f, cx + h * 0.7f, cy + h * 0.7f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawMessageIcon(Canvas canvas, float cx, float cy, float size, Paint paint) {
        float h = size / 2.0f;
        RectF r = new RectF(cx - h * 0.75f, cy - h * 0.55f, cx + h * 0.75f, cy + h * 0.55f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(IslandConfig.dpToPx(1.5f));
        canvas.drawRoundRect(r, IslandConfig.dpToPx(3f), IslandConfig.dpToPx(3f), paint);
        canvas.drawLine(r.left, r.top, cx, cy + h * 0.05f, paint);
        canvas.drawLine(r.right, r.top, cx, cy + h * 0.05f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawDownloadIcon(Canvas canvas, float cx, float cy, float size, Paint paint) {
        float h = size / 2.0f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(IslandConfig.dpToPx(1.8f));
        // Stem
        canvas.drawLine(cx, cy - h * 0.75f, cx, cy + h * 0.35f, paint);
        // Arrowhead
        float headW = h * 0.5f;
        canvas.drawLine(cx - headW, cy + h * 0.35f - headW, cx, cy + h * 0.35f, paint);
        canvas.drawLine(cx + headW, cy + h * 0.35f - headW, cx, cy + h * 0.35f, paint);
        // Tray
        float trayY = cy + h * 0.8f;
        canvas.drawLine(cx - h * 0.75f, trayY, cx + h * 0.75f, trayY, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawCheckmarkIcon(Canvas canvas, float cx, float cy, float size, Paint paint) {
        float h = size / 2.0f;
        Path p = new Path();
        p.moveTo(cx - h * 0.7f, cy);
        p.lineTo(cx - h * 0.2f, cy + h * 0.55f);
        p.lineTo(cx + h * 0.75f, cy - h * 0.55f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(IslandConfig.dpToPx(2.2f));
        canvas.drawPath(p, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawTorchIcon(Canvas canvas, float cx, float cy, float size, Paint paint) {
        float h = size / 2.0f;
        paint.setStyle(Paint.Style.FILL);
        RectF head = new RectF(cx - h * 0.45f, cy - h * 0.7f, cx + h * 0.45f, cy - h * 0.3f);
        canvas.drawRoundRect(head, IslandConfig.dpToPx(2f), IslandConfig.dpToPx(2f), paint);
        RectF body = new RectF(cx - h * 0.3f, cy - h * 0.3f, cx + h * 0.3f, cy + h * 0.7f);
        canvas.drawRoundRect(body, IslandConfig.dpToPx(2f), IslandConfig.dpToPx(2f), paint);
    }

    private void drawMediaPlayPauseIcon(Canvas canvas, float cx, float cy, float size, boolean isPlaying, Paint paint) {
        float h = size / 2.0f;
        if (isPlaying) {
            float barW = IslandConfig.dpToPx(3.2f);
            float barH = h * 1.3f;
            float gap = IslandConfig.dpToPx(4.5f);
            canvas.drawRoundRect(new RectF(cx - gap - barW, cy - barH / 2, cx - gap, cy + barH / 2), IslandConfig.dpToPx(1.5f), IslandConfig.dpToPx(1.5f), paint);
            canvas.drawRoundRect(new RectF(cx + gap, cy - barH / 2, cx + gap + barW, cy + barH / 2), IslandConfig.dpToPx(1.5f), IslandConfig.dpToPx(1.5f), paint);
        } else {
            Path p = new Path();
            p.moveTo(cx - h * 0.45f, cy - h * 0.75f);
            p.lineTo(cx + h * 0.75f, cy);
            p.lineTo(cx - h * 0.45f, cy + h * 0.75f);
            p.close();
            canvas.drawPath(p, paint);
        }
    }

    private void drawMediaPrevIcon(Canvas canvas, float cx, float cy, float size, Paint paint) {
        float h = size / 2.0f;
        Path p = new Path();
        p.moveTo(cx + h * 0.5f, cy - h * 0.65f);
        p.lineTo(cx - h * 0.2f, cy);
        p.lineTo(cx + h * 0.5f, cy + h * 0.65f);
        p.close();
        canvas.drawPath(p, paint);
        canvas.drawRect(cx - h * 0.5f, cy - h * 0.65f, cx - h * 0.3f, cy + h * 0.65f, paint);
    }

    private void drawMediaNextIcon(Canvas canvas, float cx, float cy, float size, Paint paint) {
        float h = size / 2.0f;
        Path p = new Path();
        p.moveTo(cx - h * 0.5f, cy - h * 0.65f);
        p.lineTo(cx + h * 0.2f, cy);
        p.lineTo(cx - h * 0.5f, cy + h * 0.65f);
        p.close();
        canvas.drawPath(p, paint);
        canvas.drawRect(cx + h * 0.3f, cy - h * 0.65f, cx + h * 0.5f, cy + h * 0.65f, paint);
    }

    private void performHaptic(int type) {
        if (!IslandConfig.enableHaptics) return;
        try {
            Context c = HyperRingOverlay.context != null ? HyperRingOverlay.context : HyperRingOverlay.sysContext;
            if (c != null) {
                Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) {
                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                        v.vibrate(VibrationEffect.createOneShot(type == 1 ? 25 : 12, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        v.vibrate(type == 1 ? 25 : 12);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }
}
