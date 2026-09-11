<template>
  <div>
    <div class="section-title">Spring dynamics</div>
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
          <button
            type="button"
            class="segment-btn"
            :class="{ active: currentMotionProfile === 'float' }"
            @click="applyMotionPreset('float')"
          >
            Float
          </button>
        </div>
      </div>

      <!-- Stiffness -->
      <div class="stepper-setting-block">
        <div class="stepper-header">
          <span class="stepper-title">Spring stiffness</span>
          <span class="stepper-val">{{ config.spring_stiffness }}</span>
        </div>
        <div class="stepper-controls">
          <button type="button" class="step-btn" @click="stepValue('spring_stiffness', -20, 150, 650)">-</button>
          <input
            type="range"
            min="150"
            max="650"
            step="10"
            v-model.number="config.spring_stiffness"
            @input="$emit('save-debounced')"
            class="slider-range"
          />
          <button type="button" class="step-btn" @click="stepValue('spring_stiffness', 20, 150, 650)">+</button>
        </div>
      </div>

      <!-- Damping -->
      <div class="stepper-setting-block">
        <div class="stepper-header">
          <span class="stepper-title">Damping ratio</span>
          <span class="stepper-val">{{ config.spring_damping }}</span>
        </div>
        <div class="stepper-controls">
          <button type="button" class="step-btn" @click="stepValue('spring_damping', -0.02, 0.55, 0.96)">-</button>
          <input
            type="range"
            min="0.55"
            max="0.96"
            step="0.02"
            v-model.number="config.spring_damping"
            @input="$emit('save-debounced')"
            class="slider-range"
          />
          <button type="button" class="step-btn" @click="stepValue('spring_damping', 0.02, 0.55, 0.96)">+</button>
        </div>
      </div>

      <!-- Expand Timeout -->
      <div class="stepper-setting-block" style="margin-bottom: 0;">
        <div class="stepper-header">
          <span class="stepper-title">Card auto-collapse duration</span>
          <span class="stepper-val">{{ config.expand_timeout_ms }}ms</span>
        </div>
        <div class="stepper-controls">
          <button type="button" class="step-btn" @click="stepValue('expand_timeout_ms', -250, 1500, 8000)">-</button>
          <input
            type="range"
            min="1500"
            max="8000"
            step="250"
            v-model.number="config.expand_timeout_ms"
            @input="$emit('save-debounced')"
            class="slider-range"
          />
          <button type="button" class="step-btn" @click="stepValue('expand_timeout_ms', 250, 1500, 8000)">+</button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
const props = defineProps({
  config: { type: Object, required: true },
  currentMotionProfile: { type: String, default: 'fluid' }
})

const emit = defineEmits(['save-debounced', 'apply-motion-preset'])

function applyMotionPreset(preset) {
  emit('apply-motion-preset', preset)
}

function stepValue(key, delta, min, max) {
  const current = Number(props.config[key]) || 0
  let next = current + delta
  if (min !== undefined) next = Math.max(min, next)
  if (max !== undefined) next = Math.min(max, next)
  if (typeof delta === 'number' && delta % 1 !== 0) {
    next = parseFloat(next.toFixed(2))
  }
  props.config[key] = next
  emit('save-debounced')
}
</script>
