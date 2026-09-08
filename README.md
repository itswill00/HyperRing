# HyperRing

Physics-driven dynamic punch-hole status pill and interactive overlay for rooted Android devices.

HyperRing transforms the front-facing camera cutout into an expressive, contextual island. Built as a native Android DEX process running under `app_process`, it interacts directly with `WindowManager` without requiring accessibility services or background application overhead.

## Highlights

- **Natural spring dynamics**: Runge-Kutta 4th-order (RK4) numerical integrator driving 6 independent spatial springs (position, dimension, corner radius, content alpha) at full display refresh rate (up to 120 Hz).
- **Zero background overhead**: Hardware Choreographer loop automatically halts when animations settle, consuming 0.0% CPU during idle and steady-state display.
- **Zero-jitter layout**: Window dimensions preallocate target bounding boxes before spring motion initiates, eliminating Android IPC window resizing jumps.
- **Pure OLED design**: Deep `#000000` surface matching physical display cutouts, subtle specular ambient border, and strictly minimal human typography.
- **Root-level system listeners**: Native background monitoring for battery telemetry, media sessions, volume steps, ringer mode changes, and notification enqueues.
- **Stand-alone WebUI**: Single-file Vue 3 configuration interface accessible directly within KernelSU, APatch, and MMRL with dark mode and safe-area inset protection.

## Contextual States

| State | Trigger | Compact Pill | Expanded Card |
| :--- | :--- | :--- | :--- |
| **Idle** | Screen awake, no active alerts | Stealth ring around camera cutout | None (transparent, touch pass-through) |
| **Charging** | Power connected | Fast charge indicator and percentage | Battery level, charging power, current, temperature |
| **Media** | Audio playback active | Disc glyph and animated audio visualizer | Track title, artist, seekbar, playback controls |
| **Volume** | Hardware volume rocker | Speaker glyph and volume level | Volume slider and current audio stream |
| **Ringer** | Sound profile change | Sound / vibrate / silent glyph | Active sound mode status |
| **Notification** | App notification arrival | App icon glyph and sender name | Message preview and dismissal action |
| **Torch** | Flashlight toggled | Torch glyph and status | Flashlight quick-toggle button |
| **HyperDL** | Active background download | Progress dot and transfer speed | File transfer name, progress track, transfer rate |
| **Calibration** | Position tuning | Alignment reticle and target ring | Live coordinate adjustment overlay |

## Project Structure

```
HyperRing/
├── action.sh             # Magisk/KernelSU action trigger & daemon launcher
├── service.sh            # Boot completion background service daemon
├── customize.sh          # Module installation script
├── uninstall.sh          # Module removal script
├── module.prop           # Module metadata
├── build.sh              # Standalone compilation and packaging script
├── src/
│   └── HyperRingOverlay.java   # Native Java overlay engine & window manager
├── state/
│   ├── config.json       # User cutout coordinates & feature preferences
│   └── status.json       # Live telemetry and daemon runtime state
├── webroot/
│   └── index.html        # Bundled single-file WebUI
└── webui/                # Vue 3 source code for KernelSU/MMRL WebUI
```

## Configuration

Settings are stored in `state/config.json`:

```json
{
  "cutout_x": 540,
  "cutout_y": 52,
  "cutout_radius": 36,
  "pill_alignment": "center",
  "notch_mode": false,
  "x_offset": 0,
  "y_offset": 0,
  "pill_width": 0,
  "pill_height": 0,
  "card_width": 0,
  "card_radius": 24,
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

## CLI Controls

HyperRing responds instantly to commands written via `action.sh`:

```bash
# Persistent live preview modes
su -c "/data/adb/modules/hyperring/action.sh preview-pill"
su -c "/data/adb/modules/hyperring/action.sh preview-card"
su -c "/data/adb/modules/hyperring/action.sh preview-reticle"
su -c "/data/adb/modules/hyperring/action.sh preview-off"

# Trigger events
su -c "/data/adb/modules/hyperring/action.sh charge"
su -c "/data/adb/modules/hyperring/action.sh media"
su -c "/data/adb/modules/hyperring/action.sh volume"
su -c "/data/adb/modules/hyperring/action.sh ringer"
su -c "/data/adb/modules/hyperring/action.sh notification"

# Toggle expand / collapse
su -c "/data/adb/modules/hyperring/action.sh expand"
su -c "/data/adb/modules/hyperring/action.sh collapse"

# Return to idle
su -c "/data/adb/modules/hyperring/action.sh idle"
```

## Building

The build system is entirely self-contained. It can compile from source or package existing pre-compiled binaries:

```bash
# Compile DEX, build WebUI, and generate flashable ZIP:
./build.sh

# Compile and deploy live to connected rooted device:
./build.sh --deploy

# Deploy existing compiled files without rebuilding:
./build.sh --deploy-only

# Clean build artifacts:
./build.sh --clean
```

## Requirements

- Android 10+ (API level 29+)
- Root access via KernelSU, APatch, or Magisk
- Display with camera punch-hole cutout (center, left, or right aligned)

## Author

Maintained by [@itswill00](https://github.com/itswill00).
