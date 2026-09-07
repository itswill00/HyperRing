<template>
  <div class="app-shell">
    <!-- Top App Bar -->
    <header class="page-header">
      <div>
        <div class="page-header-title">HyperRing</div>
        <div class="page-header-sub">Dynamic Cutout Engine</div>
      </div>
      <span class="badge-pill" :class="isDaemonAlive ? 'active' : 'standby'">
        {{ isDaemonAlive ? `Active · PID ${daemonPid}` : 'Standby' }}
      </span>
    </header>

    <!-- Main Content Area -->
    <main class="content-area">

      <!-- Hero Dashboard Card -->
      <section class="md3-card">
        <div class="card-hero-row">
          <div class="icon-badge">
            <Icons name="circle" :size="20" />
          </div>
          <div class="hero-meta">
            <div class="hero-title">Punch-Hole Status Pill</div>
            <div class="hero-sub">Dynamic status expansion around the camera cutout</div>
          </div>
          <label class="md3-switch">
            <input type="checkbox" :checked="isDaemonAlive" @change="toggleDaemon" />
            <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
          </label>
        </div>

        <div class="hero-stats-grid">
          <div class="hero-stat-box">
            <span class="stat-lbl">Alignment</span>
            <span class="stat-val">{{ config.cutout_x }}x{{ config.cutout_y }} (r={{ config.cutout_radius }})</span>
          </div>
          <div class="hero-stat-box">
            <span class="stat-lbl">Active State</span>
            <span class="stat-val">{{ liveState.active_island || 'Idle' }}</span>
          </div>
          <div class="hero-stat-box">
            <span class="stat-lbl">Battery</span>
            <span class="stat-val">{{ liveState.battery_pct || 100 }}% {{ liveState.battery_charging ? '(Chg)' : '' }}</span>
          </div>
          <div class="hero-stat-box">
            <span class="stat-lbl">HyperCore</span>
            <span class="stat-val">{{ liveState.hypercore_profile || 'Interactive' }}</span>
          </div>
        </div>
      </section>

      <!-- Section: Cutout Calibration -->
      <div class="section-title">Cutout Calibration</div>
      <section class="md3-card">
        <div class="preset-row">
          <span class="row-meta-label">Position Presets</span>
          <div class="segment-container">
            <button type="button" class="segment-btn" :class="{ active: currentPreset === 'center' }" @click="applyPreset('center')">Center</button>
            <button type="button" class="segment-btn" :class="{ active: currentPreset === 'left' }" @click="applyPreset('left')">Left</button>
            <button type="button" class="segment-btn" :class="{ active: currentPreset === 'right' }" @click="applyPreset('right')">Right</button>
            <button type="button" class="segment-btn" :class="{ active: currentPreset === 'auto' }" @click="autoDetectCutout">Auto</button>
          </div>
        </div>

        <!-- Horizontal X Offset -->
        <div class="stepper-setting-block">
          <div class="stepper-header">
            <div>
              <div class="stepper-title">Horizontal Center (X)</div>
              <div class="stepper-sub">Horizontal coordinate aligned with camera lens</div>
            </div>
            <div class="stepper-controls">
              <button type="button" class="step-btn" @click="stepValue('cutout_x', -1)"><Icons name="minus" :size="12" /></button>
              <span class="stepper-val">{{ config.cutout_x }} px</span>
              <button type="button" class="step-btn" @click="stepValue('cutout_x', 1)"><Icons name="plus" :size="12" /></button>
            </div>
          </div>
          <input type="range" min="0" max="1080" step="1" v-model.number="config.cutout_x" @input="saveConfigDebounced" class="slider-range" />
        </div>

        <!-- Vertical Y Offset -->
        <div class="stepper-setting-block">
          <div class="stepper-header">
            <div>
              <div class="stepper-title">Vertical Center (Y)</div>
              <div class="stepper-sub">Vertical offset from top screen boundary</div>
            </div>
            <div class="stepper-controls">
              <button type="button" class="step-btn" @click="stepValue('cutout_y', -1)"><Icons name="minus" :size="12" /></button>
              <span class="stepper-val">{{ config.cutout_y }} px</span>
              <button type="button" class="step-btn" @click="stepValue('cutout_y', 1)"><Icons name="plus" :size="12" /></button>
            </div>
          </div>
          <input type="range" min="0" max="160" step="1" v-model.number="config.cutout_y" @input="saveConfigDebounced" class="slider-range" />
        </div>

        <!-- Cutout Radius -->
        <div class="stepper-setting-block">
          <div class="stepper-header">
            <div>
              <div class="stepper-title">Cutout Radius</div>
              <div class="stepper-sub">Physical punch-hole radius for edge coverage</div>
            </div>
            <div class="stepper-controls">
              <button type="button" class="step-btn" @click="stepValue('cutout_radius', -1)"><Icons name="minus" :size="12" /></button>
              <span class="stepper-val">{{ config.cutout_radius }} px</span>
              <button type="button" class="step-btn" @click="stepValue('cutout_radius', 1)"><Icons name="plus" :size="12" /></button>
            </div>
          </div>
          <input type="range" min="15" max="60" step="1" v-model.number="config.cutout_radius" @input="saveConfigDebounced" class="slider-range" />
        </div>

        <!-- Calibration Reticle Action -->
        <button type="button" class="action-btn-secondary" @click="triggerCalibration">
          <Icons name="crosshair" :size="15" />
          <span>Show Calibration Reticle on Screen</span>
        </button>
      </section>

      <!-- Section: Feature Islands & Triggers -->
      <div class="section-title">Island Integrations</div>
      <section class="md3-list-group">
        <div class="md3-list-row" @click="toggleConfig('enable_media')">
          <div class="row-left">
            <div class="icon-badge secondary">
              <Icons name="music" :size="16" />
            </div>
            <div class="row-meta">
              <div class="row-title">Media Session Listener</div>
              <div class="row-sub">Track title, artist, audio waveform, and playback controls</div>
            </div>
          </div>
          <label class="md3-switch" @click.stop>
            <input type="checkbox" v-model="config.enable_media" @change="saveConfig" />
            <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
          </label>
        </div>

        <div class="md3-list-row" @click="toggleConfig('enable_charging')">
          <div class="row-left">
            <div class="icon-badge secondary">
              <Icons name="bolt" :size="16" />
            </div>
            <div class="row-meta">
              <div class="row-title">Battery Power Alerts</div>
              <div class="row-sub">Super Charge wattage, current telemetry, and percentage</div>
            </div>
          </div>
          <label class="md3-switch" @click.stop>
            <input type="checkbox" v-model="config.enable_charging" @change="saveConfig" />
            <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
          </label>
        </div>

        <div class="md3-list-row" @click="toggleConfig('auto_expand_charging')">
          <div class="row-left">
            <div class="icon-badge secondary">
              <Icons name="maximize" :size="16" />
            </div>
            <div class="row-meta">
              <div class="row-title">Auto-Expand on Plug</div>
              <div class="row-sub">Open expanded power card when charger is plugged in</div>
            </div>
          </div>
          <label class="md3-switch" @click.stop>
            <input type="checkbox" v-model="config.auto_expand_charging" @change="saveConfig" />
            <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
          </label>
        </div>

        <div class="md3-list-row" @click="toggleConfig('enable_hyperdl')">
          <div class="row-left">
            <div class="icon-badge secondary">
              <Icons name="download" :size="16" />
            </div>
            <div class="row-meta">
              <div class="row-title">HyperDL Download Tracker</div>
              <div class="row-sub">Transfer speed and download progress indicator</div>
            </div>
          </div>
          <label class="md3-switch" @click.stop>
            <input type="checkbox" v-model="config.enable_hyperdl" @change="saveConfig" />
            <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
          </label>
        </div>

        <div class="md3-list-row" @click="toggleConfig('enable_volume')">
          <div class="row-left">
            <div class="icon-badge secondary">
              <Icons name="wave" :size="16" />
            </div>
            <div class="row-meta">
              <div class="row-title">Volume Key Expansion</div>
              <div class="row-sub">Liquid volume slider pill when hardware keys are pressed</div>
            </div>
          </div>
          <label class="md3-switch" @click.stop>
            <input type="checkbox" v-model="config.enable_volume" @change="saveConfig" />
            <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
          </label>
        </div>

        <div class="md3-list-row" @click="toggleConfig('enable_ringer')">
          <div class="row-left">
            <div class="icon-badge secondary">
              <Icons name="circle" :size="16" />
            </div>
            <div class="row-meta">
              <div class="row-title">Ringer Mode Status</div>
              <div class="row-sub">Indicator on silent, vibrate, or sound mode transitions</div>
            </div>
          </div>
          <label class="md3-switch" @click.stop>
            <input type="checkbox" v-model="config.enable_ringer" @change="saveConfig" />
            <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
          </label>
        </div>

        <div class="md3-list-row" @click="toggleConfig('enable_notifications')">
          <div class="row-left">
            <div class="icon-badge secondary">
              <Icons name="lens" :size="16" />
            </div>
            <div class="row-meta">
              <div class="row-title">Notification Pill Bloom</div>
              <div class="row-sub">Display incoming app notifications dynamically</div>
            </div>
          </div>
          <label class="md3-switch" @click.stop>
            <input type="checkbox" v-model="config.enable_notifications" @change="saveConfig" />
            <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
          </label>
        </div>

        <div class="md3-list-row" @click="toggleConfig('enable_hypercore')">
          <div class="row-left">
            <div class="icon-badge secondary">
              <Icons name="chip" :size="16" />
            </div>
            <div class="row-meta">
              <div class="row-title">HyperCore Governor Sync</div>
              <div class="row-sub">Display governor profile changes in expanded card</div>
            </div>
          </div>
          <label class="md3-switch" @click.stop>
            <input type="checkbox" v-model="config.enable_hypercore" @change="saveConfig" />
            <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
          </label>
        </div>
      </section>

      <!-- Section: Motion & Ergonomics -->
      <div class="section-title">Motion &amp; Ergonomics</div>
      <section class="md3-card">
        <div class="preset-row">
          <span class="row-meta-label">Spring Curves</span>
          <div class="segment-container">
            <button type="button" class="segment-btn" :class="{ active: currentMotionProfile === 'fluid' }" @click="applyMotionPreset('fluid')">HyperOS</button>
            <button type="button" class="segment-btn" :class="{ active: currentMotionProfile === 'kinetic' }" @click="applyMotionPreset('kinetic')">Kinetic</button>
            <button type="button" class="segment-btn" :class="{ active: currentMotionProfile === 'snappy' }" @click="applyMotionPreset('snappy')">Snappy</button>
          </div>
        </div>

        <div class="stepper-setting-block">
          <div class="stepper-header">
            <div>
              <div class="stepper-title">Spring Stiffness (k)</div>
              <div class="stepper-sub">Expansion and morphing velocity response</div>
            </div>
            <span class="stepper-val">{{ config.spring_stiffness }}</span>
          </div>
          <input type="range" min="200" max="600" step="10" v-model.number="config.spring_stiffness" @input="saveConfigDebounced" class="slider-range" />
        </div>

        <div class="stepper-setting-block">
          <div class="stepper-header">
            <div>
              <div class="stepper-title">Damping Ratio (zeta)</div>
              <div class="stepper-sub">Fluid oscillation and elastic settlement</div>
            </div>
            <span class="stepper-val">{{ config.spring_damping }}</span>
          </div>
          <input type="range" min="0.50" max="0.95" step="0.02" v-model.number="config.spring_damping" @input="saveConfigDebounced" class="slider-range" />
        </div>

        <div class="inner-divider"></div>

        <div class="setting-toggle-line" @click="toggleConfig('hide_in_landscape')">
          <div>
            <div class="stepper-title">Hide in Landscape</div>
            <div class="stepper-sub">Automatically suspend overlay during gaming and video playback</div>
          </div>
          <label class="md3-switch" @click.stop>
            <input type="checkbox" v-model="config.hide_in_landscape" @change="saveConfig" />
            <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
          </label>
        </div>

        <div class="setting-toggle-line" @click="toggleConfig('stealth_ring_idle')">
          <div>
            <div class="stepper-title">Stealth Ring in Idle</div>
            <div class="stepper-sub">Show 1px minimal ring around punch hole when idle</div>
          </div>
          <label class="md3-switch" @click.stop>
            <input type="checkbox" v-model="config.stealth_ring_idle" @change="saveConfig" />
            <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
          </label>
        </div>
      </section>

      <!-- Section: Live Test & Diagnostics -->
      <div class="section-title">Live Test &amp; Diagnostics</div>
      <section class="md3-card">
        <div class="sim-label">Simulate Real-Time Events</div>
        <div class="action-chips-grid">
          <button type="button" class="sim-chip" @click="sendTrigger('charge')">
            <Icons name="bolt" :size="13" />
            <span>Charge</span>
          </button>
          <button type="button" class="sim-chip" @click="sendTrigger('media')">
            <Icons name="music" :size="13" />
            <span>Media</span>
          </button>
          <button type="button" class="sim-chip" @click="sendTrigger('volume')">
            <Icons name="wave" :size="13" />
            <span>Volume</span>
          </button>
          <button type="button" class="sim-chip" @click="sendTrigger('notification')">
            <Icons name="lens" :size="13" />
            <span>Alert</span>
          </button>
          <button type="button" class="sim-chip" @click="sendTrigger('torch')">
            <Icons name="circle" :size="13" />
            <span>Torch</span>
          </button>
          <button type="button" class="sim-chip" @click="sendTrigger('hyperdl')">
            <Icons name="download" :size="13" />
            <span>Download</span>
          </button>
          <button type="button" class="sim-chip" @click="sendTrigger('idle')">
            <Icons name="check" :size="13" />
            <span>Idle</span>
          </button>
        </div>

        <div class="inner-divider"></div>

        <div class="btn-group-row">
          <button type="button" class="action-btn-primary" @click="restartDaemon">
            <Icons name="refresh" :size="15" />
            <span>Restart Service Daemon</span>
          </button>
          <button type="button" class="action-btn-secondary" @click="toggleLogView">
            <Icons name="terminal" :size="15" />
            <span>{{ showLogs ? 'Hide Logs' : 'View Logs' }}</span>
          </button>
        </div>

        <!-- Collapsible Dark Terminal View -->
        <div v-if="showLogs" class="terminal-container">
          <div class="terminal-header">
            <span>/data/adb/modules/hyperring/state/overlay.log</span>
            <button type="button" class="terminal-refresh-btn" @click="fetchLogs">Refresh</button>
          </div>
          <pre class="terminal-body">{{ logContent || 'No log entries recorded.' }}</pre>
        </div>
      </section>

      <!-- Floating Toast Notification -->
      <transition name="toast-slide">
        <div v-if="toastNotice" class="toast-pill">
          <Icons name="check" :size="14" style="color: var(--primary);" />
          <span>{{ toastNotice }}</span>
        </div>
      </transition>

    </main>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import Icons from '@/components/icons/Icons.vue'

