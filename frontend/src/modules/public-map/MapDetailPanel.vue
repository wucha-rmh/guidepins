<script setup lang="ts">
import StatePanel from '../../shared/StatePanel.vue'
import type { PublicMapDetail } from './types'
import type { RemoteState } from './useRemoteResource'

defineProps<{
  detail: PublicMapDetail | null
  state: RemoteState
  error: string
  selectedPinId: string
}>()
defineEmits<{ retry: []; selectPin: [pinId: string] }>()
</script>

<template>
  <section class="map-detail-panel" aria-labelledby="map-detail-heading">
    <div class="panel-heading"><h2 id="map-detail-heading">地图与图钉详情</h2></div>
    <StatePanel v-if="state === 'loading'" kind="loading" title="正在加载地图详情" description="请稍候。" />
    <StatePanel v-else-if="state === 'error'" kind="error" title="地图详情加载失败"
      :description="error" @retry="$emit('retry')" />
    <template v-else-if="detail">
      <div class="pin-summary">
        <h3>{{ detail.name }}</h3>
        <span class="muted">{{ detail.pins.length }} 个图钉</span>
      </div>
      <StatePanel v-if="detail.pins.length === 0" kind="empty" title="这张地图暂无图钉"
        description="有新的地点时，会显示在地图上。" />
      <template v-else>
        <p class="panel-description">点击图钉或下面的条目查看说明。</p>
        <ol class="pin-list" aria-label="图钉列表">
          <li v-for="(pin, index) in detail.pins" :key="pin.id">
            <button type="button" class="pin-item" :class="{ 'pin-item--selected': pin.id === selectedPinId }"
              :aria-pressed="pin.id === selectedPinId" @click="$emit('selectPin', pin.id)">
              <span class="pin-index" aria-hidden="true">{{ index + 1 }}</span>
              <span>
                <span class="pin-name">{{ pin.name }}</span>
                <span v-if="pin.description" class="pin-description">{{ pin.description }}</span>
              </span>
            </button>
          </li>
        </ol>
      </template>
    </template>
  </section>
</template>
