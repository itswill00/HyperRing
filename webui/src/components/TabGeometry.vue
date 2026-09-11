<template>
  <div>
    <div class="section-title">Compact pill dimensions</div>
    <section class="md3-card">
      <div class="stepper-setting-block">
        <div class="stepper-header">
          <span class="stepper-title">Pill width</span>
          <span class="stepper-val">{{ config.pill_width === 0 ? 'Auto' : `${config.pill_width} dp` }}</span>
        </div>
        <div class="stepper-controls">
          <button type="button" class="step-btn" @click="$emit('step-pill-width', -5)">-</button>
          <input
            type="range"
            min="0"
            max="340"
            step="5"
            v-model.number="config.pill_width"
            @input="$emit('save-debounced')"
            class="slider-range"
          />
          <button type="button" class="step-btn" @click="$emit('step-pill-width', 5)">+</button>
        </div>
      </div>

      <div class="stepper-setting-block" style="margin-bottom: 0;">
        <div class="stepper-header">
          <span class="stepper-title">Pill height</span>
          <span class="stepper-val">{{ config.pill_height === 0 ? 'Auto' : `${config.pill_height} dp` }}</span>
        </div>
        <div class="stepper-controls">
          <button type="button" class="step-btn" @click="$emit('step-pill-height', -2)">-</button>
          <input
            type="range"
            min="0"
            max="60"
            step="2"
            v-model.number="config.pill_height"
            @input="$emit('save-debounced')"
            class="slider-range"
          />
          <button type="button" class="step-btn" @click="$emit('step-pill-height', 2)">+</button>
        </div>
      </div>
    </section>

    <div class="section-title">Expanded card dimensions</div>
    <section class="md3-card">
      <div class="stepper-setting-block">
        <div class="stepper-header">
          <span class="stepper-title">Card width</span>
          <span class="stepper-val">{{ config.card_width === 0 ? 'Auto (320 dp)' : `${config.card_width} dp` }}</span>
        </div>
        <div class="stepper-controls">
          <button type="button" class="step-btn" @click="$emit('step-card-width', -10)">-</button>
          <input
            type="range"
            min="0"
            max="420"
            step="10"
            v-model.number="config.card_width"
            @input="$emit('save-debounced')"
            class="slider-range"
          />
          <button type="button" class="step-btn" @click="$emit('step-card-width', 10)">+</button>
        </div>
      </div>

      <div class="stepper-setting-block">
        <div class="stepper-header">
          <span class="stepper-title">Card height</span>
          <span class="stepper-val">{{ config.card_height === 0 ? 'Auto' : `${config.card_height} dp` }}</span>
        </div>
        <div class="stepper-controls">
          <button type="button" class="step-btn" @click="$emit('step-card-height', -5)">-</button>
          <input
            type="range"
            min="0"
            max="220"
            step="5"
            v-model.number="config.card_height"
            @input="$emit('save-debounced')"
            class="slider-range"
          />
          <button type="button" class="step-btn" @click="$emit('step-card-height', 5)">+</button>
        </div>
      </div>

      <div class="stepper-setting-block">
        <div class="stepper-header">
          <span class="stepper-title">Top margin</span>
          <span class="stepper-val">{{ config.card_y_offset > 0 ? `+${config.card_y_offset}` : config.card_y_offset }} dp</span>
        </div>
        <div class="stepper-controls">
          <button type="button" class="step-btn" @click="$emit('step-value', 'card_y_offset', -2, -40, 60)">-</button>
          <input
            type="range"
            min="-40"
            max="60"
            step="2"
            v-model.number="config.card_y_offset"
            @input="$emit('save-debounced')"
            class="slider-range"
          />
          <button type="button" class="step-btn" @click="$emit('step-value', 'card_y_offset', 2, -40, 60)">+</button>
        </div>
      </div>

      <div class="stepper-setting-block">
        <div class="stepper-header">
          <span class="stepper-title">Corner radius</span>
          <span class="stepper-val">{{ config.card_radius }} dp</span>
        </div>
        <div class="stepper-controls">
          <button type="button" class="step-btn" @click="$emit('step-value', 'card_radius', -2, 14, 36)">-</button>
          <input
            type="range"
            min="14"
            max="36"
            step="2"
            v-model.number="config.card_radius"
            @input="$emit('save-debounced')"
            class="slider-range"
          />
          <button type="button" class="step-btn" @click="$emit('step-value', 'card_radius', 2, 14, 36)">+</button>
        </div>
      </div>

      <div class="preset-row" style="margin-top: 10px; margin-bottom: 2px;">
        <span class="row-meta-label">Placement mode</span>
        <div class="segment-container">
          <button
            type="button"
            class="segment-btn"
            :class="{ active: (config.card_position_mode || 'below') === 'below' }"
            @click="$emit('set-card-mode', 'below')"
          >
            Float below
          </button>
          <button
            type="button"
            class="segment-btn"
            :class="{ active: config.card_position_mode === 'cover' }"
            @click="$emit('set-card-mode', 'cover')"
          >
            Cover notch
          </button>
        </div>
      </div>
    </section>

    <div class="section-title">Card behavior</div>
    <section class="md3-card">
      <div class="stepper-setting-block" style="margin-bottom: 0;">
        <div class="stepper-header">
          <span class="stepper-title">Auto-collapse timeout</span>
          <span class="stepper-val">{{ (config.expand_timeout_ms / 1000).toFixed(1) }} s</span>
        </div>
        <div class="stepper-controls">
          <button type="button" class="step-btn" @click="$emit('step-value', 'expand_timeout_ms', -500, 1500, 8000)">-</button>
          <input
            type="range"
            min="1500"
            max="8000"
            step="500"
            v-model.number="config.expand_timeout_ms"
            @input="$emit('save-debounced')"
            class="slider-range"
          />
          <button type="button" class="step-btn" @click="$emit('step-value', 'expand_timeout_ms', 500, 1500, 8000)">+</button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
defineProps({
  config: Object
})

defineEmits([
  'step-pill-width',
  'step-pill-height',
  'step-card-width',
  'step-card-height',
  'step-value',
  'set-card-mode',
  'save-debounced'
])
</script>