const config = reactive({
  cutout_x: 540,
  cutout_y: 52,
  cutout_radius: 36,
  enable_media: true,
  enable_charging: true,
  enable_volume: true,
  enable_ringer: true,
  enable_notifications: true,
  enable_hyperdl: true,
  enable_hypercore: true,
  stealth_ring_idle: false,
  hide_in_landscape: true,
  spring_stiffness: 380.0,
  spring_damping: 0.76,
  auto_expand_charging: true,
  expand_timeout_ms: 3500
})

const liveState = reactive({
  pid: 0,
  active_island: 'idle',
  expanded: false,
  media_title: 'Starboy',
  media_artist: 'The Weeknd',
  media_playing: false,
  battery_pct: 100,
  battery_charging: false,
  hypercore_profile: 'Interactive',
  hyperdl_active: false,
  hyperdl_speed: '0 MB/s',
  hyperdl_progress: 0
})

const isDaemonAlive = ref(true)
const daemonPid = ref(0)
const toastNotice = ref('')
const currentPreset = ref('center')
const currentMotionProfile = ref('fluid')
const showLogs = ref(false)
const logContent = ref('')

let configDebounceTimer = null
let statusPollInterval = null
let toastTimer = null

function execShell(cmd) {
  return new Promise(resolve => {
    if (typeof ksu !== 'undefined' && typeof ksu.exec === 'function') {
      const id = '_hr_' + Date.now() + '_' + Math.floor(Math.random() * 1000)
      window[id] = (errno, stdout, stderr) => {
        delete window[id]
        resolve(stdout || stderr || '')
      }
      try { ksu.exec(cmd, '{}', id) } catch (e) { resolve('') }
    } else {
      resolve('')
    }
  })
}

