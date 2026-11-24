<script setup lang="ts">
import { onMounted, onScopeDispose, ref, watch } from 'vue'
import 'maplibre-gl/dist/maplibre-gl.css'
import StatePanel from '../../shared/StatePanel.vue'
import type { City } from '../city/types'
import type { MapPin } from '../public-map/types'
import { createBasemap } from './createBasemap'

const props = defineProps<{
  city: City
  /** 当前公共地图的图钉；为空时只显示地市底图。 */
  pins: MapPin[]
  /** 选中的图钉，与侧栏列表共用同一份状态。 */
  selectedPinId: string
}>()
const emit = defineEmits<{ selectPin: [pinId: string] }>()

const container = ref<HTMLElement | null>(null)
const state = ref<'loading' | 'ready' | 'error'>('loading')
const error = ref('')
const zoom = ref(0)
let basemap: ReturnType<typeof createBasemap> | undefined

function load() {
  if (!container.value) return
  basemap?.destroy()
  basemap = undefined
  state.value = 'loading'
  error.value = ''
  try {
    basemap = createBasemap(container.value, props.city, {
      ready: () => { state.value = 'ready' },
      failed: (message) => { error.value = message; state.value = 'error' },
      moved: (value) => { zoom.value = value },
      pinSelected: (pinId) => emit('selectPin', pinId),
    })
    // 重试加载后立即恢复已有图钉，避免底图与侧栏短暂不一致。
    basemap.setPins(props.pins, props.selectedPinId)
    basemap.fitPins(props.pins)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '底图初始化失败，请重试。'
    state.value = 'error'
  }
}

// 打开或切换公共地图时把视野收到图钉范围；清除图钉（关闭地图）时保持当前视野。
watch(() => props.pins, (pins) => {
  basemap?.setPins(pins, props.selectedPinId)
  basemap?.fitPins(pins)
})
watch(() => props.selectedPinId, (selectedPinId) => basemap?.setPins(props.pins, selectedPinId))

function flyToPin(pinId: string) {
  basemap?.flyToPin(pinId)
}
defineExpose({ flyToPin })

onMounted(load)
watch(() => props.city, load)
onScopeDispose(() => basemap?.destroy())
</script>

<template>
  <section class="basemap-viewport" :aria-label="`${city.name}底图`" :aria-busy="state === 'loading'">
    <div ref="container" class="basemap-canvas"></div>
    <div class="map-caption">
      <strong>{{ city.name }}</strong>
      <span v-if="pins.length" class="map-pin-count">{{ pins.length }} 处地点</span>
    </div>
    <div class="map-view-actions" role="group" aria-label="地图视野">
      <button class="button button--secondary" type="button" :disabled="state !== 'ready'"
        @click="basemap?.fitUrban()">主城区</button>
      <button class="button button--secondary" type="button" :disabled="state !== 'ready'"
        @click="basemap?.fitCity()">查看全市</button>
    </div>
    <div v-if="state !== 'ready'" class="basemap-state">
      <StatePanel :kind="state" :title="state === 'loading' ? '正在加载底图' : '底图加载失败'"
        :description="state === 'loading' ? '正在读取地图，请稍候。' : error" retry-label="重试加载底图" @retry="load" />
    </div>
    <div v-else class="map-bottom-note" role="status">
      <span class="map-zoom-label">{{ zoom >= 14 ? '街区视角' : '城市视角' }}</span><span v-if="pins.length">点选编号查看地点</span><span v-else>拖动浏览 · 滚轮缩放</span>
    </div>
  </section>
</template>
