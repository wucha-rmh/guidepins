<script setup lang="ts">
import StatePanel from './StatePanel.vue'
import type { PublicMapDetail } from '../types/publicMap'
import type { RemoteState } from '../composables/useRemoteResource'

defineProps<{
  detail: PublicMapDetail | null
  state: RemoteState
  error: string
  selectedPinId: string
  /** 当前身份可以维护这张地图的图钉时，列表给出编辑入口。 */
  editable?: boolean
}>()
defineEmits<{ retry: []; selectPin: [pinId: string]; editPin: [pinId: string]; openGuide: [guideId: string] }>()
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
          <li v-for="(pin, index) in detail.pins" :key="pin.id" class="pin-row">
            <div class="pin-entry">
              <button type="button" class="pin-item" :class="{ 'pin-item--selected': pin.id === selectedPinId }"
                :aria-pressed="pin.id === selectedPinId" @click="$emit('selectPin', pin.id)">
                <span class="pin-index" aria-hidden="true">{{ index + 1 }}</span>
                <span class="pin-copy">
                  <span class="pin-name">{{ pin.name }}</span>
                  <span v-if="pin.description" class="pin-description">{{ pin.description }}</span>
                </span>
              </button>
              <!-- 出处以攻略为单位列出：一个地点被多篇攻略提到时逐条显示，点标题跳到该篇。 -->
              <p v-if="pin.guides.length" class="pin-guides">
                <span class="pin-guides-label">出处：</span>
                <button v-for="guide in pin.guides" :key="guide.id" type="button"
                  class="text-link pin-guide-link" @click="$emit('openGuide', guide.id)">{{ guide.title }}</button>
              </p>
            </div>
            <button v-if="editable" type="button" class="pin-item-edit"
              :aria-label="`编辑地点 ${index + 1}：${pin.name}`" @click="$emit('editPin', pin.id)">编辑</button>
          </li>
        </ol>
      </template>
    </template>
  </section>
</template>