async function loadConfiguration() {
  try {
    const raw = await execShell('cat /data/adb/modules/hyperring/state/config.json 2>/dev/null')
    if (raw && raw.trim().startsWith('{')) {
      const parsed = JSON.parse(raw.trim())
      Object.assign(config, parsed)
    }
  } catch (e) {}
}

async function queryStatusFile() {
  try {
    const raw = await execShell('cat /data/adb/modules/hyperring/state/status.json 2>/dev/null')
    if (raw && raw.trim().startsWith('{')) {
      const parsed = JSON.parse(raw.trim())
      Object.assign(liveState, parsed)
      if (parsed.pid) {
        daemonPid.value = parsed.pid
        isDaemonAlive.value = true
      }
    }
  } catch (e) {}

  if (!daemonPid.value) {
    try {
      const pids = await execShell('pgrep -f "com.hyperring.HyperRingOverlay" 2>/dev/null')
      if (pids && pids.trim()) {
        daemonPid.value = parseInt(pids.trim().split('\n')[0])
        isDaemonAlive.value = true
      } else {
        isDaemonAlive.value = false
      }
    } catch (e) {
      isDaemonAlive.value = false
    }
  }
}

function saveConfigDebounced() {
  clearTimeout(configDebounceTimer)
  configDebounceTimer = setTimeout(saveConfig, 300)
}

