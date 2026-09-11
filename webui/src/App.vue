<template>
  <div class="app-shell">
    <!-- Top Header -->
    <header class="page-header">
      <div>
        <div class="page-header-title">HyperRing</div>
        <div class="page-header-sub">Dynamic punch-hole island</div>
      </div>
      <div style="display: flex; align-items: center; gap: 8px;">
        <button
          v-if="isReticleActive"
          type="button"
          class="badge-pill active"
          style="cursor: pointer;"
          @click="toggleReticle"
        >
          Reticle active
        </button>
        <span class="badge-pill" :class="isDaemonAlive ? 'active' : 'standby'">
          {{ isDaemonAlive ? (daemonPid ? `PID ${daemonPid}` : 'Active') : 'Standby' }}
        </span>
        <button
          type="button"
          class="theme-toggle-btn"
          :title="`Theme: ${themeMode}`"
          @click="cycleTheme"
        >
          <Icons :name="themeMode === 'auto' ? 'monitor' : (themeMode === 'light' ? 'sun' : 'moon')" :size="15" />
        </button>
      </div>
    </header>

    <!-- Segmented Navigation Tabs -->
    <nav class="tabs-nav">
      <button
        type="button"
        class="tab-btn"
        :class="{ active: currentTab === 'placement' }"
        @click="currentTab = 'placement'"
      >
        Placement
      </button>
      <button
        type="button"
        class="tab-btn"
        :class="{ active: currentTab === 'geometry' }"
        @click="currentTab = 'geometry'"
      >
        Geometry
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

    <!-- Main Scrollable Content Container -->
    <main class="content-area">
      <!-- Master Island Enable / Disable Card -->
      <section class="master-toggle-card" :class="{ 'is-disabled': !config.enabled }">
        <div class="master-toggle-info">
          <div class="master-toggle-title">
            <span>Dynamic island</span>
            <span class="master-status-chip" :class="config.enabled ? 'chip-on' : 'chip-off'">
              {{ config.enabled ? 'Active' : 'Paused' }}
            </span>
          </div>
          <div class="master-toggle-sub">
            {{ config.enabled ? 'Overlay is running and responding to events' : 'Overlay and background listeners are paused' }}
          </div>
        </div>
        <label class="md3-switch" @click.stop>
          <input type="checkbox" v-model="config.enabled" @change="onMasterToggle" />
          <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
        </label>
      </section>

      <!-- Persistent Live Preview Widget -->
      <LivePreviewBar
        v-if="currentTab === 'placement' || currentTab === 'geometry'"
        :preview-mode="previewMode"
        :preview-mode-label="previewModeLabel"
        @set-preview-mode="setPreviewMode"
      />

      <!-- Tab 1: Cutout & Position -->
      <TabPlacement
        v-if="currentTab === 'placement'"
        :config="config"
        :live-state="liveState"
        :preview-mode="previewMode"
        :is-reticle-active="isReticleActive"
        :dpad-step="dpadStep"
        :sim-cutout-left="parseFloat(simCutoutLeft)"
        :sim-cutout-top="simCutoutTop"
        :sim-cutout-size="simCutoutSize"
        :sim-pill-left="parseFloat(simPillLeft)"
        :sim-pill-top="simPillTop"
        :sim-pill-width="simPillWidth"
        :sim-pill-height="simPillHeight"
        :sim-card-width="simCardWidth"
        :sim-card-height="simCardHeight"
        :sim-card-radius="simCardRadius"
        @toggle-reticle="toggleReticle"
        @set-alignment="setAlignment"
        @toggle-config="toggleConfig"
        @save-config="saveConfig"
        @save-debounced="saveConfigDebounced"
        @nudge="nudge"
        @update-dpad-step="step => dpadStep = step"
        @step-value="stepValue"
      />

      <!-- Tab 2: Geometry & Scale -->
      <TabGeometry
        v-else-if="currentTab === 'geometry'"
        :config="config"
        :preview-mode="previewMode"
        :sim-cutout-left="parseFloat(simCutoutLeft)"
        :sim-cutout-top="simCutoutTop"
        :sim-cutout-size="simCutoutSize"
        :sim-pill-left="parseFloat(simPillLeft)"
        :sim-pill-top="simPillTop"
        :sim-pill-width="simPillWidth"
        :sim-pill-height="simPillHeight"
        :sim-card-width="simCardWidth"
        :sim-card-height="simCardHeight"
        :sim-card-radius="simCardRadius"
        @save-debounced="saveConfigDebounced"
        @set-card-mode="setCardPositionMode"
        @step-pill-width="stepPillWidth"
        @step-pill-height="stepPillHeight"
        @step-card-width="stepCardWidth"
        @step-card-height="stepCardHeight"
        @step-value="stepValue"
      />

      <!-- Tab 3: Events & Features -->
      <TabFeatures
        v-else-if="currentTab === 'features'"
        :config="config"
        @toggle-config="toggleConfig"
        @save-config="saveConfig"
        @save-debounced="saveConfigDebounced"
        @step-value="stepValue"
      />

      <!-- Tab 4: Motion Physics -->
      <TabMotion
        v-else-if="currentTab === 'motion'"
        :config="config"
        :current-motion-profile="currentMotionProfile"
        @save-debounced="saveConfigDebounced"
        @apply-motion-preset="applyMotionPreset"
      />

      <!-- Tab 5: Tools & Diagnostics -->
      <TabTools
        v-else-if="currentTab === 'tools'"
        :live-state="liveState"
        :daemon-pid="daemonPid"
        :show-logs="showLogs"
        :log-content="logContent"
        @send-trigger="sendTrigger"
        @toggle-reticle="toggleReticle"
        @restart-daemon="restartDaemon"
        @toggle-log-view="toggleLogView"
        @fetch-logs="fetchLogs"
        @reset-defaults="resetDefaults"
      />

      <!-- Toast Feedback Pill -->
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
import LivePreviewBar from '@/components/LivePreviewBar.vue'
import TabPlacement from '@/components/TabPlacement.vue'
import TabGeometry from '@/components/TabGeometry.vue'
import TabFeatures from '@/components/TabFeatures.vue'
import TabMotion from '@/components/TabMotion.vue'
import TabTools from '@/components/TabTools.vue'

