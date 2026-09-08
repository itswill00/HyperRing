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
          style="border-color: #00e5ff; color: #00e5ff; cursor: pointer;"
          @click="toggleReticle"
        >
          Reticle active
        </button>
        <span class="badge-pill" :class="isDaemonAlive ? 'active' : 'standby'">
          {{ isDaemonAlive ? (daemonPid ? `PID ${daemonPid}` : 'Active') : 'Standby' }}
        </span>
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

    <!-- Main Scrollable Content -->
    <main class="content-area">

      <!-- Persistent Live Preview Widget -->
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
            @click="setPreviewMode('off')"
          >
            Off
          </button>
          <button
            type="button"
            class="segment-btn"
            :class="{ active: previewMode === 'reticle' }"
            @click="setPreviewMode('reticle')"
          >
            Reticle
          </button>
          <button
            type="button"
            class="segment-btn"
            :class="{ active: previewMode === 'pill' }"
            @click="setPreviewMode('pill')"
          >
            Pill
          </button>
          <button
            type="button"
            class="segment-btn"
            :class="{ active: previewMode === 'card' }"
            @click="setPreviewMode('card')"
          >
            Card
          </button>
        </div>
      </section>

      <!-- TAB 1: PLACEMENT & ALIGNMENT -->
      <div v-if="currentTab === 'placement'">
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

        <!-- High-contrast Alignment Reticle Toggle -->
        <div
          class="reticle-toggle-bar"
          :class="{ active: isReticleActive }"
          @click="toggleReticle"
        >
          <div style="display: flex; align-items: center; gap: 10px;">
            <Icons name="crosshair" :size="18" :style="{ color: isReticleActive ? '#00e5ff' : 'var(--on-surface-variant)' }" />
            <div>
              <div class="row-title" :style="{ color: isReticleActive ? '#00e5ff' : 'var(--on-surface)' }">
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
                @click="setAlignment('center')"
              >
                Center
              </button>
              <button
                type="button"
                class="segment-btn"
                :class="{ active: config.pill_alignment === 'left' }"
                @click="setAlignment('left')"
              >
                Left
              </button>
              <button
                type="button"
                class="segment-btn"
                :class="{ active: config.pill_alignment === 'right' }"
                @click="setAlignment('right')"
              >
                Right
              </button>
              <button
                type="button"
                class="segment-btn"
                :class="{ active: config.pill_alignment === 'freeform' }"
                @click="setAlignment('freeform')"
              >
                Freeform
              </button>
            </div>
          </div>

          <!-- Notch Mode Toggle -->
          <div class="md3-list-row" style="padding: 10px 0 0 0; border-top: 1px solid var(--surface-container-high); margin-top: 8px;" @click="toggleConfig('notch_mode')">
            <div class="row-left">
              <div class="row-meta">
                <div class="row-title">Attach to top bezel (Notch)</div>
                <div class="row-sub">Pins island flush against top screen frame</div>
              </div>
            </div>
            <label class="md3-switch" @click.stop>
              <input type="checkbox" v-model="config.notch_mode" @change="saveConfig" />
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
            <button type="button" class="dpad-btn" @click="nudge(0, -1)" title="Nudge Up">
              <Icons name="arrow-up" :size="16" />
            </button>
            <div></div>
            <button type="button" class="dpad-btn" @click="nudge(-1, 0)" title="Nudge Left">
              <Icons name="arrow-left" :size="16" />
            </button>
            <div class="dpad-center">
              {{ dpadStep }}px
            </div>
            <button type="button" class="dpad-btn" @click="nudge(1, 0)" title="Nudge Right">
              <Icons name="arrow-right" :size="16" />
            </button>
            <div></div>
            <button type="button" class="dpad-btn" @click="nudge(0, 1)" title="Nudge Down">
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
                @click="dpadStep = s"
              >
                {{ s }}px
              </button>
            </div>
          </div>
        </section>

        <!-- Precise Camera Cutout Coordinates -->
        <div class="section-title">Hardware cutout coordinates</div>
        <section class="md3-card">
          <!-- Horizontal Center X -->
          <div class="stepper-setting-block">
            <div class="stepper-header">
              <span class="stepper-title">Horizontal center (X)</span>
              <span class="stepper-val">{{ config.cutout_x }} px</span>
            </div>
            <div class="stepper-controls">
              <button type="button" class="step-btn" @click="stepValue('cutout_x', -1, 0, 1440)">-</button>
              <input
                type="range"
                min="0"
                max="1440"
                step="1"
                v-model.number="config.cutout_x"
                @input="saveConfigDebounced"
                class="slider-range"
              />
              <button type="button" class="step-btn" @click="stepValue('cutout_x', 1, 0, 1440)">+</button>
            </div>
          </div>

          <!-- Vertical Center Y -->
          <div class="stepper-setting-block">
            <div class="stepper-header">
              <span class="stepper-title">Vertical center (Y)</span>
              <span class="stepper-val">{{ config.cutout_y }} px</span>
            </div>
            <div class="stepper-controls">
              <button type="button" class="step-btn" @click="stepValue('cutout_y', -1, 0, 240)">-</button>
              <input
                type="range"
                min="0"
                max="240"
                step="1"
                v-model.number="config.cutout_y"
                @input="saveConfigDebounced"
                class="slider-range"
              />
              <button type="button" class="step-btn" @click="stepValue('cutout_y', 1, 0, 240)">+</button>
            </div>
          </div>

          <!-- Cutout Radius -->
          <div class="stepper-setting-block" style="margin-bottom: 0;">
            <div class="stepper-header">
              <span class="stepper-title">Camera radius</span>
              <span class="stepper-val">{{ config.cutout_radius }} px</span>
            </div>
            <div class="stepper-controls">
              <button type="button" class="step-btn" @click="stepValue('cutout_radius', -1, 12, 70)">-</button>
              <input
                type="range"
                min="12"
                max="70"
                step="1"
                v-model.number="config.cutout_radius"
                @input="saveConfigDebounced"
                class="slider-range"
              />
              <button type="button" class="step-btn" @click="stepValue('cutout_radius', 1, 12, 70)">+</button>
            </div>
          </div>
        </section>

        <!-- Island Micro Offsets -->
        <div class="section-title">Island position offsets</div>
        <section class="md3-card">
          <!-- Vertical Nudge Y -->
          <div class="stepper-setting-block">
            <div class="stepper-header">
              <span class="stepper-title">Vertical offset</span>
              <span class="stepper-val">{{ config.y_offset > 0 ? `+${config.y_offset}` : config.y_offset }} px</span>
            </div>
            <div class="stepper-controls">
              <button type="button" class="step-btn" @click="stepValue('y_offset', -1, -150, 200)">-</button>
              <input
                type="range"
                min="-150"
                max="200"
                step="1"
                v-model.number="config.y_offset"
                @input="saveConfigDebounced"
                class="slider-range"
              />
              <button type="button" class="step-btn" @click="stepValue('y_offset', 1, -150, 200)">+</button>
            </div>
          </div>

          <!-- Horizontal Nudge X -->
          <div class="stepper-setting-block" style="margin-bottom: 0;">
            <div class="stepper-header">
              <span class="stepper-title">Horizontal offset</span>
              <span class="stepper-val">{{ config.x_offset > 0 ? `+${config.x_offset}` : config.x_offset }} px</span>
            </div>
            <div class="stepper-controls">
              <button type="button" class="step-btn" @click="stepValue('x_offset', -1, -250, 250)">-</button>
              <input
                type="range"
                min="-250"
                max="250"
                step="1"
                v-model.number="config.x_offset"
                @input="saveConfigDebounced"
                class="slider-range"
              />
              <button type="button" class="step-btn" @click="stepValue('x_offset', 1, -250, 250)">+</button>
            </div>
          </div>
        </section>
      </div>

      <!-- TAB 2: GEOMETRY & SIZING -->
      <div v-else-if="currentTab === 'geometry'">
        <div class="section-title">Compact pill dimensions</div>
        <section class="md3-card">
          <!-- Custom Pill Width -->
          <div class="stepper-setting-block">
            <div class="stepper-header">
              <span class="stepper-title">Pill width</span>
              <span class="stepper-val">{{ config.pill_width === 0 ? 'Auto' : `${config.pill_width} dp` }}</span>
            </div>
            <div class="stepper-controls">
              <button type="button" class="step-btn" @click="stepPillWidth(-5)">-</button>
              <input
                type="range"
                min="0"
                max="340"
                step="5"
                v-model.number="config.pill_width"
                @input="saveConfigDebounced"
                class="slider-range"
              />
              <button type="button" class="step-btn" @click="stepPillWidth(5)">+</button>
            </div>
          </div>

          <!-- Custom Pill Height -->
          <div class="stepper-setting-block" style="margin-bottom: 0;">
            <div class="stepper-header">
              <span class="stepper-title">Pill height</span>
              <span class="stepper-val">{{ config.pill_height === 0 ? 'Auto' : `${config.pill_height} dp` }}</span>
            </div>
            <div class="stepper-controls">
              <button type="button" class="step-btn" @click="stepPillHeight(-2)">-</button>
              <input
                type="range"
                min="0"
                max="60"
                step="2"
                v-model.number="config.pill_height"
                @input="saveConfigDebounced"
                class="slider-range"
              />
              <button type="button" class="step-btn" @click="stepPillHeight(2)">+</button>
            </div>
          </div>
        </section>

        <div class="section-title">Expanded card dimensions</div>
        <section class="md3-card">
          <!-- Card Width -->
          <div class="stepper-setting-block">
            <div class="stepper-header">
              <span class="stepper-title">Card width</span>
              <span class="stepper-val">{{ config.card_width === 0 ? 'Auto (320 dp)' : `${config.card_width} dp` }}</span>
            </div>
            <div class="stepper-controls">
              <button type="button" class="step-btn" @click="stepCardWidth(-10)">-</button>
              <input
                type="range"
                min="0"
                max="420"
                step="10"
                v-model.number="config.card_width"
                @input="saveConfigDebounced"
                class="slider-range"
              />
              <button type="button" class="step-btn" @click="stepCardWidth(10)">+</button>
            </div>
          </div>

          <!-- Card Corner Radius -->
          <div class="stepper-setting-block" style="margin-bottom: 0;">
            <div class="stepper-header">
              <span class="stepper-title">Corner radius</span>
              <span class="stepper-val">{{ config.card_radius }} dp</span>
            </div>
            <div class="stepper-controls">
              <button type="button" class="step-btn" @click="stepValue('card_radius', -2, 14, 36)">-</button>
              <input
                type="range"
                min="14"
                max="36"
                step="2"
                v-model.number="config.card_radius"
                @input="saveConfigDebounced"
                class="slider-range"
              />
              <button type="button" class="step-btn" @click="stepValue('card_radius', 2, 14, 36)">+</button>
            </div>
          </div>
        </section>

        <div class="section-title">Card behavior</div>
        <section class="md3-card">
          <!-- Expand Timeout -->
          <div class="stepper-setting-block" style="margin-bottom: 0;">
            <div class="stepper-header">
              <span class="stepper-title">Auto-collapse timeout</span>
              <span class="stepper-val">{{ (config.expand_timeout_ms / 1000).toFixed(1) }} s</span>
            </div>
            <div class="stepper-controls">
              <button type="button" class="step-btn" @click="stepValue('expand_timeout_ms', -500, 1500, 8000)">-</button>
              <input
                type="range"
                min="1500"
                max="8000"
                step="500"
                v-model.number="config.expand_timeout_ms"
                @input="saveConfigDebounced"
                class="slider-range"
              />
              <button type="button" class="step-btn" @click="stepValue('expand_timeout_ms', 500, 1500, 8000)">+</button>
            </div>
          </div>
        </section>
      </div>

      <!-- TAB 3: EVENTS & LISTENERS -->
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
                <div class="row-sub">Track title and audio spectrum</div>
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
                <div class="row-sub">Wattage and power telemetry</div>
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
                <div class="row-sub">Level indicator on rocker press</div>
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
                <div class="row-sub">Silent, vibrate, and ring pill</div>
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
                <div class="row-sub">Heads-up app alerts</div>
              </div>
            </div>
            <label class="md3-switch" @click.stop>
              <input type="checkbox" v-model="config.enable_notifications" @change="saveConfig" />
              <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
            </label>
          </div>

          <!-- Flashlight / Torch -->
          <div class="md3-list-row" @click="toggleConfig('enable_torch')">
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
              <input type="checkbox" v-model="config.enable_torch" @change="saveConfig" />
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
                <div class="row-sub">HyperDL background transfer rate</div>
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
                <div class="row-sub">Kernel profile on expanded card</div>
              </div>
            </div>
            <label class="md3-switch" @click.stop>
              <input type="checkbox" v-model="config.enable_hypercore" @change="saveConfig" />
              <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
            </label>
          </div>
        </section>

        <div class="section-title">Automations</div>
        <section class="md3-list-group">
          <!-- Auto-expand charging -->
          <div class="md3-list-row" @click="toggleConfig('auto_expand_charging')">
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
              <input type="checkbox" v-model="config.auto_expand_charging" @change="saveConfig" />
              <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
            </label>
          </div>

          <!-- Auto-expand media -->
          <div class="md3-list-row" @click="toggleConfig('auto_expand_media')">
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
              <input type="checkbox" v-model="config.auto_expand_media" @change="saveConfig" />
              <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
            </label>
          </div>

          <!-- Stealth Ring in Idle -->
          <div class="md3-list-row" @click="toggleConfig('stealth_ring_idle')">
            <div class="row-left">
              <div class="icon-badge secondary">
                <Icons name="circle" :size="16" />
              </div>
              <div class="row-meta">
                <div class="row-title">Stealth idle</div>
                <div class="row-sub">Hide ring when screen is inactive</div>
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
                <div class="row-sub">Disable overlay in games and movies</div>
              </div>
            </div>
            <label class="md3-switch" @click.stop>
              <input type="checkbox" v-model="config.hide_in_landscape" @change="saveConfig" />
              <span class="md3-switch-track"><span class="md3-switch-thumb"></span></span>
            </label>
          </div>
        </section>
      </div>

      <!-- TAB 4: MOTION PHYSICS -->
      <div v-else-if="currentTab === 'motion'">
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
                @input="saveConfigDebounced"
                class="slider-range"
              />
              <button type="button" class="step-btn" @click="stepValue('spring_stiffness', 20, 150, 650)">+</button>
            </div>
          </div>

          <!-- Damping -->
          <div class="stepper-setting-block" style="margin-bottom: 0;">
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
                @input="saveConfigDebounced"
                class="slider-range"
              />
              <button type="button" class="step-btn" @click="stepValue('spring_damping', 0.02, 0.55, 0.96)">+</button>
            </div>
          </div>
        </section>
      </div>

      <!-- TAB 5: TOOLS & DIAGNOSTICS -->
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
              <span>Notif</span>
            </button>
            <button type="button" class="sim-chip" @click="sendTrigger('torch')">
              <Icons name="power" :size="14" />
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
          <div style="display: flex; gap: 8px; margin-top: 10px;">
            <button type="button" class="action-btn-secondary" style="margin-top: 0; flex: 1;" @click="sendTrigger('expand')">
              <Icons name="maximize" :size="14" />
              <span>Expand card</span>
            </button>
            <button type="button" class="action-btn-secondary" style="margin-top: 0; flex: 1;" @click="sendTrigger('collapse')">
              <Icons name="minus" :size="14" />
              <span>Collapse pill</span>
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

          <!-- Collapsible Terminal Output -->
          <div v-if="showLogs" class="terminal-container">
            <div class="terminal-header">
              <span>overlay.log</span>
              <button type="button" class="terminal-refresh-btn" @click="fetchLogs">Refresh</button>
            </div>
            <pre class="terminal-body">{{ logContent || 'No log entries recorded.' }}</pre>
          </div>
        </section>
      </div>

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