async function saveConfig() {
  const jsonStr = JSON.stringify(config, null, 2).replace(/'/g, "'\\x27'")
  await execShell(`mkdir -p /data/adb/modules/hyperring/state && echo '${jsonStr}' > /data/adb/modules/hyperring/state/config.json`)
}

function stepValue(key, delta) {
  config[key] = Math.max(0, config[key] + delta)
  saveConfigDebounced()
}

function toggleConfig(key) {
  config[key] = !config[key]
  saveConfig()
}

function applyPreset(type) {
  currentPreset.value = type
  if (type === 'center') {
    config.cutout_x = 540
    config.cutout_y = 52
    config.cutout_radius = 36
  } else if (type === 'left') {
    config.cutout_x = 120
    config.cutout_y = 52
    config.cutout_radius = 36
  } else if (type === 'right') {
    config.cutout_x = 960
    config.cutout_y = 52
    config.cutout_radius = 36
  }
  saveConfig()
  showToast(`Applied ${type} cutout preset`)
}

async function autoDetectCutout() {
  currentPreset.value = 'auto'
  try {
    const wmOut = await execShell('dumpsys window | grep -i "status_bar" | head -n 10 2>/dev/null')
    let sbHeight = 104
    if (wmOut && wmOut.includes('fillx')) {
      const match = wmOut.match(/fillx(\d+)/)
      if (match) sbHeight = parseInt(match[1])
    }
    config.cutout_x = 540
    config.cutout_y = Math.round(sbHeight / 2)
    config.cutout_radius = 36
    saveConfig()
    showToast(`Detected status bar height: ${sbHeight}px`)
  } catch (e) {
    showToast('Auto detection failed, using center default')
  }
}

function applyMotionPreset(profile) {
  currentMotionProfile.value = profile
  if (profile === 'fluid') {
    config.spring_stiffness = 380.0
    config.spring_damping = 0.76
  } else if (profile === 'kinetic') {
    config.spring_stiffness = 440.0
    config.spring_damping = 0.85
  } else if (profile === 'snappy') {
    config.spring_stiffness = 520.0
    config.spring_damping = 0.92
  }
  saveConfig()
  showToast(`Applied ${profile} motion preset`)
}

async function sendTrigger(cmd) {
  await execShell(`echo "${cmd}" > /data/adb/modules/hyperring/state/trigger.cmd`)
  showToast(`Trigger sent: ${cmd}`)
  setTimeout(queryStatusFile, 600)
}

async function triggerCalibration() {
  await sendTrigger('calibrate')
  showToast('Calibration reticle visible for 6 seconds')
}

async function toggleDaemon() {
  if (isDaemonAlive.value) {
    await execShell('kill -9 $(pgrep -f "com.hyperring.HyperRingOverlay") 2>/dev/null')
    isDaemonAlive.value = false
    daemonPid.value = 0
    showToast('HyperRing daemon stopped')
  } else {
    restartDaemon()
  }
}

async function restartDaemon() {
  showToast('Restarting HyperRing service...')
  await execShell('sh /data/adb/modules/hyperring/action.sh 2>/dev/null')
  setTimeout(async () => {
    await queryStatusFile()
    showToast(isDaemonAlive.value ? `Active · PID ${daemonPid.value}` : 'Daemon started')
  }, 1200)
}

async function fetchLogs() {
  const raw = await execShell('tail -n 25 /data/adb/modules/hyperring/state/overlay.log 2>/dev/null')
  logContent.value = raw ? raw.trim() : 'Log file empty'
}

function toggleLogView() {
  showLogs.value = !showLogs.value
  if (showLogs.value) fetchLogs()
}

function showToast(msg) {
  toastNotice.value = msg
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    if (toastNotice.value === msg) toastNotice.value = ''
  }, 2400)
}

