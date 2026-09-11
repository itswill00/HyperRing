<template>
  <section class="preview-bar-card">
    <div class="preview-bar-left">
      <span class="preview-bar-title">Live preview</span>
      <span class="preview-bar-sub">{{ previewModeLabel }}</span>
    </div>
    <div class="segment-container preview-segments">
      <button
        type="button"
        class="segment-btn"
        :class="{ active: previewMode === 'off' }"
        @click="$emit('set-preview-mode', 'off')"
      >
        Off
      </button>
      <button
        type="button"
        class="segment-btn"
        :class="{ active: previewMode === 'reticle' }"
        @click="$emit('set-preview-mode', 'reticle')"
      >
        Reticle
      </button>
      <button
        type="button"
        class="segment-btn"
        :class="{ active: previewMode === 'pill' }"
        @click="$emit('set-preview-mode', 'pill')"
      >
        Pill
      </button>
      <button
        type="button"
        class="segment-btn"
        :class="{ active: previewMode === 'card' }"
        @click="$emit('set-preview-mode', 'card')"
      >
        Card
      </button>
    </div>
  </section>
</template>

<script setup>
defineProps({
  previewMode: String,
  previewModeLabel: String
})

defineEmits(['set-preview-mode'])
</script>
