<template>
  <div class="app-shell">
    <!-- Top Header -->
    <header class="page-header">
      <div>
        <div class="page-header-title">HyperRing</div>
        <div class="page-header-sub">Punch-hole overlay</div>
      </div>
      <span class="badge-pill" :class="isDaemonAlive ? 'active' : 'standby'">
        {{ isDaemonAlive ? (daemonPid ? `Active · ${daemonPid}` : 'Active') : 'Standby' }}
      </span>
    </header>

    <!-- Segmented Navigation Tabs -->
    <nav class="tabs-nav">
      <button
        type="button"
        class="tab-btn"
        :class="{ active: currentTab === 'cutout' }"
        @click="currentTab = 'cutout'"
      >
        Cutout
      </button>
      <button
        type="button"
        class="tab-btn"
        :class="{ active: currentTab === 'features' }"
        @click="currentTab = 'features'"
      >
        Events
      </button>
      <button
        type="button"
        class="tab-btn"
        :class="{ active: currentTab === 'motion' }"
        @click="currentTab = 'motion'"
      >
        Motion
      </button>
      <button
        type="button"
        class="tab-btn"
        :class="{ active: currentTab === 'tools' }"
        @click="currentTab = 'tools'"
      >
        Tools
      </button>
    </nav>

    <!-- Main Scrollable Area -->
    <main class="content-area">

      <!-- TAB 1: CUTOUT CALIBRATION -->
      <div v-if="currentTab === 'cutout'">
        <!-- Live Cutout Simulator Preview -->
        <div class="section-title">Visual simulator</div>
        <div class="sim-bezel-box">
          <div class="sim-screen-boundary">
            <!-- Simulated Pill -->
            <div
              class="sim-pill"
              :style="{
                left: simPillLeft + '%',
                top: simPillTop + 'px',
                width: simPillWidth + 'px',
                height: simPillHeight + 'px'
              }"
            >
              <span style="font-size: 8px; color: #9ea3b2; opacity: 0.85;">{{ liveState.battery_pct || 100 }}%</span>
              <span style="font-size: 8px; color: #f0f2f5; opacity: 0.85;">Hyper</span>
            </div>
            <!-- Physical Camera Cutout -->
            <div
              class="sim-punch-hole"
              :style="{
                left: simCutoutLeft + '%',
                top: simCutoutTop + 'px',
                width: simCutoutSize + 'px',
                height: simCutoutSize + 'px'
              }"
            ></div>
          </div>
        </div>

        <div class="section-title">Camera placement</div>
        <section class="md3-card">
          <!-- Alignment Presets -->
          <div class="preset-row">
            <span class="row-meta-label">Position preset</span>
            <div class="segment-container">
              <button
                type="button"
                class="segment-btn"
                :class="{ active: currentPreset === 'center' }"
                @click="applyPreset('center')"
              >
                Center
              </button>
              <button
                type="button"
                class="segment-btn"
                :class="{ active: currentPreset === 'left' }"
                @click="applyPreset('left')"
              >
                Left
              </button>
              <button
                type="button"
                class="segment-btn"
                :class="{ active: currentPreset === 'right' }"
                @click="applyPreset('right')"
              >
                Right
              </button>
              <button
                type="button"
                class="segment-btn"
                :class="{ active: currentPreset === 'auto' }"
                @click="autoDetectCutout"
              >
                Auto
              </button>
            </div>
          </div>

          <!-- Horizontal Position X -->
          <div class="stepper-setting-block">
            <div class="stepper-header">
              <span class="stepper-title">Horizontal offset (X)</span>
              <span class="stepper-val">{{ config.cutout_x }} px</span>
            </div>
            <input
              type="range"
              min="0"
              max="1080"
              step="1"
              v-model.number="config.cutout_x"
              @input="saveConfigDebounced"
              class="slider-range"
            />
          </div>

          <!-- Vertical Position Y -->
          <div class="stepper-setting-block">
            <div class="stepper-header">
              <span class="stepper-title">Vertical offset (Y)</span>
              <span class="stepper-val">{{ config.cutout_y }} px</span>
            </div>
            <input
              type="range"
              min="0"
              max="160"
              step="1"
              v-model.number="config.cutout_y"
              @input="saveConfigDebounced"
              class="slider-range"
            />
          </div>

          <!-- Cutout Radius -->
          <div class="stepper-setting-block">
            <div class="stepper-header">
              <span class="stepper-title">Camera radius</span>
              <span class="stepper-val">{{ config.cutout_radius }} px</span>
            </div>
            <input
              type="range"
              min="15"
              max="60"
              step="1"
              v-model.number="config.cutout_radius"
              @input="saveConfigDebounced"
              class="slider-range"
            />
          </div>

          <!-- Reticle Trigger Button -->
          <button type="button" class="action-btn-secondary" @click="triggerCalibration">
            <Icons name="crosshair" :size="15" />
            <span>Show alignment reticle</span>
          </button>
        </section>
      </div>

      <!-- TAB 2: EVENT INTEGRATIONS -->
      <div v-else-if="currentTab === 'features'">
        <div class="section-title">Active listeners</div>
        <section class="md3-list-group">
          <!-- Media Session -->
          <div class="md3-list-row" @click="toggleConfig('enable_media')">
            <div class="row-left">
              <div class="icon-badge secondary">
                <Icons name="music" :size="16" />
              </div>
              <div class="row-meta">
                <div class="row-title">Now playing</div>
                <div class="row-sub">Track info and media controls</div>
              </div>
            </div>
            <label class="md3-switch" @click.stop>
              <input type="checkbox" v-model="config.enable_media" @change="saveConfig" />
              <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
            </label>
          </div>

          <!-- Charging Telemetry -->
          <div class="md3-list-row" @click="toggleConfig('enable_charging')">
            <div class="row-left">
              <div class="icon-badge secondary">
                <Icons name="bolt" :size="16" />
              </div>
              <div class="row-meta">
                <div class="row-title">Battery charging</div>
                <div class="row-sub">Live wattage and power status</div>
              </div>
            </div>
            <label class="md3-switch" @click.stop>
              <input type="checkbox" v-model="config.enable_charging" @change="saveConfig" />
              <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
            </label>
          </div>

          <!-- Volume Key Expansion -->
          <div class="md3-list-row" @click="toggleConfig('enable_volume')">
            <div class="row-left">
              <div class="icon-badge secondary">
                <Icons name="wave" :size="16" />
              </div>
              <div class="row-meta">
                <div class="row-title">Volume HUD</div>
                <div class="row-sub">Level slider on keypress</div>
              </div>
            </div>
            <label class="md3-switch" @click.stop>
              <input type="checkbox" v-model="config.enable_volume" @change="saveConfig" />
              <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
            </label>
          </div>

          <!-- Ringer Mode -->
          <div class="md3-list-row" @click="toggleConfig('enable_ringer')">
            <div class="row-left">
              <div class="icon-badge secondary">
                <Icons name="circle" :size="16" />
              </div>
              <div class="row-meta">
                <div class="row-title">Ringer mode</div>
                <div class="row-sub">Silent and vibrate popups</div>
              </div>
            </div>
            <label class="md3-switch" @click.stop>
              <input type="checkbox" v-model="config.enable_ringer" @change="saveConfig" />
              <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
            </label>
          </div>

          <!-- Notifications -->
          <div class="md3-list-row" @click="toggleConfig('enable_notifications')">
            <div class="row-left">
              <div class="icon-badge secondary">
                <Icons name="lens" :size="16" />
              </div>
              <div class="row-meta">
                <div class="row-title">Notifications</div>
                <div class="row-sub">Heads-up message alerts</div>
              </div>
            </div>
            <label class="md3-switch" @click.stop>
              <input type="checkbox" v-model="config.enable_notifications" @change="saveConfig" />
              <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
            </label>
          </div>

          <!-- HyperDL Engine -->
          <div class="md3-list-row" @click="toggleConfig('enable_hyperdl')">
            <div class="row-left">
              <div class="icon-badge secondary">
                <Icons name="download" :size="16" />
              </div>
              <div class="row-meta">
                <div class="row-title">Downloads</div>
                <div class="row-sub">HyperDL transfer progress</div>
              </div>
            </div>
            <label class="md3-switch" @click.stop>
              <input type="checkbox" v-model="config.enable_hyperdl" @change="saveConfig" />
              <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
            </label>
          </div>

          <!-- HyperCore Governor -->
          <div class="md3-list-row" @click="toggleConfig('enable_hypercore')">
            <div class="row-left">
              <div class="icon-badge secondary">
                <Icons name="chip" :size="16" />
              </div>
              <div class="row-meta">
                <div class="row-title">HyperCore sync</div>
                <div class="row-sub">Kernel profile in expanded card</div>
              </div>
            </div>
            <label class="md3-switch" @click.stop>
              <input type="checkbox" v-model="config.enable_hypercore" @change="saveConfig" />
              <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
            </label>
          </div>
        </section>

        <div class="section-title">Display behavior</div>
        <section class="md3-list-group">
          <!-- Stealth Ring in Idle -->
          <div class="md3-list-row" @click="toggleConfig('stealth_ring_idle')">
            <div class="row-left">
              <div class="icon-badge secondary">
                <Icons name="circle" :size="16" />
              </div>
              <div class="row-meta">
                <div class="row-title">Stealth idle</div>
                <div class="row-sub">Hide ring when inactive</div>
              </div>
            </div>
            <label class="md3-switch" @click.stop>
              <input type="checkbox" v-model="config.stealth_ring_idle" @change="saveConfig" />
              <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
            </label>
          </div>

          <!-- Landscape Guard -->
          <div class="md3-list-row" @click="toggleConfig('hide_in_landscape')">
            <div class="row-left">
              <div class="icon-badge secondary">
                <Icons name="shield" :size="16" />
              </div>
              <div class="row-meta">
                <div class="row-title">Landscape guard</div>
                <div class="row-sub">Disable in full-screen games</div>
              </div>
            </div>
            <label class="md3-switch" @click.stop>
              <input type="checkbox" v-model="config.hide_in_landscape" @change="saveConfig" />
              <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
            </label>
          </div>
        </section>
      </div>

      <!-- TAB 3: MOTION PHYSICS -->
      <div v-else-if="currentTab === 'motion'">
        <div class="section-title">Spring curves</div>
        <section class="md3-card">
          <div class="preset-row">
            <span class="row-meta-label">Curve preset</span>
            <div class="segment-container">
              <button
                type="button"
                class="segment-btn"
                :class="{ active: currentMotionProfile === 'fluid' }"
                @click="applyMotionPreset('fluid')"
              >
                HyperOS
              </button>
              <button
                type="button"
                class="segment-btn"
                :class="{ active: currentMotionProfile === 'kinetic' }"
                @click="applyMotionPreset('kinetic')"
              >
                Kinetic
              </button>
              <button
                type="button"
                class="segment-btn"
                :class="{ active: currentMotionProfile === 'snappy' }"
                @click="applyMotionPreset('snappy')"
              >
                Snappy
              </button>
            </div>
          </div>

          <!-- Stiffness -->
          <div class="stepper-setting-block">
            <div class="stepper-header">
              <span class="stepper-title">Stiffness</span>
              <span class="stepper-val">{{ config.spring_stiffness }}</span>
            </div>
            <input
              type="range"
              min="200"
              max="600"
              step="10"
              v-model.number="config.spring_stiffness"
              @input="saveConfigDebounced"
              class="slider-range"
            />
          </div>

          <!-- Damping -->
          <div class="stepper-setting-block">
            <div class="stepper-header">
              <span class="stepper-title">Damping ratio</span>
              <span class="stepper-val">{{ config.spring_damping }}</span>
            </div>
            <input
              type="range"
              min="0.50"
              max="0.95"
              step="0.02"
              v-model.number="config.spring_damping"
              @input="saveConfigDebounced"
              class="slider-range"
            />
          </div>
        </section>

        <div class="section-title">Expansion options</div>
        <section class="md3-list-group">
          <div class="md3-list-row" @click="toggleConfig('auto_expand_charging')">
            <div class="row-left">
              <div class="icon-badge secondary">
                <Icons name="maximize" :size="16" />
              </div>
              <div class="row-meta">
                <div class="row-title">Auto-expand on plug</div>
                <div class="row-sub">Open power card when charger connects</div>
              </div>
            </div>
            <label class="md3-switch" @click.stop>
              <input type="checkbox" v-model="config.auto_expand_charging" @change="saveConfig" />
              <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
            </label>
          </div>
        </section>
      </div>

      <!-- TAB 4: TOOLS & DIAGNOSTICS -->
      <div v-else-if="currentTab === 'tools'">
        <div class="section-title">Live event triggers</div>
        <section class="md3-card">
          <div class="action-chips-grid">
            <button type="button" class="sim-chip" @click="sendTrigger('charge')">
              <Icons name="bolt" :size="14" />
              <span>Charge</span>
            </button>
            <button type="button" class="sim-chip" @click="sendTrigger('media')">
              <Icons name="music" :size="14" />
              <span>Media</span>
            </button>
            <button type="button" class="sim-chip" @click="sendTrigger('volume')">
              <Icons name="wave" :size="14" />
              <span>Volume</span>
            </button>
            <button type="button" class="sim-chip" @click="sendTrigger('ringer')">
              <Icons name="circle" :size="14" />
              <span>Ringer</span>
            </button>
            <button type="button" class="sim-chip" @click="sendTrigger('notification')">
              <Icons name="lens" :size="14" />
              <span>Alert</span>
            </button>
            <button type="button" class="sim-chip" @click="sendTrigger('torch')">
              <Icons name="circle" :size="14" />
              <span>Torch</span>
            </button>
            <button type="button" class="sim-chip" @click="sendTrigger('hyperdl')">
              <Icons name="download" :size="14" />
              <span>Download</span>
            </button>
            <button type="button" class="sim-chip" @click="sendTrigger('idle')">
              <Icons name="check" :size="14" />
              <span>Idle</span>
            </button>
          </div>
        </section>

        <div class="section-title">Service daemon</div>
        <section class="md3-card">
          <button type="button" class="action-btn-primary" @click="restartDaemon">
            <Icons name="refresh" :size="15" />
            <span>Restart service</span>
          </button>
          <button type="button" class="action-btn-secondary" @click="toggleLogView">
            <Icons name="terminal" :size="15" />
            <span>{{ showLogs ? 'Hide log output' : 'Inspect log output' }}</span>
          </button>

          <!-- Collapsible Dark Terminal -->
          <div v-if="showLogs" class="terminal-container">
            <div class="terminal-header">
              <span>overlay.log</span>
              <button type="button" class="terminal-refresh-btn" @click="fetchLogs">Refresh</button>
            </div>
            <pre class="terminal-body">{{ logContent || 'No log entries recorded.' }}</pre>
          </div>
        </section>
      </div>

      <!-- Floating Toast Notice -->
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
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import Icons from '@/components/icons/Icons.vue'

