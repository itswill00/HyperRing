#!/system/bin/sh
SKIPUNZIP=0

ui_print "- Installing HyperRing..."

ARCH_ABI=$(getprop ro.product.cpu.abi 2>/dev/null)
if [ "$ARCH" != "arm64" ] && [ "$ARCH_ABI" != "arm64-v8a" ]; then
    abort "! Requires ARM64 architecture"
fi

mkdir -p "$MODPATH/state"
chmod 777 "$MODPATH/state"

PREV_STATE="/data/adb/modules/hyperring/state"
if [ -d "$PREV_STATE" ]; then
    cp -af "$PREV_STATE/config.json" "$MODPATH/state/config.json" 2>/dev/null || true
fi

if [ ! -f "$MODPATH/state/config.json" ]; then
    DEF_X=540
    DEF_Y=55
    DEF_R=28
    WM_SIZE=$(wm size 2>/dev/null | grep -oE '[0-9]+x[0-9]+' | head -n 1)
    if [ -n "$WM_SIZE" ]; then
        W=${WM_SIZE%x*}
        if [ "$W" -gt 0 ]; then
            DEF_X=$((W / 2))
        fi
    fi
    cat > "$MODPATH/state/config.json" << C_EOF
{
  "enabled": true,
  "cutout_x": $DEF_X,
  "cutout_y": $DEF_Y,
  "cutout_radius": $DEF_R,
  "pill_alignment": "center",
  "notch_mode": false,
  "x_offset": 0,
  "y_offset": 0,
  "pill_width": 0,
  "pill_height": 0,
  "card_width": 0,
  "card_height": 0,
  "card_radius": 24,
  "card_y_offset": 0,
  "card_position_mode": "below",
  "enable_media": true,
  "enable_charging": true,
  "enable_volume": true,
  "enable_ringer": true,
  "enable_notifications": true,
  "enable_torch": true,
  "enable_haptics": true,
  "stealth_ring_idle": false,
  "hide_in_landscape": true,
  "spring_stiffness": 380.0,
  "spring_damping": 0.78,
  "auto_expand_charging": true,
  "auto_expand_media": false,
  "auto_expand_notification": false,
  "expand_timeout_ms": 3500
}
C_EOF
fi

chmod 644 "$MODPATH/bin/hyperring.dex"
chmod 755 "$MODPATH/service.sh"
chmod 755 "$MODPATH/action.sh"
chmod 755 "$MODPATH/toggle.sh" 2>/dev/null || true
chmod 755 "$MODPATH/uninstall.sh"
chmod 644 "$MODPATH/webroot/index.html" 2>/dev/null || true
chmod 644 "$MODPATH/module.prop"

chcon -R u:object_r:system_file:s0 "$MODPATH" 2>/dev/null || true

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

ui_print "- HyperRing installation complete."
