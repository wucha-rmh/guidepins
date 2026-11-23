<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import BasemapViewport from '../modules/basemap/BasemapViewport.vue'
import { useCities } from '../modules/city/useCities'
import MapDetailPanel from '../modules/public-map/MapDetailPanel.vue'
import PublicMapPanel from '../modules/public-map/PublicMapPanel.vue'
import type { MapPin } from '../modules/public-map/types'
import { usePublicMapDetail } from '../modules/public-map/usePublicMapDetail'
import { usePublicMaps } from '../modules/public-map/usePublicMaps'
import StatePanel from '../shared/StatePanel.vue'

const route = useRoute()
const { cities, state: cityState, error: cityError, reload: reloadCities } = useCities()
const cityId = computed(() => String(route.params.cityId ?? ''))
const mapId = computed(() => (route.params.mapId ? String(route.params.mapId) : ''))

// 列表与详情各自读取接口，URL 是地图选择的唯一来源：刷新或直接粘贴地址都能恢复。
const { maps, state: mapsState, error: mapsError, reload: reloadMaps } = usePublicMaps(() => cityId.value)
const { detail, state: detailState, error: detailError, notFound: detailNotFound, reload: reloadDetail } =
  usePublicMapDetail(() => mapId.value)

const city = computed(() => cities.value.find((item) => item.id === cityId.value))
const emptyPins: MapPin[] = []
const pins = computed(() => detail.value?.pins ?? emptyPins)
const mapName = computed(() => detail.value?.name ?? '')

// 地址里的地图不存在，或详情不属于当前地市时，按“找不到”处理，避免串到别的城市。
const mapMissing = computed(() => mapId.value !== '' &&
  (detailNotFound.value || (detail.value !== null && detail.value.cityId !== cityId.value)))

// 图钉选中状态在页面统一持有：侧栏列表与地图图钉共享，切换地图时清空。
const selectedPinId = ref('')
const viewport = ref<InstanceType<typeof BasemapViewport> | null>(null)
watch(() => detail.value?.id, () => { selectedPinId.value = '' })

function selectPin(pinId: string) {
  selectedPinId.value = pinId
}

/** 从列表点选时把地图移到该图钉；点地图图钉时不移动视野。 */
function focusPin(pinId: string) {
  selectedPinId.value = pinId
  viewport.value?.flyToPin(pinId)
}
</script>

<template>
  <div v-if="cityState !== 'ready'" class="not-found-page">
    <StatePanel :kind="cityState === 'error' ? 'error' : 'loading'"
      :title="cityState === 'error' ? '地市信息加载失败' : '正在加载地市'"
      :description="cityState === 'error' ? cityError : '正在恢复当前地市，请稍候。'" @retry="reloadCities">
      <RouterLink class="text-link" to="/">返回地市选择</RouterLink>
    </StatePanel>
  </div>
  <div v-else-if="!city" class="not-found-page">
    <StatePanel kind="empty" title="找不到这个地市" description="地市可能已下线，请从目录重新选择。">
      <RouterLink class="button button--primary" to="/">返回地市选择</RouterLink>
    </StatePanel>
  </div>
  <div v-else-if="mapMissing" class="not-found-page">
    <StatePanel kind="empty" title="找不到这张地图" :description="`它可能已下线，或不属于${city.name}。`">
      <RouterLink class="button button--primary" :to="{ name: 'city-map', params: { cityId } }">返回{{ city.name }}底图</RouterLink>
    </StatePanel>
  </div>
  <div v-else class="map-page">
    <header class="map-page-heading">
      <div class="map-heading-title"><RouterLink class="text-link" to="/">返回地市选择</RouterLink><span class="heading-divider"></span>
        <h1>{{ city.name }}</h1>
        <span v-if="mapId" class="muted">{{ mapName || (detailState === 'error' ? '地图详情不可用' : '正在加载公共地图…') }}</span></div>
      <RouterLink v-if="mapId" class="button button--secondary" :to="{ name: 'city-map', params: { cityId } }">关闭公共地图</RouterLink>
    </header>
    <div class="map-workspace">
      <aside class="map-sidebar" aria-label="公共地图与详情">
        <PublicMapPanel :city-id="cityId" :maps="maps" :state="mapsState" :error="mapsError"
          :selected-map-id="mapId" @retry="reloadMaps" />
        <MapDetailPanel v-if="mapId" :detail="detail" :state="detailState" :error="detailError"
          :selected-pin-id="selectedPinId" @retry="reloadDetail" @select-pin="focusPin" />
        <div v-else class="sidebar-note"><h3>浏览地市底图</h3><p>拖动或缩放地图查看街区；“主城区”回到街区首屏，“查看全市”看完整市域。</p></div>
      </aside>
      <BasemapViewport ref="viewport" :key="city.id" :city="city" :pins="pins"
        :selected-pin-id="selectedPinId" @select-pin="selectPin" />
    </div>
  </div>
</template>
