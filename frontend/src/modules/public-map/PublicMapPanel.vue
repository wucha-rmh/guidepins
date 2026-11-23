<script setup lang="ts">
import { RouterLink } from 'vue-router'
import StatePanel from '../../shared/StatePanel.vue'
import type { RemoteState } from './useRemoteResource'
import type { PublicMap } from './types'

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
    <div class="panel-heading">
      <h2 id="public-maps-heading">公共地图</h2>
      <span v-if="state === 'ready'" class="tag">{{ maps.length }} 张</span>
    </div>
    <p class="panel-description">挑选一张地图，看看这座城市里值得停留的地方。</p>
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
          <h3>{{ map.name }}</h3>
          <span class="public-map-card-action">{{ map.id === selectedMapId ? '当前地图' : '打开地图' }}</span>
        </RouterLink>
      </li>
    </ul>
  </section>
</template>
