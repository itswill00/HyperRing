<template>
  <div class="app-shell">
    <!-- Header with proper safe insets -->
    <header class="page-header">
      <div class="brand">
        <div class="status-indicator"></div>
        <span class="title">HyperRing</span>
      </div>
      <span class="version-badge">1.0.0</span>
    </header>

    <main class="content-body">
      <!-- Live Status & Physical Preview -->
      <section class="panel preview-panel">
        <div class="stage-cutout">
          <div 
            class="island"
            :class="{ expanded: isExpandedView, active: liveState.active_island !== 'idle' }"
            :style="islandStyle"
          >
            <div class="lens-dot" :style="{ width: (config.cutout_radius * 0.7) + 'px', height: (config.cutout_radius * 0.7) + 'px' }"></div>

            <!-- Compact Media -->
            <div v-if="liveState.active_island === 'media' && !isExpandedView" class="compact-row">
              <span class="disc-dot"></span>
              <div class="wave-meter">
                <span></span><span></span><span></span><span></span>
              </div>
            </div>

            <!-- Compact Charging -->
            <div v-if="liveState.active_island === 'charging' && !isExpandedView" class="compact-row">
              <svg class="bolt-svg" viewBox="0 0 24 24" fill="none" stroke="#22C55E" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon>
              </svg>
              <span class="data-text">{{ liveState.battery_pct }}%</span>
            </div>

            <!-- Compact HyperDL -->
            <div v-if="liveState.active_island === 'hyperdl' && !isExpandedView" class="compact-row">
              <span class="disc-dot"></span>
              <span class="data-text">{{ liveState.hyperdl_speed }}</span>
            </div>

            <!-- Expanded Media -->
            <div v-if="isExpandedView && liveState.active_island === 'media'" class="expanded-media-card">
              <div class="media-art-plate">
                <div class="inner-groove"></div>
              </div>
              <div class="media-meta">
                <div class="meta-title">{{ liveState.media_title || 'Starboy' }}</div>
                <div class="meta-sub">{{ liveState.media_artist || 'The Weeknd' }}</div>
              </div>
              <div class="seek-track">
                <div class="seek-progress"></div>
              </div>
              <div class="media-actions">
                <span class="act-btn">Prev</span>
                <span class="act-btn bold">Pause</span>
                <span class="act-btn">Next</span>
              </div>
            </div>

            <!-- Expanded Charging -->
            <div v-if="isExpandedView && liveState.active_island === 'charging'" class="expanded-charge-card">
              <div class="charge-header-row">
                <svg class="bolt-svg large" viewBox="0 0 24 24" fill="none" stroke="#22C55E" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                  <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon>
                </svg>
                <div>
                  <div class="charge-status-lbl">Charging</div>
                  <div class="charge-status-pct">{{ liveState.battery_pct }}%</div>
                </div>
              </div>
              <div class="telemetry-bar">
                <span>33W Super Charge</span>
                <span>4200 mA</span>
                <span>{{ liveState.hypercore_profile || 'Interactive' }}</span>
              </div>
            </div>

            <!-- Expanded HyperDL -->
            <div v-if="isExpandedView && liveState.active_island === 'hyperdl'" class="expanded-charge-card">
              <div class="meta-title">Download in progress</div>
              <div class="meta-sub">{{ liveState.hyperdl_speed }} • {{ liveState.hyperdl_progress }}%</div>
              <div class="seek-track">
                <div class="seek-progress" :style="{ width: (liveState.hyperdl_progress || 40) + '%' }"></div>
              </div>
            </div>
          </div>
        </div>
        <div class="preview-footer">
          <span>Active State: {{ liveState.active_island }}</span>
          <span v-if="isExpandedView">• Expanded</span>
        </div>
      </section>

      <!-- Hardware Trigger Actions -->
      <section class="panel">
        <div class="panel-header">Hardware Event Triggers</div>
        <div class="grid-buttons">
          <button type="button" class="action-btn" @click="sendEvent('charge')">Charge event</button>
          <button type="button" class="action-btn" @click="sendEvent('media')">Media event</button>
          <button type="button" class="action-btn" @click="sendEvent('expand')">Expand view</button>
          <button type="button" class="action-btn" @click="sendEvent('collapse')">Collapse view</button>
        </div>
      </section>

      <!-- Cutout Alignment & Presets -->
      <section class="panel">
        <div class="panel-header">Cutout Hardware Alignment</div>
        
        <div class="preset-row">
          <button type="button" class="preset-btn" @click="applyPreset('center')">Center (Default)</button>
          <button type="button" class="preset-btn" @click="applyPreset('left')">Left Hole</button>
          <button type="button" class="preset-btn" @click="applyPreset('reset')">Auto Detect</button>
        </div>

        <div class="field-item">
          <div class="field-meta">
            <span>Center horizontal position</span>
            <span class="field-val">{{ config.cutout_x }} px</span>
          </div>
          <input type="range" min="0" max="1080" step="2" v-model.number="config.cutout_x" @input="saveConfigDebounced" />
        </div>

        <div class="field-item">
          <div class="field-meta">
            <span>Center vertical position</span>
            <span class="field-val">{{ config.cutout_y }} px</span>
          </div>
          <input type="range" min="10" max="150" step="1" v-model.number="config.cutout_y" @input="saveConfigDebounced" />
        </div>

        <div class="field-item">
          <div class="field-meta">
            <span>Camera cutout radius</span>
            <span class="field-val">{{ config.cutout_radius }} px</span>
          </div>
          <input type="range" min="15" max="60" step="1" v-model.number="config.cutout_radius" @input="saveConfigDebounced" />
        </div>
      </section>

      <!-- Harmonic Spring Parameters -->
      <section class="panel">
        <div class="panel-header">Physics Spring Model</div>

        <div class="field-item">
          <div class="field-meta">
            <span>Spring stiffness</span>
            <span class="field-val">{{ config.spring_stiffness }}</span>
          </div>
          <input type="range" min="200" max="600" step="10" v-model.number="config.spring_stiffness" @input="saveConfigDebounced" />
        </div>

        <div class="field-item">
          <div class="field-meta">
            <span>Damping ratio</span>
            <span class="field-val">{{ config.spring_damping }}</span>
          </div>
          <input type="range" min="0.50" max="0.95" step="0.02" v-model.number="config.spring_damping" @input="saveConfigDebounced" />
        </div>
      </section>

      <!-- Active Integrations -->
      <section class="panel">
        <div class="panel-header">Integrations & Behaviors</div>

        <label class="toggle-control">
          <span>Media player listener</span>
          <input type="checkbox" v-model="config.enable_media" @change="saveConfig" />
        </label>

        <label class="toggle-control">
          <span>Battery charging notification</span>
          <input type="checkbox" v-model="config.enable_charging" @change="saveConfig" />
        </label>

        <label class="toggle-control">
          <span>HyperDL download progress</span>
          <input type="checkbox" v-model="config.enable_hyperdl" @change="saveConfig" />
        </label>

        <label class="toggle-control">
          <span>HyperCore kernel profile sync</span>
          <input type="checkbox" v-model="config.enable_hypercore" @change="saveConfig" />
        </label>

        <label class="toggle-control">
          <span>Auto-expand card on charger connect</span>
          <input type="checkbox" v-model="config.auto_expand_charging" @change="saveConfig" />
        </label>
      </section>

      <!-- Process Management -->
      <section class="panel">
        <button type="button" class="btn-full" @click="restartOverlay">
          Restart HyperRing Process
        </button>
        <div v-if="toastMessage" class="toast-feedback">{{ toastMessage }}</div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'

