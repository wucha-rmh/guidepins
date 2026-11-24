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
    <div class="panel-heading"><h2 id="map-detail-heading">沿途地点</h2><span v-if="state === 'ready' && detail" class="panel-count">{{ detail.pins.length }} 处</span></div>
    <StatePanel v-if="state === 'loading'" kind="loading" title="正在加载地图详情" description="请稍候。" />
    <StatePanel v-else-if="state === 'error'" kind="error" title="地图详情加载失败"
      :description="error" @retry="$emit('retry')" />
    <template v-else-if="detail">
      <StatePanel v-if="detail.pins.length === 0" kind="empty" title="这张地图暂无图钉"
        description="有新的地点时，会显示在地图上。" />
      <template v-else>
        <p class="panel-description">编号对应地图上的地点，点选可查看详情</p>
        <ol class="pin-list" aria-label="图钉列表">
          <li v-for="(pin, index) in detail.pins" :key="pin.id">
            <button type="button" class="pin-item" :class="{ 'pin-item--selected': pin.id === selectedPinId }"
              :aria-pressed="pin.id === selectedPinId" @click="$emit('selectPin', pin.id)">
              <span class="pin-index" aria-hidden="true">{{ index + 1 }}</span>
              <span class="pin-copy">
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
