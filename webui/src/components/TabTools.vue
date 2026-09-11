<template>
  <div>
    <!-- Live Island Telemetry Status -->
    <div class="section-title">Island live telemetry</div>
    <section class="md3-card">
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px; font-size: 11px;">
        <div style="background: var(--surface-container-high); padding: 8px 10px; border-radius: 8px;">
          <div style="color: var(--on-surface-variant); font-size: 10px; margin-bottom: 2px;">Active Island</div>
          <div style="color: var(--on-surface); font-weight: 600; text-transform: capitalize;">
            {{ liveState.active_island || 'Idle' }}
          </div>
        </div>
        <div style="background: var(--surface-container-high); padding: 8px 10px; border-radius: 8px;">
          <div style="color: var(--on-surface-variant); font-size: 10px; margin-bottom: 2px;">Battery / Power</div>
          <div style="color: var(--on-surface); font-weight: 600;">
            {{ liveState.battery_pct }}% · {{ liveState.battery_charging ? 'Charging' : 'Discharging' }}
          </div>
        </div>
        <div style="background: var(--surface-container-high); padding: 8px 10px; border-radius: 8px;">
          <div style="color: var(--on-surface-variant); font-size: 10px; margin-bottom: 2px;">Now Playing</div>
          <div style="color: var(--on-surface); font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
            {{ liveState.media_playing ? (liveState.media_title || 'Media active') : 'Inactive' }}
          </div>
        </div>
        <div style="background: var(--surface-container-high); padding: 8px 10px; border-radius: 8px;">
          <div style="color: var(--on-surface-variant); font-size: 10px; margin-bottom: 2px;">Overlay Daemon</div>
          <div style="color: var(--on-surface); font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
            {{ daemonPid ? `PID ${daemonPid} (Active)` : 'Standby' }}
          </div>
        </div>
      </div>
      <!-- Download Telemetry banner if active -->
      <div v-if="liveState.active_island === 'progress' || liveState.download_title" style="margin-top: 8px; background: rgba(56, 189, 248, 0.1); border: 1px solid rgba(56, 189, 248, 0.25); padding: 8px 10px; border-radius: 8px; font-size: 11px;">
        <div style="display: flex; justify-content: space-between; margin-bottom: 4px;">
          <span style="color: #38bdf8; font-weight: 600;">{{ liveState.download_app || 'Downloading' }}</span>
          <span style="color: #38bdf8; font-weight: 700;">{{ liveState.progress_pct ?? 0 }}%</span>
        </div>
        <div style="color: var(--on-surface-variant); font-size: 10px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
          {{ liveState.download_title || 'Active file transfer' }}
        </div>
      </div>
    </section>

    <div class="section-title">Live event triggers</div>
    <section class="md3-card">
      <div class="action-chips-grid">
        <button type="button" class="sim-chip" @click="emit('send-trigger', 'charge')">
          <Icons name="bolt" :size="14" />
          <span>Charge</span>
        </button>
        <button type="button" class="sim-chip" @click="emit('send-trigger', 'media')">
          <Icons name="music" :size="14" />
          <span>Media</span>
        </button>
        <button type="button" class="sim-chip" @click="emit('send-trigger', 'volume')">
          <Icons name="wave" :size="14" />
          <span>Volume</span>
        </button>
        <button type="button" class="sim-chip" @click="emit('send-trigger', 'ringer')">
          <Icons name="vibrate" :size="14" />
          <span>Ringer</span>
        </button>
        <button type="button" class="sim-chip" @click="emit('send-trigger', 'notification')">
          <Icons name="bell" :size="14" />
          <span>Notif</span>
        </button>
        <button type="button" class="sim-chip" @click="emit('send-trigger', 'torch')">
          <Icons name="power" :size="14" />
          <span>Torch</span>
        </button>
        <button type="button" class="sim-chip" @click="emit('send-trigger', 'download:Chrome:hyperring_update.apk:68')">
          <Icons name="download" :size="14" />
          <span>Download</span>
        </button>
        <button type="button" class="sim-chip" @click="emit('toggle-reticle')">
          <Icons name="crosshair" :size="14" />
          <span>Reticle</span>
        </button>
        <button type="button" class="sim-chip" @click="emit('send-trigger', 'idle')">
          <Icons name="check" :size="14" />
          <span>Idle</span>
        </button>
      </div>
      <div style="display: flex; gap: 8px; margin-top: 10px;">
        <button type="button" class="action-btn-secondary" style="margin-top: 0; flex: 1;" @click="emit('send-trigger', 'expand')">
          <Icons name="maximize" :size="14" />
          <span>Expand card</span>
        </button>
        <button type="button" class="action-btn-secondary" style="margin-top: 0; flex: 1;" @click="emit('send-trigger', 'collapse')">
          <Icons name="minus" :size="14" />
          <span>Collapse pill</span>
        </button>
      </div>
    </section>

    <div class="section-title">Service daemon</div>
    <section class="md3-card">
      <button type="button" class="action-btn-primary" @click="emit('restart-daemon')">
        <Icons name="refresh" :size="15" />
        <span>Restart service</span>
      </button>
      <button type="button" class="action-btn-secondary" @click="emit('toggle-log-view')">
        <Icons name="terminal" :size="15" />
        <span>{{ showLogs ? 'Hide log output' : 'Inspect log output' }}</span>
      </button>

      <!-- Collapsible Terminal Output -->
      <div v-if="showLogs" class="terminal-container">
        <div class="terminal-header">
          <span>overlay.log</span>
          <button type="button" class="terminal-refresh-btn" @click="emit('fetch-logs')">Refresh</button>
        </div>
        <pre class="terminal-body">{{ logContent || 'No log entries recorded.' }}</pre>
      </div>
    </section>

    <div class="section-title">Configuration profile</div>
    <section class="md3-card">
      <button type="button" class="action-btn-secondary" style="margin-top: 0;" @click="emit('reset-defaults')">
        <Icons name="refresh" :size="15" />
        <span>Reset to factory defaults</span>
      </button>
    </section>
  </div>
</template>

<script setup>
import Icons from '@/components/icons/Icons.vue'

defineProps({
  liveState: { type: Object, required: true },
  daemonPid: { type: [Number, String], default: 0 },
  showLogs: { type: Boolean, default: false },
  logContent: { type: String, default: '' }
})

const emit = defineEmits([
  'send-trigger',
  'toggle-reticle',
  'restart-daemon',
  'toggle-log-view',
  'fetch-logs',
  'reset-defaults'
])
</script>
