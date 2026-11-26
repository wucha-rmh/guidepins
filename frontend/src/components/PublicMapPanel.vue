<script setup lang="ts">
import { RouterLink } from 'vue-router'
import StatePanel from './StatePanel.vue'
import type { RemoteState } from '../composables/useRemoteResource'
import type { PublicMap } from '../types/publicMap'

defineProps<{
  cityId: string
  maps: PublicMap[]
  state: RemoteState
  error: string
  /** 地址中已打开的地图，用于标出当前项。 */
  selectedMapId?: string
}>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <section class="public-map-panel" aria-labelledby="public-maps-heading">
    <span class="section-kicker">CITY GUIDES</span>
    <div class="panel-heading">
      <h2 id="public-maps-heading">公共地图</h2>
      <span v-if="state === 'ready'" class="panel-count">{{ maps.length }} 张</span>
    </div>
    <p class="panel-description">找一份灵感，走进这座城。</p>
    <StatePanel v-if="state === 'loading'" kind="loading" title="正在加载公共地图" description="请稍候。" />
    <StatePanel v-else-if="state === 'error'" kind="error" title="公共地图加载失败"
      :description="error" @retry="$emit('retry')" />
    <StatePanel v-else-if="maps.length === 0" kind="empty" title="暂无公共地图"
      description="你仍然可以浏览这座城市的底图。" />
    <ul v-else class="public-map-list">
      <li v-for="map in maps" :key="map.id">
        <RouterLink class="public-map-card" :class="{ 'public-map-card--selected': map.id === selectedMapId }"
          :aria-current="map.id === selectedMapId ? 'page' : undefined"
          :to="{ name: 'public-map', params: { cityId, mapId: map.id } }">
          <span class="map-card-symbol" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" focusable="false"><path d="M3 6L9 3L15 6L21 3V18L15 21L9 18L3 21V6ZM9 3V18M15 6V21"/></svg></span>
          <div class="public-map-card-body"><h3>{{ map.name }}</h3><span class="public-map-card-action">{{ map.id === selectedMapId ? '正在浏览' : '打开地图' }}</span></div>
          <span class="map-card-arrow" aria-hidden="true">{{ map.id === selectedMapId ? '•' : '↗' }}</span>
        </RouterLink>
      </li>
    </ul>
  </section>
</template>
