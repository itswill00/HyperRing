# HyperRing

Physics-driven dynamic punch-hole island for rooted Android devices.

HyperRing transforms the front-facing camera cutout into a contextual status island. Written in native Java and executed under Android's `app_process`, it attaches directly to `WindowManager` without requiring accessibility services, companion apps, or background framework bloat.

## Architecture

- **RK4 spring physics**: Runge-Kutta 4th-order integrator driving spatial springs (width, height, corner radius, content alpha) at full display refresh rate.
- **True zero-idle CPU**: Choreographer VSYNC callbacks halt completely when animations settle. During idle and static display, overlay CPU usage sits at 0.0%.
- **Status-bar safe expanded card**: Expanded cards float neatly below the physical cutout and status bar, keeping system indicators (clock, network, battery) fully visible and unclipped.
- **Universal hardware auto-detection**: Screen resolution, density, status bar height, and horizontal punch-hole center are probed at runtime. Works out-of-the-box across 720p, 1080p, and 1440p displays.
- **Integrated WebUI**: Bundled single-file Vue 3 interface running inside KernelSU, APatch, and MMRL with live visual simulator, high-contrast alignment reticle, and instant parameter persistence.
- **Low-latency root telemetry**: Direct background monitoring of battery state, media sessions, volume steps, ringer profiles, torch state, and incoming notifications.

## Contextual States

| State | Trigger | Compact Island | Expanded Card |
| :--- | :--- | :--- | :--- |
| **Idle** | Default state | Invisible or subtle cutout ring | Off (touch pass-through) |
| **Charging** | USB power connected | Battery percent and bolt glyph | Power wattage, charge current, battery level bar |
| **Media** | Active audio playback | Music note glyph and audio visualizer | Album disc, track title, artist, seekbar, controls |
| **Volume** | Volume key pressed | Speaker glyph and volume level | Volume slider and audio profile status |
| **Ringer** | Sound mode toggled | Bell glyph and active profile | Audible status and ring mode description |
| **Notification** | Message arrived | App glyph and sender label | Sender, message snippet, arrival timestamp |
| **Torch** | Flashlight activated | Flashlight glyph and status | Flashlight state and toggle reminder |
| **Calibration** | Tuning mode | Center crosshair and alignment ring | Hardware cutout position alignment |

## Repository Layout

```
HyperRing/
├── action.sh             # CLI trigger dispatcher & daemon manager
├── toggle.sh             # Quick on/off toggle shortcut
├── service.sh            # Boot completion background launcher
├── customize.sh          # Module installer & hardware prober
├── uninstall.sh          # Cleanup script
├── module.prop           # Magisk / KernelSU module metadata
├── build.sh              # Standalone DEX compiler & WebUI bundler
├── src/
│   └── HyperRingOverlay.java  # Native daemon & WindowManager overlay
├── state/
│   ├── config.json       # Island geometry & user preferences
│   └── status.json       # Live runtime status & telemetry
├── webroot/
│   └── index.html        # Production single-file WebUI
└── webui/                # Vue 3 source files
```

## Configuration

Settings live in `state/config.json` and update dynamically without restarting the daemon:

```json
{
  "enabled": true,
  "cutout_x": 540,
  "cutout_y": 55,
  "cutout_radius": 28,
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
  "enable_hyperdl": true,
  "enable_hypercore": true,
  "stealth_ring_idle": false,
  "hide_in_landscape": true,
  "spring_stiffness": 380.0,
  "spring_damping": 0.78,
  "auto_expand_charging": true,
  "auto_expand_media": false,
  "auto_expand_notification": false,
  "expand_timeout_ms": 3500
}
```

## Control Commands

HyperRing can be managed directly via shell:

```bash
# Toggle daemon state on / off
su -c "/data/adb/modules/hyperring/toggle.sh"
# or
su -c "/data/adb/modules/hyperring/action.sh toggle"

# Explicit service state
su -c "/data/adb/modules/hyperring/action.sh on"
su -c "/data/adb/modules/hyperring/action.sh off"

# Live preview locking
su -c "/data/adb/modules/hyperring/action.sh preview-pill"
su -c "/data/adb/modules/hyperring/action.sh preview-card"
su -c "/data/adb/modules/hyperring/action.sh preview-reticle"
su -c "/data/adb/modules/hyperring/action.sh preview-off"

# Manual event triggers
su -c "/data/adb/modules/hyperring/action.sh charge"
su -c "/data/adb/modules/hyperring/action.sh media"
su -c "/data/adb/modules/hyperring/action.sh volume"
su -c "/data/adb/modules/hyperring/action.sh ringer"
su -c "/data/adb/modules/hyperring/action.sh torch"
```

## Building from Source

The build pipeline requires Node.js (for WebUI) and Android SDK / `android.jar` + `d8` (or Termux equivalent):

```bash
# Build DEX, bundle WebUI, and generate flashable ZIP:
./build.sh

# Build and immediately deploy to local rooted environment:
./build.sh --deploy

# Re-deploy existing binaries without rebuilding:
./build.sh --deploy-only

# Clean build artifacts:
./build.sh --clean
```

Generated flashable archives are placed in `releases/` and copied to `/sdcard/Download/` for straightforward installation via KernelSU or Magisk.

## System Requirements

- Android 10 or newer (API 29+)
- Root manager: KernelSU, APatch, or Magisk
- Display with camera punch-hole cutout (center, left, or right)

## License

Personal project maintained by [@itswill00](https://github.com/itswill00).