const currentTab = ref('placement')
const dpadStep = ref(1)

const config = reactive({
  enabled: true,
  cutout_x: 540,
  cutout_y: 55,
  cutout_radius: 28,
  pill_alignment: 'center',
  notch_mode: false,
  x_offset: 0,
  y_offset: 0,
  pill_width: 0,
  pill_height: 0,
  card_width: 0,
  card_height: 0,
  card_radius: 24,
  card_y_offset: 0,
  card_position_mode: 'below',
  enable_media: true,
  media_show_pill_art: true,
  media_art_style: 'rounded',
  media_show_waveform: true,
  media_pulse_color: 'auto',
  media_ambient_glow: true,
  media_glow_opacity: 25,
  media_marquee: true,
  enable_charging: true,
  enable_volume: true,
  enable_ringer: true,
  enable_notifications: true,
  enable_torch: true,
  enable_progress: true,
  enable_haptics: true,
  stealth_ring_idle: false,
  hide_in_landscape: true,
  spring_stiffness: 380.0,
  spring_damping: 0.78,
  auto_expand_charging: true,
  auto_expand_media: false,
  auto_expand_notification: false,
  auto_expand_progress: false,
  expand_timeout_ms: 3500
})

const liveState = reactive({
  pid: 0,
  active_island: 'idle',
  expanded: false,
  media_title: '',
  media_artist: '',
  media_playing: false,
  battery_pct: 100,
  battery_charging: false,
  download_app: '',
  download_title: '',
  progress_pct: 0
})

const isDaemonAlive = ref(true)
const daemonPid = ref(0)
const toastNotice = ref('')
const currentMotionProfile = ref('fluid')
const showLogs = ref(false)
const logContent = ref('')
const themeMode = ref('auto')

function applyTheme(mode) {
  themeMode.value = mode
  try {
    localStorage.setItem('hyperring_theme', mode)
  } catch (_) {}

  if (mode === 'auto') {
    document.documentElement.removeAttribute('data-theme')
  } else {
    document.documentElement.setAttribute('data-theme', mode)
  }
}

function cycleTheme() {
  if (themeMode.value === 'auto') {
    applyTheme('dark')
  } else if (themeMode.value === 'dark') {
    applyTheme('light')
  } else {
    applyTheme('auto')
  }
  showToast(`Theme: ${themeMode.value}`)
}

let configDebounceTimer = null
let statusPollInterval = null
let toastTimer = null

const isReticleActive = computed(() => liveState.active_island === 'calibration' || previewMode.value === 'reticle')

