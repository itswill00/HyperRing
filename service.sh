#!/system/bin/sh
MODDIR="${0%/*}"

until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 3
done
sleep 2

mkdir -p "$MODDIR/state"
chmod 777 "$MODDIR/state"
chcon -R u:object_r:system_file:s0 "$MODDIR/state" 2>/dev/null || true

cmd appops set --uid 0 SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
cmd appops set --uid 1000 SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
cmd appops set --uid 2000 SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
appops set android SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
appops set com.android.shell SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
pm grant com.android.shell android.permission.SYSTEM_ALERT_WINDOW 2>/dev/null || true

for p in $(pgrep -f "com.hyperring.HyperRingOverlay" 2>/dev/null); do
    if [ "$p" != "$$" ] && [ "$p" != "$PPID" ]; then
        kill -9 "$p" 2>/dev/null || true
    fi
done

if [ -f "$MODDIR/bin/hyperring.dex" ]; then
    CLASSPATH="$MODDIR/bin/hyperring.dex" nohup /system/bin/app_process /system/bin com.hyperring.HyperRingOverlay "$MODDIR/state" > "$MODDIR/state/overlay.log" 2>&1 &
fi

sleep 1

for pid in $(pgrep -f "com.hyperring.HyperRingOverlay" 2>/dev/null); do
    echo -1000 > "/proc/$pid/oom_score_adj" 2>/dev/null || true
    chmod 000 "/proc/$pid/oom_score_adj" 2>/dev/null || true
done