const currentTab = ref('placement')
const dpadStep = ref(1)

const config = reactive({
  cutout_x: 540,
  cutout_y: 52,
  cutout_radius: 36,
  pill_alignment: 'center',
  notch_mode: false,
  x_offset: 0,
  y_offset: 0,
  pill_width: 0,
  pill_height: 0,
  card_width: 0,
  card_radius: 24,
  enable_media: true,
  enable_charging: true,
  enable_volume: true,
  enable_ringer: true,
  enable_notifications: true,
  enable_torch: true,
  enable_hyperdl: true,
  enable_hypercore: true,
  stealth_ring_idle: false,
  hide_in_landscape: true,
  spring_stiffness: 380.0,
  spring_damping: 0.78,
  auto_expand_charging: true,
  auto_expand_media: false,
  auto_expand_notification: false,
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
  if (Math.abs(cLeft - 50) < 15) return 50
  if (cLeft < 35) return Math.max(14, cLeft + 6)
  return Math.min(86, cLeft - 6)
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
  configDebounceTimer = setTimeout(saveConfig, 250)
}

async function saveConfig() {
  const jsonStr = JSON.stringify(config, null, 2).replace(/'/g, "'\\x27'")
  await execShell(`mkdir -p /data/adb/modules/hyperring/state && echo '${jsonStr}' > /data/adb/modules/hyperring/state/config.json`)
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

function nudge(dx, dy) {
  config.x_offset = Math.max(-250, Math.min(250, config.x_offset + dx * dpadStep.value))
  config.y_offset = Math.max(-150, Math.min(200, config.y_offset + dy * dpadStep.value))
  saveConfigDebounced()
  showToast(`Offset X: ${config.x_offset > 0 ? `+${config.x_offset}` : config.x_offset}px · Y: ${config.y_offset > 0 ? `+${config.y_offset}` : config.y_offset}px`)
}

async function toggleReticle() {
  if (isReticleActive.value) {
    await sendTrigger('calibrate_off')
    showToast('Reticle dismissed')
  } else {
    await sendTrigger('calibrate')
    showToast('Reticle active on screen')
  }
}

function applyPreset(type) {
  currentPreset.value = type
  if (type === 'center') {
    config.cutout_x = 540
    config.cutout_y = 52
    config.cutout_radius = 36
    config.x_offset = 0
    config.y_offset = 0
  } else if (type === 'left') {
    config.cutout_x = 120
    config.cutout_y = 52
    config.cutout_radius = 36
    config.x_offset = 0
    config.y_offset = 0
  } else if (type === 'right') {
    config.cutout_x = 960
    config.cutout_y = 52
    config.cutout_radius = 36
    config.x_offset = 0
    config.y_offset = 0
  }
  saveConfig()
  showToast(`Applied ${type} preset`)
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