const previewMode = ref('off')
const previewModeLabel = computed(() => {
  if (previewMode.value === 'reticle') return 'Camera alignment reticle locked on screen'
  if (previewMode.value === 'pill') return 'Compact pill locked for positioning'
  if (previewMode.value === 'card') return 'Expanded card locked for sizing'
  return 'Normal interactive mode'
})

async function setPreviewMode(mode) {
  previewMode.value = mode
  if (mode === 'reticle') {
    await sendTrigger('preview:reticle')
    showToast('Reticle locked on screen')
  } else if (mode === 'pill') {
    await sendTrigger('preview:pill')
    showToast('Compact pill locked on screen')
  } else if (mode === 'card') {
    await sendTrigger('preview:expanded')
    showToast('Expanded card locked on screen')
  } else {
    await sendTrigger('preview:off')
    showToast('Live preview dismissed')
  }
}

function setAlignment(align) {
  config.pill_alignment = align
  saveConfig()
  showToast(`Pill alignment: ${align}`)
}

// Live Simulator Bezel Projections
const simCutoutLeft = computed(() => {
  const normX = Math.max(0, Math.min(1080, config.cutout_x + config.x_offset))
  return ((normX / 1080) * 100).toFixed(1)
})

const simCutoutTop = computed(() => {
  return Math.max(10, Math.min(50, Math.round((config.cutout_y + config.y_offset) * 0.42)))
})

const simCutoutSize = computed(() => {
  return Math.max(12, Math.min(28, Math.round(config.cutout_radius * 0.44)))
})

const simPillLeft = computed(() => {
  const cLeft = parseFloat(simCutoutLeft.value)
  if (config.pill_alignment === 'left') return 18
  if (config.pill_alignment === 'right') return 82
  return cLeft
})

const simPillTop = computed(() => simCutoutTop.value)

const simPillWidth = computed(() => {
  if (config.pill_width > 0) {
    return Math.max(70, Math.min(130, Math.round(config.pill_width * 0.65)))
  }
  return 96
})

const simPillHeight = computed(() => {
  if (config.pill_height > 0) {
    return Math.max(18, Math.min(30, Math.round(config.pill_height * 0.6)))
  }
  return Math.max(20, Math.round(config.cutout_radius * 0.55))
})

const simCardWidth = computed(() => {
  if (config.card_width > 0) {
    return Math.max(120, Math.min(200, Math.round(config.card_width * 0.52)))
  }
  return 160
})

const simCardHeight = computed(() => {
  if (config.card_height > 0) {
    return Math.max(36, Math.min(52, Math.round(config.card_height * 0.28)))
  }
  return 44
})