const config = reactive({
  cutout_x: 540,
  cutout_y: 52,
  cutout_radius: 36,
  enable_media: true,
  enable_charging: true,
  enable_hyperdl: true,
  enable_hypercore: true,
  spring_stiffness: 360.0,
  spring_damping: 0.76,
  auto_expand_charging: true,
  expand_timeout_ms: 3500
})

const liveState = reactive({
  active_island: 'media',
  expanded: false,
  media_title: 'Starboy',
  media_artist: 'The Weeknd',
  media_playing: true,
  battery_pct: 85,
  battery_charging: true,
  hypercore_profile: 'Interactive',
  hyperdl_active: false,
  hyperdl_speed: '8.4 MB/s',
  hyperdl_progress: 45
})

const isExpandedView = ref(false)
const toastMessage = ref('')
let configTimer = null
let pollTimer = null

const islandStyle = computed(() => {
  if (isExpandedView.value) {
    return {
      width: '280px',
      height: liveState.active_island === 'media' ? '118px' : '96px',
      borderRadius: '22px'
    }
  } else if (liveState.active_island !== 'idle') {
    return {
      width: '136px',
      height: '34px',
      borderRadius: '17px'
    }
  }
  return {
    width: (config.cutout_radius * 2) + 'px',
    height: (config.cutout_radius * 2) + 'px',
    borderRadius: '50%'
  }
})

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

async function loadConfig() {
  try {
    const raw = await execShell('cat /data/adb/modules/hyperring/state/config.json 2>/dev/null')
    if (raw && raw.trim().startsWith('{')) {
      const data = JSON.parse(raw.trim())
      Object.assign(config, data)
    }
  } catch (e) {}
}

async function pollLiveStatus() {
  try {
    const raw = await execShell('cat /data/adb/modules/hyperring/state/status.json 2>/dev/null')
    if (raw && raw.trim().startsWith('{')) {
      const data = JSON.parse(raw.trim())
      Object.assign(liveState, data)
    }
  } catch (e) {}
}