const currentTab = ref('cutout')

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

// Live Simulator Bezel Projections
const simCutoutLeft = computed(() => {
  const normX = Math.max(0, Math.min(1080, config.cutout_x))
  return ((normX / 1080) * 100).toFixed(1)
})

const simCutoutTop = computed(() => {
  return Math.max(12, Math.min(48, Math.round(config.cutout_y * 0.45)))
})

const simCutoutSize = computed(() => {
  return Math.max(12, Math.min(26, Math.round(config.cutout_radius * 0.45)))
})

const simPillLeft = computed(() => {
  const cLeft = parseFloat(simCutoutLeft.value)
  if (Math.abs(cLeft - 50) < 15) return 50
  if (cLeft < 35) return Math.max(14, cLeft + 6)
  return Math.min(86, cLeft - 6)
})

const simPillTop = computed(() => {
  return simCutoutTop.value
})

const simPillWidth = computed(() => {
  return 92
})

const simPillHeight = computed(() => {
  return Math.max(20, Math.round(config.cutout_radius * 0.55))
})

// Dual-Bridge Shell Runner (Supports KernelSU and APatch/MMRL/Magisk WebRoot)
function execShell(cmd) {
  return new Promise(resolve => {
    if (typeof ksu !== 'undefined' && typeof ksu.exec === 'function') {
      const id = '_hr_' + Date.now() + '_' + Math.floor(Math.random() * 1000)
      window[id] = (errno, stdout, stderr) => {
        delete window[id]
        resolve(stdout || stderr || '')
      }
      try { ksu.exec(cmd, '{}', id) } catch (e) { resolve('') }
    } else if (typeof exec === 'function') {
      try {
        exec(cmd, (errno, stdout, stderr) => {
          resolve(stdout || stderr || '')
        })
      } catch (e) { resolve('') }
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
  showToast(`Applied ${type} position`)
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
    showToast('Auto detection default applied')
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
  showToast(`Applied ${profile} curve`)
}

async function sendTrigger(cmd) {
  await execShell(`echo "${cmd}" > /data/adb/modules/hyperring/state/trigger.cmd`)
  showToast(`Sent trigger: ${cmd}`)
  setTimeout(queryStatusFile, 600)
}

async function triggerCalibration() {
  await sendTrigger('calibrate')
  showToast('Reticle visible on screen')
}

async function restartDaemon() {
  showToast('Restarting service...')
  await execShell('sh /data/adb/modules/hyperring/action.sh 2>/dev/null')
  setTimeout(async () => {
    await queryStatusFile()
    showToast(isDaemonAlive.value ? `Active · PID ${daemonPid.value}` : 'Service started')
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
  }, 2200)
}

onMounted(() => {
  loadConfiguration()
  queryStatusFile()
  statusPollInterval = setInterval(queryStatusFile, 3000)

  // Status bar safe area fallback for KernelSU / APatch / MMRL / WebView
  const cs = getComputedStyle(document.documentElement)
  const winTop = parseInt(cs.getPropertyValue('--window-inset-top')) || 0
  if (winTop === 0) {
    const div = document.createElement('div')
    div.style.paddingTop = 'env(safe-area-inset-top, 0px)'
    document.body.appendChild(div)
    const envTop = parseInt(getComputedStyle(div).paddingTop) || 0
    document.body.removeChild(div)
    document.documentElement.style.setProperty('--window-inset-top', `${envTop > 0 ? envTop : 36}px`)
  }
})

onUnmounted(() => {
  if (statusPollInterval) clearInterval(statusPollInterval)
})
</script>
