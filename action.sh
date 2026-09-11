#!/system/bin/sh
MODDIR="${0%/*}"

mkdir -p "$MODDIR/state"
chmod 777 "$MODDIR/state"

case "$1" in
    charge*|test-charge*)
        echo "$1" > "$MODDIR/state/trigger.cmd"
        echo "Sent trigger: $1"
        exit 0
        ;;
    media*|test-media*)
        echo "$1" > "$MODDIR/state/trigger.cmd"
        echo "Sent trigger: $1"
        exit 0
        ;;
    volume*|test-volume*)
        echo "$1" > "$MODDIR/state/trigger.cmd"
        echo "Sent trigger: $1"
        exit 0
        ;;
    ringer*|test-ringer*)
        echo "$1" > "$MODDIR/state/trigger.cmd"
        echo "Sent trigger: $1"
        exit 0
        ;;
    notif*|notification*|test-notif*)
        echo "$1" > "$MODDIR/state/trigger.cmd"
        echo "Sent trigger: $1"
        exit 0
        ;;
    torch*|test-torch*)
        echo "$1" > "$MODDIR/state/trigger.cmd"
        echo "Sent trigger: $1"
        exit 0
        ;;
    calibrate*)
        echo "$1" > "$MODDIR/state/trigger.cmd"
        echo "Sent trigger: $1"
        exit 0
        ;;
    preview*|test-preview*)
        echo "$1" > "$MODDIR/state/trigger.cmd"
        echo "Sent trigger: $1"
        exit 0
        ;;
    expand*)
        echo "$1" > "$MODDIR/state/trigger.cmd"
        echo "Sent trigger: $1"
        exit 0
        ;;
    collapse*)
        echo "$1" > "$MODDIR/state/trigger.cmd"
        echo "Sent trigger: $1"
        exit 0
        ;;
    idle*)
        echo "$1" > "$MODDIR/state/trigger.cmd"
        echo "Sent trigger: $1"
        exit 0
        ;;
    toggle*)
        STATE_FILE="$MODDIR/state/config.json"
        if [ -f "$STATE_FILE" ]; then
            if grep -qE '"enabled":[ ]*false' "$STATE_FILE"; then
                sed -i -E 's/"enabled":[ ]*false/"enabled": true/' "$STATE_FILE"
                echo "preview:off" > "$MODDIR/state/trigger.cmd"
                echo "HyperRing service: ENABLED"
            else
                sed -i -E 's/"enabled":[ ]*true/"enabled": false/' "$STATE_FILE"
                echo "idle" > "$MODDIR/state/trigger.cmd"
                echo "HyperRing service: DISABLED"
            fi
        else
            echo "Error: config.json not found"
        fi
        exit 0
        ;;
    on|enable)
        STATE_FILE="$MODDIR/state/config.json"
        if [ -f "$STATE_FILE" ]; then
            sed -i -E 's/"enabled":[ ]*false/"enabled": true/' "$STATE_FILE"
            echo "preview:off" > "$MODDIR/state/trigger.cmd"
            echo "HyperRing service: ENABLED"
        fi
        exit 0
        ;;
    off|disable)
        STATE_FILE="$MODDIR/state/config.json"
        if [ -f "$STATE_FILE" ]; then
            sed -i -E 's/"enabled":[ ]*true/"enabled": false/' "$STATE_FILE"
            echo "idle" > "$MODDIR/state/trigger.cmd"
            echo "HyperRing service: DISABLED"
        fi
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

NEW_PID=""
for pid in $(pgrep -f "com.hyperring.HyperRingOverlay" 2>/dev/null); do
    echo -1000 > "/proc/$pid/oom_score_adj" 2>/dev/null || true
    chmod 000 "/proc/$pid/oom_score_adj" 2>/dev/null || true
    NEW_PID="$pid"
done

if [ -n "$NEW_PID" ]; then
    echo "HyperRing service active (PID: $NEW_PID)."
else
    echo "Error: HyperRing daemon failed to start."
    if [ -f "$MODDIR/state/overlay.log" ]; then
        echo "Log output:"
        tail -n 10 "$MODDIR/state/overlay.log"
    fi
fi
