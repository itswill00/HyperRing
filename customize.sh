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
    cat > "$MODPATH/state/config.json" << 'C_EOF'
{
  "cutout_x": 540,
  "cutout_y": 52,
  "cutout_radius": 36,
  "enable_media": true,
  "enable_charging": true,
  "enable_hyperdl": true,
  "enable_hypercore": true,
  "spring_stiffness": 360.0,
  "spring_damping": 0.76,
  "auto_expand_charging": true,
  "expand_timeout_ms": 3500
}
C_EOF
fi

chmod 644 "$MODPATH/bin/hyperring.dex"
chmod 755 "$MODPATH/service.sh"
chmod 755 "$MODPATH/action.sh"
chmod 755 "$MODPATH/uninstall.sh"
chmod 644 "$MODPATH/webroot/index.html" 2>/dev/null || true
chmod 644 "$MODPATH/module.prop"

chcon -R u:object_r:system_file:s0 "$MODPATH" 2>/dev/null || true

cmd appops set --uid 0 SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
cmd appops set --uid 1000 SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
cmd appops set --uid 2000 SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
appops set android SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
appops set com.android.shell SYSTEM_ALERT_WINDOW allow 2>/dev/null || true

ui_print "- HyperRing installation complete."
