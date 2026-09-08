#!/system/bin/sh
MODDIR="${0%/*}"

mkdir -p "$MODDIR/state"
chmod 777 "$MODDIR/state"

case "$1" in
    charge|test-charge)
        echo "charge" > "$MODDIR/state/trigger.cmd"
        echo "Sent charge trigger"
        exit 0
        ;;
    media|test-media)
        echo "media" > "$MODDIR/state/trigger.cmd"
        echo "Sent media trigger"
        exit 0
        ;;
    volume|test-volume)
        echo "volume" > "$MODDIR/state/trigger.cmd"
        echo "Sent volume trigger"
        exit 0
        ;;
    ringer|test-ringer)
        echo "ringer" > "$MODDIR/state/trigger.cmd"
        echo "Sent ringer trigger"
        exit 0
        ;;
    notif|notification|test-notif)
        echo "notification" > "$MODDIR/state/trigger.cmd"
        echo "Sent notification trigger"
        exit 0
        ;;
    torch|test-torch)
        echo "torch" > "$MODDIR/state/trigger.cmd"
        echo "Sent torch trigger"
        exit 0
        ;;
    hyperdl|download|test-download)
        echo "hyperdl" > "$MODDIR/state/trigger.cmd"
        echo "Sent hyperdl trigger"
        exit 0
        ;;
    calibrate)
        echo "calibrate" > "$MODDIR/state/trigger.cmd"
        echo "Sent calibrate trigger"
        exit 0
        ;;
    calibrate_off|calibrate-off)
        echo "calibrate_off" > "$MODDIR/state/trigger.cmd"
        echo "Sent calibrate_off trigger"
        exit 0
        ;;
    preview-pill|preview:pill)
        echo "preview:pill" > "$MODDIR/state/trigger.cmd"
        echo "Sent preview:pill trigger"
        exit 0
        ;;
    preview-card|preview:card|preview-expanded|preview:expanded)
        echo "preview:expanded" > "$MODDIR/state/trigger.cmd"
        echo "Sent preview:expanded trigger"
        exit 0
        ;;
    preview-reticle|preview:reticle)
        echo "preview:reticle" > "$MODDIR/state/trigger.cmd"
        echo "Sent preview:reticle trigger"
        exit 0
        ;;
    preview-off|preview:off)
        echo "preview:off" > "$MODDIR/state/trigger.cmd"
        echo "Sent preview:off trigger"
        exit 0
        ;;
    expand)
        echo "expand" > "$MODDIR/state/trigger.cmd"
        echo "Sent expand trigger"
        exit 0
        ;;
    collapse)
        echo "collapse" > "$MODDIR/state/trigger.cmd"
        echo "Sent collapse trigger"
        exit 0
        ;;
    idle)
        echo "idle" > "$MODDIR/state/trigger.cmd"
        echo "Sent idle trigger"
        exit 0
        ;;
esac

echo "Restarting HyperRing service..."

cmd appops set --uid 0 SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
cmd appops set --uid 1000 SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
cmd appops set --uid 2000 SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
cmd appops set --uid 0 CAMERA allow 2>/dev/null || true
cmd appops set --uid 1000 CAMERA allow 2>/dev/null || true
cmd appops set --uid 2000 CAMERA allow 2>/dev/null || true
appops set android SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
appops set com.android.shell SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
appops set com.android.shell CAMERA allow 2>/dev/null || true
pm grant com.android.shell android.permission.SYSTEM_ALERT_WINDOW 2>/dev/null || true
pm grant com.android.shell android.permission.CAMERA 2>/dev/null || true

for p in $(pgrep -f "com.hyperring.HyperRingOverlay" 2>/dev/null); do
    if [ "$p" != "$$" ] && [ "$p" != "$PPID" ]; then
        kill -9 "$p" 2>/dev/null || true
    fi
done
sleep 0.3

if [ -f "$MODDIR/bin/hyperring.dex" ]; then
    CLASSPATH="$MODDIR/bin/hyperring.dex" nohup /system/bin/app_process /system/bin com.hyperring.HyperRingOverlay "$MODDIR/state" > "$MODDIR/state/overlay.log" 2>&1 &
fi

sleep 0.8

for pid in $(pgrep -f "com.hyperring.HyperRingOverlay" 2>/dev/null); do
    echo -1000 > "/proc/$pid/oom_score_adj" 2>/dev/null || true
    chmod 000 "/proc/$pid/oom_score_adj" 2>/dev/null || true
done

echo "HyperRing service active."
