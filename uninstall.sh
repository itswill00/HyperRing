#!/system/bin/sh
for p in $(pgrep -f "com.hyperring.HyperRingOverlay" 2>/dev/null); do
    kill -9 "$p" 2>/dev/null || true
done
rm -rf /data/adb/modules/hyperring/state 2>/dev/null || true