onMounted(() => {
  loadConfiguration()
  queryStatusFile()
  statusPollInterval = setInterval(queryStatusFile, 3000)

  // Enforce safe statusbar height fallback if webview does not inject insets
  const cs = getComputedStyle(document.documentElement)
  const winTop = cs.getPropertyValue('--window-inset-top')
  if (!winTop || winTop.trim() === '' || winTop.trim() === '0px') {
    document.documentElement.style.setProperty('--window-inset-top', '38px')
  }
})

onUnmounted(() => {
  if (statusPollInterval) clearInterval(statusPollInterval)
})
</script>

<style scoped>
.card-hero-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.hero-meta {
  flex: 1;
  min-width: 0;
}

.hero-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--on-surface);
}

.hero-sub {
  font-size: 11px;
  color: var(--on-surface-variant);
  margin-top: 1px;
}

.hero-stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px;
  margin-top: 14px;
}

.hero-stat-box {
  background: var(--surface-container-low);
  border: 1px solid var(--surface-container-high);
  border-radius: 10px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-lbl {
  font-size: 10px;
  color: var(--on-surface-variant);
  font-weight: 500;
}

.stat-val {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--on-surface);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.preset-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.row-meta-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--on-surface);
}

.segment-container {
  display: inline-flex;
  background: var(--surface-container-lowest);
  padding: 2px;
  border-radius: 9px;
  border: 1px solid var(--surface-container-high);
  gap: 2px;
}