const simCardRadius = computed(() => {
  return Math.round((config.card_radius || 24) * 0.35)
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
      if (typeof config.enabled !== 'boolean') {
        config.enabled = true
      }
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
      if (parsed.active_island === 'calibration') {
        previewMode.value = 'reticle'
      } else if (parsed.preview_lock && parsed.expanded) {
        previewMode.value = 'card'
      } else if (parsed.preview_lock && !parsed.expanded) {
        previewMode.value = 'pill'
      } else if (!parsed.preview_lock && previewMode.value !== 'off') {
        previewMode.value = 'off'
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
  configDebounceTimer = setTimeout(saveConfig, 150)
}

async function saveConfig() {
  try {
    const jsonStr = JSON.stringify(config)
    const b64 = btoa(unescape(encodeURIComponent(jsonStr)))
    await execShell(`mkdir -p /data/adb/modules/hyperring/state && echo '${b64}' | base64 -d > /data/adb/modules/hyperring/state/config.json`)
  } catch (e) {}
}

async function onMasterToggle() {
  saveConfig()
  if (!config.enabled) {
    await sendTrigger('preview:off')
    await sendTrigger('idle')
    showToast('Dynamic island paused')
  } else {
    showToast('Dynamic island active')
  }
}

function toggleConfig(key) {
  config[key] = !config[key]
  saveConfig()
}

function stepValue(key, delta, min, max) {
  let val = config[key] + delta
  if (min !== undefined) val = Math.max(min, val)
  if (max !== undefined) val = Math.min(max, val)
  config[key] = Math.round(val * 100) / 100
  saveConfigDebounced()
}

function stepPillWidth(delta) {
  if (config.pill_width === 0) {
    config.pill_width = delta > 0 ? 140 : 0
  } else {
    config.pill_width = Math.max(0, Math.min(340, config.pill_width + delta))
    if (config.pill_width < 70) config.pill_width = 0
  }
  saveConfigDebounced()
}

function stepPillHeight(delta) {
  if (config.pill_height === 0) {
    config.pill_height = delta > 0 ? 34 : 0
  } else {
    config.pill_height = Math.max(0, Math.min(60, config.pill_height + delta))
    if (config.pill_height < 20) config.pill_height = 0
  }
  saveConfigDebounced()
}

function stepCardWidth(delta) {
  if (config.card_width === 0) {
    config.card_width = delta > 0 ? 320 : 0
  } else {
    config.card_width = Math.max(0, Math.min(420, config.card_width + delta))
    if (config.card_width < 220) config.card_width = 0
  }
  saveConfigDebounced()
}

function stepCardHeight(delta) {
  if (config.card_height === 0) {
    config.card_height = delta > 0 ? 130 : 0
  } else {
    config.card_height = Math.max(0, Math.min(220, config.card_height + delta))
    if (config.card_height < 70) config.card_height = 0
  }
  saveConfigDebounced()
}

function setCardPositionMode(mode) {
  config.card_position_mode = mode
  saveConfig()
  showToast(mode === 'below' ? 'Floating below cutout' : 'Cover status bar mode')
}

function nudge(dx, dy) {
  config.x_offset = Math.max(-250, Math.min(250, config.x_offset + dx * dpadStep.value))
  config.y_offset = Math.max(-150, Math.min(200, config.y_offset + dy * dpadStep.value))
  saveConfigDebounced()
  showToast(`Offset X: ${config.x_offset > 0 ? `+${config.x_offset}` : config.x_offset}px · Y: ${config.y_offset > 0 ? `+${config.y_offset}` : config.y_offset}px`)
}

async function toggleReticle() {
  if (isReticleActive.value) {
    previewMode.value = 'off'
    await sendTrigger('calibrate_off')
    showToast('Reticle dismissed')
  } else {
    previewMode.value = 'reticle'
    await sendTrigger('calibrate')
    showToast('Reticle active on screen')
  }
}

function resetDefaults() {
  Object.assign(config, {
    enabled: true,
    cutout_x: 540,
    cutout_y: 55,
    cutout_radius: 28,
    pill_alignment: 'center',
    notch_mode: false,
    x_offset: 0,
    y_offset: 0,
    pill_width: 0,
    pill_height: 0,
    card_width: 0,
    card_height: 0,
    card_radius: 24,
    card_y_offset: 0,
    card_position_mode: 'below',
    enable_media: true,
    media_show_pill_art: true,
    media_art_style: 'rounded',
    media_show_waveform: true,
    media_pulse_color: 'auto',
    media_ambient_glow: true,
    media_glow_opacity: 25,
    media_marquee: true,
    enable_charging: true,
    enable_volume: true,
    enable_ringer: true,
    enable_notifications: true,
    enable_torch: true,
    enable_progress: true,
    enable_haptics: true,
    stealth_ring_idle: false,
    hide_in_landscape: true,
    spring_stiffness: 380.0,
    spring_damping: 0.78,
    auto_expand_charging: true,
    auto_expand_media: false,
    auto_expand_notification: false,
    auto_expand_progress: false,
    expand_timeout_ms: 3500
  })
  saveConfig()
  showToast('Configuration reset to defaults')
}

function applyMotionPreset(profile) {
  currentMotionProfile.value = profile
  if (profile === 'fluid') {
    config.spring_stiffness = 380.0
    config.spring_damping = 0.78
  } else if (profile === 'kinetic') {
    config.spring_stiffness = 450.0
    config.spring_damping = 0.86
  } else if (profile === 'snappy') {
    config.spring_stiffness = 540.0
    config.spring_damping = 0.92
  } else if (profile === 'float') {
    config.spring_stiffness = 260.0
    config.spring_damping = 0.72
  }
  saveConfig()
  showToast(`Applied ${profile} motion curve`)
}

async function sendTrigger(cmd) {
  await execShell(`echo "${cmd}" > /data/adb/modules/hyperring/state/trigger.cmd`)
  showToast(`Sent trigger: ${cmd}`)
  setTimeout(queryStatusFile, 500)
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
  try {
    const savedTheme = localStorage.getItem('hyperring_theme') || 'auto'
    applyTheme(savedTheme)
  } catch (_) {}
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
