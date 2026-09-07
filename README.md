# HyperRing

Physics-driven dynamic punch-hole status pill and interactive floating card for rooted Android devices.

## Architecture

- **Harmonic Spring Physics**: Employs an RK4-style numerical spring solver (stiffness ~360, damping ratio ~0.76) for elastic overshoot, momentum conservation, and fluid settling.
- **Hardware Cutout Anchoring**: Origin transformation coordinates are locked to the physical camera lens center. The background uses true black (#000000) with continuous squircle geometry for seamless hardware blending.
- **Native Window Injection**: Runs as a lightweight process under `app_process` attached directly to the WindowManager (`TYPE_APPLICATION_OVERLAY`), completely bypassing Accessibility Services.
- **Ecosystem Integration**:
  - **HyperDL**: Circular progress indicator and transfer rate telemetry.
  - **HyperCore**: Charging power metrics (wattage, current) and active kernel profile name.
  - **Media Session**: Realtime audio spectrum bars and playback controls (play, pause, next, previous).
- **Single-File WebUI**: Visual cutout calibration, physics tuning, and event triggers available inside KernelSU, APatch, and MMRL.

## State Model

| State | Trigger | Compact Representation | Expanded Representation |
| :--- | :--- | :--- | :--- |
| **Idle** | Default | Transparent circle over camera cutout | None |
| **Charging** | Power connected | Vector bolt and battery percentage | Charging wattage, current, and active kernel profile |
| **HyperDL** | Active download | Progress dot and download speed | Filename, progress bar, and speed |
| **Media** | Playback active | Disc icon and audio visualizer bars | Track title, artist, seekbar, and playback controls |

## Command-Line Triggers

Simulate events or control states directly:

```bash
# Simulate charging event
su -c "/data/adb/modules/hyperring/action.sh charge"

# Simulate media playback
su -c "/data/adb/modules/hyperring/action.sh media"

# Expand island into full card
su -c "/data/adb/modules/hyperring/action.sh expand"

# Collapse island into compact pill
su -c "/data/adb/modules/hyperring/action.sh collapse"

# Return to idle state
su -c "/data/adb/modules/hyperring/action.sh idle"
```

## Build and Deployment

```bash
# Build WebUI, DEX, and package zip:
./build.sh

# Build and deploy immediately to active device:
./build.sh --deploy

# Deploy existing build without recompiling:
./build.sh --deploy-only

# Clean build artifacts:
./build.sh --clean
```

## License

Maintained by @itswill00.