.segment-btn {
  padding: 4px 10px;
  border-radius: 7px;
  background: transparent;
  color: var(--on-surface-variant);
  font-size: 11px;
  font-weight: 500;
  border: none;
  cursor: pointer;
  transition: all 0.15s ease;
}

.segment-btn.active {
  background: var(--primary-container);
  color: var(--on-primary-container);
  font-weight: 600;
}

.stepper-setting-block {
  margin-bottom: 14px;
}

.stepper-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.stepper-title {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--on-surface);
}

.stepper-sub {
  font-size: 11px;
  color: var(--on-surface-variant);
  margin-top: 1px;
}

.stepper-controls {
  display: flex;
  align-items: center;
  gap: 6px;
}

.step-btn {
  width: 26px;
  height: 26px;
  border-radius: 7px;
  background: var(--surface-container-high);
  border: 1px solid var(--surface-bright);
  color: var(--on-surface);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.step-btn:active {
  background: var(--surface-bright);
}

.stepper-val {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--primary);
  min-width: 52px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.slider-range {
  width: 100%;
  accent-color: var(--primary);
  height: 4px;
  background: var(--surface-container-highest);
  border-radius: 2px;
}

.action-btn-secondary {
  width: 100%;
  padding: 9px 14px;
  border-radius: 11px;
  background: var(--surface-container-low);
  border: 1px solid var(--surface-container-high);
  color: var(--on-surface);
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.action-btn-secondary:active {
  background: var(--surface-container-highest);
}

.action-btn-primary {
  flex: 1;
  padding: 10px 14px;
  border-radius: 11px;
  background: var(--primary);
  border: none;
  color: var(--on-primary);
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.action-btn-primary:active {
  filter: brightness(0.92);
}

.inner-divider {
  height: 1px;
  background: var(--surface-container-high);
  margin: 14px 0;
}

.setting-toggle-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  cursor: pointer;
}

.sim-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--on-surface-variant);
  margin-bottom: 8px;
}

