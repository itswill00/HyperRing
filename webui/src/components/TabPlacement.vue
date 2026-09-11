<template>
  <div>
    <!-- Live Cutout Simulator Preview -->
    <div class="section-title">Visual simulator</div>
    <div class="sim-bezel-box">
      <div class="sim-screen-boundary">
        <div
          v-if="previewMode === 'card'"
          class="sim-card"
          :style="{
            left: simPillLeft + '%',
            top: (simCutoutTop + Math.round(simCardHeight / 2) + 2) + 'px',
            width: simCardWidth + 'px',
            height: simCardHeight + 'px',
            borderRadius: simCardRadius + 'px'
          }"
        >
          <span style="font-size: 8px; color: var(--on-surface); opacity: 0.9; font-weight: 600;">{{ liveState.media_title || 'Now Playing' }}</span>
          <span style="font-size: 7px; color: var(--on-surface-variant);">{{ liveState.media_artist || 'Expanded Card' }}</span>
        </div>
        <div
          v-else
          class="sim-pill"
          :style="{
            left: simPillLeft + '%',
            top: simPillTop + 'px',
            width: simPillWidth + 'px',
            height: simPillHeight + 'px'
          }"
        >
          <span style="font-size: 8px; color: var(--on-surface-variant); opacity: 0.85;">{{ liveState.battery_pct || 100 }}%</span>
          <span style="font-size: 8px; color: var(--on-surface); opacity: 0.85;">Hyper</span>
        </div>
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

    <!-- High-contrast Alignment Reticle Toggle -->
    <div
      class="reticle-toggle-bar"
      :class="{ active: isReticleActive }"
      @click="$emit('toggle-reticle')"
    >
      <div style="display: flex; align-items: center; gap: 10px;">
        <Icons name="crosshair" :size="18" :style="{ color: isReticleActive ? 'var(--on-primary-container)' : 'var(--on-surface-variant)' }" />
        <div>
          <div class="row-title" :style="{ color: isReticleActive ? 'var(--on-primary-container)' : 'var(--on-surface)' }">
            {{ isReticleActive ? 'Alignment reticle active' : 'Show alignment reticle' }}
          </div>
          <div class="row-sub">
            {{ isReticleActive ? 'Transparent overlay active on screen. Tap to hide.' : 'High-contrast reticle to align with physical lens' }}
          </div>
        </div>
      </div>
      <span class="badge-pill" :class="isReticleActive ? 'active' : 'standby'">
        {{ isReticleActive ? 'On' : 'Off' }}
      </span>
    </div>

    <!-- Alignment Mode & Notch -->
    <div class="section-title">Punch hole alignment</div>
    <section class="md3-card">
      <div class="preset-row">
        <span class="row-meta-label">Alignment</span>
        <div class="segment-container">
          <button
            type="button"
            class="segment-btn"
            :class="{ active: config.pill_alignment === 'center' }"
            @click="$emit('set-alignment', 'center')"
          >
            Center
          </button>
          <button
            type="button"
            class="segment-btn"
            :class="{ active: config.pill_alignment === 'left' }"
            @click="$emit('set-alignment', 'left')"
          >
            Left
          </button>
          <button
            type="button"
            class="segment-btn"
            :class="{ active: config.pill_alignment === 'right' }"
            @click="$emit('set-alignment', 'right')"
          >
            Right
          </button>
          <button
            type="button"
            class="segment-btn"
            :class="{ active: config.pill_alignment === 'freeform' }"
            @click="$emit('set-alignment', 'freeform')"
          >
            Freeform
          </button>
        </div>
      </div>

      <div class="md3-list-row" style="padding: 10px 0 0 0; border-top: 1px solid var(--surface-container-high); margin-top: 8px;" @click="$emit('toggle-config', 'notch_mode')">
        <div class="row-left">
          <div class="row-meta">
            <div class="row-title">Attach to top bezel (Notch)</div>
            <div class="row-sub">Pins island flush against top screen frame</div>
          </div>
        </div>
        <label class="md3-switch" @click.stop>
          <input type="checkbox" v-model="config.notch_mode" @change="$emit('save-config')" />
          <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
        </label>
      </div>
    </section>

    <!-- Directional D-Pad Nudge Widget -->
    <div class="section-title">Directional nudge</div>
    <section class="dpad-card">
      <div style="font-size: 11px; color: var(--on-surface-variant); margin-bottom: 4px;">
        Micro-step camera placement
      </div>
      <div class="dpad-grid">
        <div></div>
        <button type="button" class="dpad-btn" @click="$emit('nudge', 0, -1)" title="Nudge Up">
          <Icons name="arrow-up" :size="16" />
        </button>
        <div></div>
        <button type="button" class="dpad-btn" @click="$emit('nudge', -1, 0)" title="Nudge Left">
          <Icons name="arrow-left" :size="16" />
        </button>
        <div class="dpad-center">{{ dpadStep }}px</div>
        <button type="button" class="dpad-btn" @click="$emit('nudge', 1, 0)" title="Nudge Right">
          <Icons name="arrow-right" :size="16" />
        </button>
        <div></div>
        <button type="button" class="dpad-btn" @click="$emit('nudge', 0, 1)" title="Nudge Down">
          <Icons name="arrow-down" :size="16" />
        </button>
        <div></div>
      </div>
      <div class="dpad-footer">
        <span style="font-size: 11px; color: var(--on-surface-variant);">Step increment</span>
        <div class="segment-container">
          <button
            v-for="s in [1, 5, 10]"
            :key="s"
            type="button"
            class="segment-btn"
            :class="{ active: dpadStep === s }"
            @click="$emit('update-dpad-step', s)"
          >
            {{ s }}px
          </button>
        </div>
      </div>
    </section>

    <!-- Precise Camera Cutout Coordinates -->
    <div class="section-title">Hardware cutout coordinates</div>
    <section class="md3-card">
      <div class="stepper-setting-block">
        <div class="stepper-header">
          <span class="stepper-title">Horizontal center (X)</span>
          <span class="stepper-val">{{ config.cutout_x }} px</span>
        </div>
        <div class="stepper-controls">
          <button type="button" class="step-btn" @click="$emit('step-value', 'cutout_x', -1, 0, 1440)">-</button>
          <input
            type="range"
            min="0"
            max="1440"
            step="1"
            v-model.number="config.cutout_x"
            @input="$emit('save-debounced')"
            class="slider-range"
          />
          <button type="button" class="step-btn" @click="$emit('step-value', 'cutout_x', 1, 0, 1440)">+</button>
        </div>
      </div>

      <div class="stepper-setting-block">
        <div class="stepper-header">
          <span class="stepper-title">Vertical center (Y)</span>
          <span class="stepper-val">{{ config.cutout_y }} px</span>
        </div>
        <div class="stepper-controls">
          <button type="button" class="step-btn" @click="$emit('step-value', 'cutout_y', -1, 0, 240)">-</button>
          <input
            type="range"
            min="0"
            max="240"
            step="1"
            v-model.number="config.cutout_y"
            @input="$emit('save-debounced')"
            class="slider-range"
          />
          <button type="button" class="step-btn" @click="$emit('step-value', 'cutout_y', 1, 0, 240)">+</button>
        </div>
      </div>

      <div class="stepper-setting-block" style="margin-bottom: 0;">
        <div class="stepper-header">
          <span class="stepper-title">Camera radius</span>
          <span class="stepper-val">{{ config.cutout_radius }} px</span>
        </div>
        <div class="stepper-controls">
          <button type="button" class="step-btn" @click="$emit('step-value', 'cutout_radius', -1, 12, 70)">-</button>
          <input
            type="range"
            min="12"
            max="70"
            step="1"
            v-model.number="config.cutout_radius"
            @input="$emit('save-debounced')"
            class="slider-range"
          />
          <button type="button" class="step-btn" @click="$emit('step-value', 'cutout_radius', 1, 12, 70)">+</button>
        </div>
      </div>
    </section>

    <!-- Island Micro Offsets -->
    <div class="section-title">Island position offsets</div>
    <section class="md3-card">
      <div class="stepper-setting-block">
        <div class="stepper-header">
          <span class="stepper-title">Vertical offset</span>
          <span class="stepper-val">{{ config.y_offset > 0 ? `+${config.y_offset}` : config.y_offset }} px</span>
        </div>
        <div class="stepper-controls">
          <button type="button" class="step-btn" @click="$emit('step-value', 'y_offset', -1, -150, 200)">-</button>
          <input
            type="range"
            min="-150"
            max="200"
            step="1"
            v-model.number="config.y_offset"
            @input="$emit('save-debounced')"
            class="slider-range"
          />
          <button type="button" class="step-btn" @click="$emit('step-value', 'y_offset', 1, -150, 200)">+</button>
        </div>
      </div>

      <div class="stepper-setting-block" style="margin-bottom: 0;">
        <div class="stepper-header">
          <span class="stepper-title">Horizontal offset</span>
          <span class="stepper-val">{{ config.x_offset > 0 ? `+${config.x_offset}` : config.x_offset }} px</span>
        </div>
        <div class="stepper-controls">
          <button type="button" class="step-btn" @click="$emit('step-value', 'x_offset', -1, -250, 250)">-</button>
          <input
            type="range"
            min="-250"
            max="250"
            step="1"
            v-model.number="config.x_offset"
            @input="$emit('save-debounced')"
            class="slider-range"
          />
          <button type="button" class="step-btn" @click="$emit('step-value', 'x_offset', 1, -250, 250)">+</button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import Icons from './icons/Icons.vue'

defineProps({
  config: Object,
  liveState: Object,
  previewMode: String,
  isReticleActive: Boolean,
  dpadStep: Number,
  simPillLeft: Number,
  simPillTop: Number,
  simPillWidth: Number,
  simPillHeight: Number,
  simCardWidth: Number,
  simCardHeight: Number,
  simCardRadius: Number,
  simCutoutLeft: Number,
  simCutoutTop: Number,
  simCutoutSize: Number
})

defineEmits([
  'toggle-reticle',
  'set-alignment',
  'toggle-config',
  'save-config',
  'save-debounced',
  'nudge',
  'update-dpad-step',
  'step-value'
])
</script>
