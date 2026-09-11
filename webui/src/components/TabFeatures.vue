<template>
  <div>
    <div class="section-title">Active listeners</div>
    <section class="md3-list-group">
      <!-- Media Session -->
      <div class="md3-list-row" @click="$emit('toggle-config', 'enable_media')">
        <div class="row-left">
          <div class="icon-badge secondary">
            <Icons name="music" :size="16" />
          </div>
          <div class="row-meta">
            <div class="row-title">Now playing</div>
            <div class="row-sub">Track title and audio spectrum</div>
          </div>
        </div>
        <label class="md3-switch" @click.stop>
          <input type="checkbox" v-model="config.enable_media" @change="$emit('save-config')" />
          <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
        </label>
      </div>

      <!-- Charging Telemetry -->
      <div class="md3-list-row" @click="$emit('toggle-config', 'enable_charging')">
        <div class="row-left">
          <div class="icon-badge secondary">
            <Icons name="bolt" :size="16" />
          </div>
          <div class="row-meta">
            <div class="row-title">Battery charging</div>
            <div class="row-sub">Wattage and power telemetry</div>
          </div>
        </div>
        <label class="md3-switch" @click.stop>
          <input type="checkbox" v-model="config.enable_charging" @change="$emit('save-config')" />
          <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
        </label>
      </div>

      <!-- Volume Key Expansion -->
      <div class="md3-list-row" @click="$emit('toggle-config', 'enable_volume')">
        <div class="row-left">
          <div class="icon-badge secondary">
            <Icons name="wave" :size="16" />
          </div>
          <div class="row-meta">
            <div class="row-title">Volume HUD</div>
            <div class="row-sub">Level indicator on rocker press</div>
          </div>
        </div>
        <label class="md3-switch" @click.stop>
          <input type="checkbox" v-model="config.enable_volume" @change="$emit('save-config')" />
          <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
        </label>
      </div>

      <!-- Ringer Mode -->
      <div class="md3-list-row" @click="$emit('toggle-config', 'enable_ringer')">
        <div class="row-left">
          <div class="icon-badge secondary">
            <Icons name="vibrate" :size="16" />
          </div>
          <div class="row-meta">
            <div class="row-title">Ringer mode</div>
            <div class="row-sub">Silent, vibrate, and ring pill</div>
          </div>
        </div>
        <label class="md3-switch" @click.stop>
          <input type="checkbox" v-model="config.enable_ringer" @change="$emit('save-config')" />
          <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
        </label>
      </div>

      <!-- Notifications -->
      <div class="md3-list-row" @click="$emit('toggle-config', 'enable_notifications')">
        <div class="row-left">
          <div class="icon-badge secondary">
            <Icons name="bell" :size="16" />
          </div>
          <div class="row-meta">
            <div class="row-title">Notifications</div>
            <div class="row-sub">Heads-up app alerts</div>
          </div>
        </div>
        <label class="md3-switch" @click.stop>
          <input type="checkbox" v-model="config.enable_notifications" @change="$emit('save-config')" />
          <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
        </label>
      </div>

      <!-- Universal Downloads & Progress -->
      <div class="md3-list-row" @click="$emit('toggle-config', 'enable_progress')">
        <div class="row-left">
          <div class="icon-badge secondary">
            <Icons name="download" :size="16" />
          </div>
          <div class="row-meta">
            <div class="row-title">Downloads & live progress</div>
            <div class="row-sub">Chrome, Play Store, browsers & ongoing tasks</div>
          </div>
        </div>
        <label class="md3-switch" @click.stop>
          <input type="checkbox" v-model="config.enable_progress" @change="$emit('save-config')" />
          <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
        </label>
      </div>

      <!-- Flashlight / Torch -->
      <div class="md3-list-row" @click="$emit('toggle-config', 'enable_torch')">
        <div class="row-left">
          <div class="icon-badge secondary">
            <Icons name="power" :size="16" />
          </div>
          <div class="row-meta">
            <div class="row-title">Flashlight and torch</div>
            <div class="row-sub">Indicator on torch state change</div>
          </div>
        </div>
        <label class="md3-switch" @click.stop>
          <input type="checkbox" v-model="config.enable_torch" @change="$emit('save-config')" />
          <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
        </label>
      </div>

      <!-- Haptics Toggle -->
      <div class="md3-list-row" style="padding: 12px 0;" @click="$emit('toggle-config', 'enable_haptics')">
        <div class="row-left">
          <div class="icon-badge secondary">
            <Icons name="sparkles" :size="16" />
          </div>
          <div class="row-meta">
            <div class="row-title">Haptic micro-feedback</div>
            <div class="row-sub">Tactile clicks and ticks on gestures and taps</div>
          </div>
        </div>
        <label class="md3-switch" @click.stop>
          <input type="checkbox" v-model="config.enable_haptics" @change="$emit('save-config')" />
          <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
        </label>
      </div>
    </section>

    <!-- HyperOS Media Experience -->
    <div class="section-title">Media & playback</div>
    <section class="md3-card" style="margin-bottom: 24px;">
      <!-- Compact Album Art Toggle -->
      <div class="md3-list-row" style="padding: 12px 0;" @click="$emit('toggle-config', 'media_show_pill_art')">
        <div class="row-left">
          <div class="icon-badge secondary">
            <Icons name="image" :size="16" />
          </div>
          <div class="row-meta">
            <div class="row-title">Show album art in compact pill</div>
            <div class="row-sub">Cover art thumbnail on the left wing</div>
          </div>
        </div>
        <label class="md3-switch" @click.stop>
          <input type="checkbox" v-model="config.media_show_pill_art" @change="$emit('save-config')" />
          <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
        </label>
      </div>

      <!-- Pill Artwork Style Segmented Selector -->
      <div class="preset-row" v-if="config.media_show_pill_art" style="margin-top: 6px; padding-top: 12px; border-top: 1px solid var(--surface-container-high);">
        <span class="row-meta-label">Pill artwork style</span>
        <div class="segment-container">
          <button
            type="button"
            class="segment-btn"
            :class="{ active: config.media_art_style === 'rounded' }"
            @click="config.media_art_style = 'rounded'; $emit('save-config');"
          >
            Rounded
          </button>
          <button
            type="button"
            class="segment-btn"
            :class="{ active: config.media_art_style === 'squircle' }"
            @click="config.media_art_style = 'squircle'; $emit('save-config');"
          >
            Squircle
          </button>
          <button
            type="button"
            class="segment-btn"
            :class="{ active: config.media_art_style === 'circle' }"
            @click="config.media_art_style = 'circle'; $emit('save-config');"
          >
            Circle
          </button>
        </div>
      </div>

      <!-- Waveform Visualizer Toggle -->
      <div class="md3-list-row" style="padding: 12px 0; border-top: 1px solid var(--surface-container-high);" @click="$emit('toggle-config', 'media_show_waveform')">
        <div class="row-left">
          <div class="icon-badge secondary">
            <Icons name="wave" :size="16" />
          </div>
          <div class="row-meta">
            <div class="row-title">Animated audio spectrum / waveform</div>
            <div class="row-sub">Dynamic frequency equalizer bars</div>
          </div>
        </div>
        <label class="md3-switch" @click.stop>
          <input type="checkbox" v-model="config.media_show_waveform" @change="$emit('save-config')" />
          <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
        </label>
      </div>

      <!-- Audio Pulse Monochrome Presets -->
      <div class="preset-row" v-if="config.media_show_waveform" style="margin-top: 6px; padding-top: 12px; border-top: 1px solid var(--surface-container-high);">
        <span class="row-meta-label">Pulse accent</span>
        <div class="segment-container" style="gap: 4px;">
          <button
            type="button"
            class="segment-btn"
            :class="{ active: config.media_pulse_color === 'auto' || !config.media_pulse_color }"
            @click="config.media_pulse_color = 'auto'; $emit('save-config');"
          >
            Dynamic (Auto)
          </button>
          <button
            type="button"
            class="segment-btn"
            :class="{ active: config.media_pulse_color === '#FFFFFF' }"
            @click="config.media_pulse_color = '#FFFFFF'; $emit('save-config');"
          >
            Pure White
          </button>
          <button
            type="button"
            class="segment-btn"
            :class="{ active: config.media_pulse_color === '#A1A1AA' }"
            @click="config.media_pulse_color = '#A1A1AA'; $emit('save-config');"
          >
            Muted Zinc
          </button>
        </div>
      </div>

      <!-- Dynamic Ambient Glow Toggle -->
      <div class="md3-list-row" style="padding: 12px 0; border-top: 1px solid var(--surface-container-high);" @click="$emit('toggle-config', 'media_ambient_glow')">
        <div class="row-left">
          <div class="icon-badge secondary">
            <Icons name="eye" :size="16" />
          </div>
          <div class="row-meta">
            <div class="row-title">Dynamic ambient color glow</div>
            <div class="row-sub">Radial gradient tinted by album art</div>
          </div>
        </div>
        <label class="md3-switch" @click.stop>
          <input type="checkbox" v-model="config.media_ambient_glow" @change="$emit('save-config')" />
          <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
        </label>
      </div>

      <!-- Ambient Glow Opacity Slider -->
      <div class="stepper-setting-block" v-if="config.media_ambient_glow" style="margin-top: 6px; padding-top: 12px; border-top: 1px solid var(--surface-container-high);">
        <div class="stepper-header">
          <span class="stepper-title">Ambient glow opacity</span>
          <span class="stepper-val">{{ config.media_glow_opacity }}%</span>
        </div>
        <div class="stepper-controls">
          <button type="button" class="step-btn" @click="$emit('step-value', 'media_glow_opacity', -5, 0, 50)">-</button>
          <input
            type="range"
            min="0"
            max="50"
            step="5"
            v-model.number="config.media_glow_opacity"
            @input="$emit('save-debounced')"
            class="slider-range"
          />
          <button type="button" class="step-btn" @click="$emit('step-value', 'media_glow_opacity', 5, 0, 50)">+</button>
        </div>
      </div>

      <!-- Marquee Scrolling Toggle -->
      <div class="md3-list-row" style="padding: 12px 0; margin-bottom: 0; border-top: 1px solid var(--surface-container-high);" @click="$emit('toggle-config', 'media_marquee')">
        <div class="row-left">
          <div class="icon-badge secondary">
            <Icons name="sliders" :size="16" />
          </div>
          <div class="row-meta">
            <div class="row-title">Marquee scrolling for long titles</div>
            <div class="row-sub">Smooth text marquee for long song names</div>
          </div>
        </div>
        <label class="md3-switch" @click.stop>
          <input type="checkbox" v-model="config.media_marquee" @change="$emit('save-config')" />
          <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
        </label>
      </div>
    </section>

    <div class="section-title">Automations</div>
    <section class="md3-list-group">
      <!-- Auto-expand charging -->
      <div class="md3-list-row" @click="$emit('toggle-config', 'auto_expand_charging')">
        <div class="row-left">
          <div class="icon-badge secondary">
            <Icons name="maximize" :size="16" />
          </div>
          <div class="row-meta">
            <div class="row-title">Expand on charging</div>
            <div class="row-sub">Open power card on charger connection</div>
          </div>
        </div>
        <label class="md3-switch" @click.stop>
          <input type="checkbox" v-model="config.auto_expand_charging" @change="$emit('save-config')" />
          <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
        </label>
      </div>

      <!-- Auto-expand media -->
      <div class="md3-list-row" @click="$emit('toggle-config', 'auto_expand_media')">
        <div class="row-left">
          <div class="icon-badge secondary">
            <Icons name="play" :size="16" />
          </div>
          <div class="row-meta">
            <div class="row-title">Expand on track change</div>
            <div class="row-sub">Briefly show track controls on new song</div>
          </div>
        </div>
        <label class="md3-switch" @click.stop>
          <input type="checkbox" v-model="config.auto_expand_media" @change="$emit('save-config')" />
          <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
        </label>
      </div>

      <!-- Auto-expand notification -->
      <div class="md3-list-row" @click="$emit('toggle-config', 'auto_expand_notification')">
        <div class="row-left">
          <div class="icon-badge secondary">
            <Icons name="lens" :size="16" />
          </div>
          <div class="row-meta">
            <div class="row-title">Expand on notification</div>
            <div class="row-sub">Briefly show banner details on new alert</div>
          </div>
        </div>
        <label class="md3-switch" @click.stop>
          <input type="checkbox" v-model="config.auto_expand_notification" @change="$emit('save-config')" />
          <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
        </label>
      </div>

      <!-- Auto-expand download/progress -->
      <div class="md3-list-row" @click="$emit('toggle-config', 'auto_expand_progress')">
        <div class="row-left">
          <div class="icon-badge secondary">
            <Icons name="download" :size="16" />
          </div>
          <div class="row-meta">
            <div class="row-title">Expand on download</div>
            <div class="row-sub">Open live progress card when download begins</div>
          </div>
        </div>
        <label class="md3-switch" @click.stop>
          <input type="checkbox" v-model="config.auto_expand_progress" @change="$emit('save-config')" />
          <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
        </label>
      </div>

      <!-- Stealth Ring in Idle -->
      <div class="md3-list-row" @click="$emit('toggle-config', 'stealth_ring_idle')">
        <div class="row-left">
          <div class="icon-badge secondary">
            <Icons name="eye-off" :size="16" />
          </div>
          <div class="row-meta">
            <div class="row-title">Stealth idle</div>
            <div class="row-sub">Hide ring when screen is inactive</div>
          </div>
        </div>
        <label class="md3-switch" @click.stop>
          <input type="checkbox" v-model="config.stealth_ring_idle" @change="$emit('save-config')" />
          <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
        </label>
      </div>

      <!-- Landscape Guard -->
      <div class="md3-list-row" @click="$emit('toggle-config', 'hide_in_landscape')">
        <div class="row-left">
          <div class="icon-badge secondary">
            <Icons name="shield" :size="16" />
          </div>
          <div class="row-meta">
            <div class="row-title">Landscape guard</div>
            <div class="row-sub">Disable overlay in games and movies</div>
          </div>
        </div>
        <label class="md3-switch" @click.stop>
          <input type="checkbox" v-model="config.hide_in_landscape" @change="$emit('save-config')" />
          <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
        </label>
      </div>
    </section>
  </div>
</template>

<script setup>
import Icons from './icons/Icons.vue'

defineProps({
  config: Object
})

defineEmits([
  'toggle-config',
  'save-config',
  'save-debounced',
  'step-value'
])
</script>