.action-chips-grid {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.sim-chip {
  padding: 6px 12px;
  border-radius: 10px;
  background: var(--surface-container-low);
  border: 1px solid var(--surface-container-high);
  color: var(--on-surface);
  font-size: 11px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  white-space: nowrap;
}

.sim-chip:active {
  background: var(--surface-container-highest);
}

.btn-group-row {
  display: flex;
  gap: 8px;
}

.terminal-container {
  margin-top: 12px;
  background: #000000;
  border: 1px solid var(--surface-container-high);
  border-radius: 10px;
  overflow: hidden;
}

.terminal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 10px;
  background: var(--surface-container-lowest);
  border-bottom: 1px solid var(--surface-container-high);
  font-size: 10px;
  color: var(--on-surface-variant);
}

.terminal-refresh-btn {
  background: transparent;
  border: none;
  color: var(--primary);
  font-size: 10px;
  cursor: pointer;
}

.terminal-body {
  padding: 10px;
  font-family: var(--font-mono);
  font-size: 10.5px;
  line-height: 1.4;
  color: #A3E635;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 140px;
  overflow-y: auto;
}

.row-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.row-meta {
  flex: 1;
}

.row-title {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--on-surface);
}

.row-sub {
  font-size: 11px;
  color: var(--on-surface-variant);
  margin-top: 1px;
}

.toast-slide-enter-active {
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1), opacity 0.22s ease;
}
.toast-slide-leave-active {
  transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.18s ease;
}
.toast-slide-enter-from {
  opacity: 0;
  transform: translate(-50%, 16px) scale(0.92);
}
.toast-slide-leave-to {
  opacity: 0;
  transform: translate(-50%, -8px) scale(0.96);
}
</style>