function saveConfigDebounced() {
  clearTimeout(configTimer)
  configTimer = setTimeout(saveConfig, 350)
}

async function saveConfig() {
  const payload = JSON.stringify(config, null, 2).replace(/'/g, "'\\''")
  await execShell(`mkdir -p /data/adb/modules/hyperring/state && echo '${payload}' > /data/adb/modules/hyperring/state/config.json`)
}

function applyPreset(preset) {
  if (preset === 'center') {
    config.cutout_x = 540
    config.cutout_y = 52
    config.cutout_radius = 36
  } else if (preset === 'left') {
    config.cutout_x = 120
    config.cutout_y = 52
    config.cutout_radius = 36
  } else if (preset === 'reset') {
    config.cutout_x = 540
    config.cutout_y = 52
    config.cutout_radius = 36
  }
  saveConfig()
  showToast(`Preset: ${preset}`)
}

async function sendEvent(name) {
  if (name === 'charge') {
    liveState.active_island = 'charging'
    isExpandedView.value = false
  } else if (name === 'media') {
    liveState.active_island = 'media'
    isExpandedView.value = false
  } else if (name === 'expand') {
    isExpandedView.value = true
  } else if (name === 'collapse') {
    isExpandedView.value = false
  }

  await execShell(`sh /data/adb/modules/hyperring/action.sh ${name} 2>/dev/null`)
  showToast(`Trigger sent: ${name}`)
}

async function restartOverlay() {
  showToast('Restarting HyperRing service...')
  await execShell('sh /data/adb/modules/hyperring/action.sh 2>/dev/null')
  showToast('Service restarted successfully')
  pollLiveStatus()
}

function showToast(msg) {
  toastMessage.value = msg
  setTimeout(() => {
    if (toastMessage.value === msg) toastMessage.value = ''
  }, 2200)
}

onMounted(() => {
  loadConfig()
  pollLiveStatus()
  pollTimer = setInterval(pollLiveStatus, 3000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style>
@import url('https://mui.kernelsu.org/internal/insets.css');
@import url('https://mui.kernelsu.org/internal/colors.css');

:root {
  --bg: #121316;
  --on-bg: #f0f2f5;
  --surface: #121316;
  --on-surface: #f0f2f5;
  --surface-container-low: #17181c;
  --surface-container: #1d1e23;
  --surface-container-high: #26272e;
  --surface-container-highest: #30323a;
  --outline: #727682;
  --outline-variant: #383a42;
  --primary: #38bdf8;
  --on-primary: #18191c;
  --window-inset-top: env(safe-area-inset-top, 0px);
  --window-inset-bottom: env(safe-area-inset-bottom, 0px);
}

*, *::before, *::after {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
  font-family: inherit;
  -webkit-tap-highlight-color: transparent;
}

html, body, #app {
  height: 100%;
  width: 100%;
  background-color: var(--bg);
  color: var(--on-bg);
  font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  font-variant-numeric: tabular-nums;
  -webkit-font-smoothing: antialiased;
  text-rendering: optimizeLegibility;
  overflow-x: hidden;
  overflow-y: auto;
  user-select: none;
}
</style>

<style scoped>
.app-shell {
  min-height: 100%;
  width: 100%;
  max-width: 480px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  background: var(--bg);
  padding-bottom: calc(24px + var(--window-inset-bottom, 0px));
}

.page-header {
  position: sticky;
  top: 0;
  z-index: 50;
  background: var(--bg);
  padding-top: calc(14px + var(--window-inset-top, 0px));
  padding-bottom: 14px;
  padding-left: 16px;
  padding-right: 16px;
  border-bottom: 1px solid var(--surface-container-high);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary);
}

.title {
  font-size: 18px;
  font-weight: 600;
  color: #FFFFFF;
}

.version-badge {
  font-size: 11px;
  color: #94A3B8;
  background: var(--surface-container-high);
  padding: 3px 8px;
  border-radius: 6px;
  font-weight: 500;
}

.content-body {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.panel {
  background: var(--surface-container-low);
  border: 1px solid var(--surface-container-high);
  border-radius: 14px;
  padding: 14px 16px;
}

.panel-header {
  font-size: 11px;
  color: #94A3B8;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  font-weight: 600;
  margin-bottom: 12px;
}

.preview-panel {
  padding: 20px 16px 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #000000;
  border-color: #1F222A;
}

.stage-cutout {
  width: 100%;
  height: 120px;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding-top: 4px;
}

.island {
  background: #000000;
  border: 1px solid rgba(255, 255, 255, 0.12);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 12px;
  transition: all 0.36s cubic-bezier(0.34, 1.45, 0.64, 1);
  overflow: hidden;
  position: relative;
}

.lens-dot {
  background: #050505;
  border: 1px solid #141414;
  border-radius: 50%;
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
}

.compact-row {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  z-index: 2;
}

.disc-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary);
}

.wave-meter {
  display: flex;
  gap: 2px;
  align-items: center;
}
.wave-meter span {
  width: 2px;
  height: 10px;
  background: var(--primary);
  border-radius: 1px;
  animation: wavePulse 0.8s infinite alternate ease-in-out;
}
.wave-meter span:nth-child(2) { animation-delay: 0.2s; height: 13px; }
.wave-meter span:nth-child(3) { animation-delay: 0.4s; height: 7px; }
.wave-meter span:nth-child(4) { animation-delay: 0.1s; height: 11px; }

@keyframes wavePulse {
  from { transform: scaleY(0.35); }
  to { transform: scaleY(1); }
}

.bolt-svg {
  width: 13px;
  height: 13px;
}
.bolt-svg.large {
  width: 22px;
  height: 22px;
}

.data-text {
  font-size: 11.5px;
  font-weight: 600;
  color: #22C55E;
}

.expanded-media-card {
  width: 100%;
  height: 100%;
  display: grid;
  grid-template-columns: 44px 1fr;
  grid-template-rows: 44px auto auto;
  gap: 8px;
  padding: 4px;
  z-index: 2;
}

.media-art-plate {
  width: 44px;
  height: 44px;
  background: rgba(56, 189, 248, 0.12);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.inner-groove {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 2px solid var(--primary);
}

.media-meta {
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.meta-title {
  font-size: 13px;
  font-weight: 600;
  color: #FFFFFF;
}
.meta-sub {
  font-size: 11.5px;
  color: #94A3B8;
}

.seek-track {
  grid-column: span 2;
  height: 2px;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 1px;
}
.seek-progress {
  width: 42%;
  height: 100%;
  background: var(--primary);
  border-radius: 1px;
}

.media-actions {
  grid-column: span 2;
  display: flex;
  justify-content: center;
  gap: 14px;
  padding-top: 2px;
}
.act-btn {
  font-size: 11px;
  color: #94A3B8;
  cursor: pointer;
}
.act-btn.bold {
  font-weight: 600;
  color: #FFFFFF;
}

.expanded-charge-card {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 6px;
  z-index: 2;
}

.charge-header-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.charge-status-lbl {
  font-size: 10px;
  color: #94A3B8;
  text-transform: uppercase;
}
.charge-status-pct {
  font-size: 16px;
  font-weight: 600;
  color: #22C55E;
}

.telemetry-bar {
  display: flex;
  gap: 12px;
  font-size: 11px;
  color: #64748B;
  padding-top: 6px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.preview-footer {
  font-size: 11px;
  color: #64748B;
  display: flex;
  gap: 6px;
  margin-top: 6px;
}

.grid-buttons {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.action-btn {
  background: var(--surface-container);
  border: 1px solid var(--surface-container-high);
  border-radius: 8px;
  padding: 10px;
  font-size: 12px;
  font-weight: 500;
  color: var(--on-surface);
  cursor: pointer;
}
.action-btn:active {
  background: var(--surface-container-highest);
}

.preset-row {
  display: flex;
  gap: 6px;
  margin-bottom: 12px;
}
.preset-btn {
  flex: 1;
  background: var(--surface-container);
  border: 1px solid var(--surface-container-high);
  border-radius: 6px;
  padding: 7px 4px;
  font-size: 11px;
  color: var(--on-surface-variant);
  cursor: pointer;
}
.preset-btn:active {
  background: var(--surface-container-highest);
}

.field-item {
  margin-bottom: 12px;
}
.field-item:last-child {
  margin-bottom: 0;
}

.field-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12.5px;
  color: var(--on-surface-variant);
  margin-bottom: 6px;
}
.field-val {
  color: var(--primary);
  font-weight: 500;
}

input[type="range"] {
  width: 100%;
  accent-color: var(--primary);
  height: 4px;
  background: var(--surface-container-high);
  border-radius: 2px;
}

.toggle-control {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12.5px;
  color: var(--on-surface);
  padding: 9px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.03);
  cursor: pointer;
}
.toggle-control:last-child {
  border-bottom: none;
}
.toggle-control input[type="checkbox"] {
  width: 16px;
  height: 16px;
  accent-color: var(--primary);
}

.btn-full {
  width: 100%;
  background: var(--surface-container);
  border: 1px solid var(--surface-container-high);
  border-radius: 8px;
  color: var(--primary);
  font-size: 13px;
  font-weight: 500;
  padding: 12px;
  cursor: pointer;
}
.btn-full:active {
  background: var(--surface-container-highest);
}

.toast-feedback {
  text-align: center;
  font-size: 11.5px;
  color: var(--primary);
  margin-top: 8px;
}
</style>
