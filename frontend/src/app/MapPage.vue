<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useAuth } from '../modules/auth/useAuth'
import MyMapsPanel from '../modules/map-management/MyMapsPanel.vue'
import MapSettingsPanel from '../modules/map-management/MapSettingsPanel.vue'
import { useOwnedMaps } from '../modules/map-management/useOwnedMaps'
import type { ManagedMap } from '../modules/map-management/types'
import '../modules/map-management/styles.css'
import BasemapViewport from '../modules/basemap/BasemapViewport.vue'
import { useCities } from '../modules/city/useCities'
import MapDetailPanel from '../modules/public-map/MapDetailPanel.vue'
import PublicMapPanel from '../modules/public-map/PublicMapPanel.vue'
import type { MapPin } from '../modules/public-map/types'
import { usePublicMapDetail } from '../modules/public-map/usePublicMapDetail'
import { usePublicMaps } from '../modules/public-map/usePublicMaps'
import StatePanel from '../shared/StatePanel.vue'

const props = defineProps<{ personalMapsRequest?: number }>()
const route = useRoute()
const router = useRouter()
const auth = useAuth()
const { user, state: authState, error: authError, restore } = auth
const { cities, state: cityState, error: cityError, reload: reloadCities } = useCities()
const cityId = computed(() => String(route.params.cityId ?? ''))
const mapId = computed(() => (route.params.mapId ? String(route.params.mapId) : ''))
const mine = computed(() => route.query.tab === 'mine')
const loginRoute = computed(() => ({ name: 'auth', query: { redirect: route.fullPath } }))
const owned = useOwnedMaps(() => cities.value.some((item) => item.id === cityId.value) ? cityId.value : '')
const ownedMap = computed(() => owned.maps.value.find((map) => map.id === mapId.value))

// 列表与详情各自读取接口，URL 是地图选择的唯一来源：刷新或直接粘贴地址都能恢复。
const { maps, state: mapsState, error: mapsError, reload: reloadMaps } = usePublicMaps(() => cityId.value)
const { detail, state: detailState, error: detailError, notFound: detailNotFound, reload: reloadDetail } =
  usePublicMapDetail(() => authState.value === 'ready' ? mapId.value : '', () => user.value?.id ?? '')
const adminCanRename = computed(() => authState.value === 'ready' && user.value?.role === 'admin' &&
  mapsState.value === 'ready' && maps.value.some((map) => map.id === mapId.value))

const city = computed(() => cities.value.find((item) => item.id === cityId.value))
const emptyPins: MapPin[] = []
const pins = computed(() => !mapMissing.value && detail.value?.cityId === cityId.value ? detail.value.pins : emptyPins)
const mapName = computed(() => detail.value?.name ?? '')

// 地址里的地图不存在，或详情不属于当前地市时，按“找不到”处理，避免串到别的城市。
const mapMissing = computed(() => mapId.value !== '' &&
  (detailNotFound.value || (detail.value !== null && detail.value.cityId !== cityId.value)))

// 图钉选中状态在页面统一持有：侧栏列表与地图图钉共享，切换地图时清空。
const selectedPinId = ref('')
const viewport = ref<InstanceType<typeof BasemapViewport> | null>(null)
watch(() => detail.value?.id, () => { selectedPinId.value = '' })

const sidebarCollapsed = ref(false)
const sidebar = ref<HTMLElement | null>(null)
const sidebarToggle = ref<HTMLButtonElement | null>(null)
let sidebarScrollTop = 0

async function setSidebarCollapsed(collapsed: boolean) {
  if (sidebarCollapsed.value === collapsed) return
  if (collapsed) {
    sidebarScrollTop = sidebar.value?.scrollTop ?? 0
    if (sidebar.value?.contains(document.activeElement)) sidebarToggle.value?.focus()
  }
  // 仅隐藏侧栏，不卸载表单和详情；地图已有 ResizeObserver 负责扩展画布。
  sidebarCollapsed.value = collapsed
  if (!collapsed) {
    await nextTick()
    if (sidebar.value) sidebar.value.scrollTop = sidebarScrollTop
  }
}
watch(cityId, () => { sidebarScrollTop = 0; sidebarCollapsed.value = false })
watch(() => props.personalMapsRequest, () => { void setSidebarCollapsed(false) })
watch(mine, (value) => { if (value) void setSidebarCollapsed(false) })

function selectTab(tab: 'public' | 'mine') {
  void router.replace({ path: route.path, query: tab === 'mine' ? { tab: 'mine' } : {} })
}

async function createdMap(map: ManagedMap) {
  owned.applyMap(map)
  await router.push({ name: 'public-map', params: { cityId: map.cityId, mapId: map.id }, query: { tab: 'mine' } })
}

function updatedMap(map: ManagedMap) {
  // 写入返回后合并个人记录并刷新公共列表，使名称、公开状态与更新时间保持一致。
  if (detail.value?.id === map.id) detail.value = { ...detail.value, name: map.name }
  owned.applyMap(map)
  void reloadMaps()
}

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
  <div v-else class="map-page">
    <header class="map-page-heading">
      <div class="map-heading-title"><RouterLink class="back-link" to="/"><span aria-hidden="true">←</span>城市目录</RouterLink><span class="heading-divider" aria-hidden="true"></span>
        <h1>{{ city.name }}</h1>
        <span v-if="mapId" class="current-map-name" :title="mapName">{{ mapMissing ? '地图不可访问' : mapName || (detailState === 'error' ? '地图详情不可用' : '正在加载地图…') }}</span></div>
      <RouterLink v-if="mapId" class="button button--secondary close-map" :to="{ name: 'city-map', params: { cityId }, query: mine ? { tab: 'mine' } : {} }"><span aria-hidden="true">×</span>关闭地图</RouterLink>
      <span v-else class="workspace-hint"><span aria-hidden="true"></span>自由探索，从这里开始</span>
    </header>
    <div class="map-workspace" :class="{ 'map-workspace--sidebar-collapsed': sidebarCollapsed }">
      <aside v-show="!sidebarCollapsed" id="map-sidebar" ref="sidebar" class="map-sidebar" aria-label="地图列表与详情">
        <div class="map-tabs" role="group" aria-label="地图列表分类">
          <button type="button" :aria-pressed="!mine" @click="selectTab('public')">公共地图</button>
          <button type="button" :aria-pressed="mine" @click="selectTab('mine')">我的地图</button>
        </div>
        <PublicMapPanel v-if="!mine" :city-id="cityId" :maps="maps" :state="mapsState" :error="mapsError"
          :selected-map-id="mapId" @retry="reloadMaps" />
        <StatePanel v-else-if="authState === 'loading'" kind="loading" title="正在恢复登录" description="请稍候。" />
        <StatePanel v-else-if="authState === 'error'" kind="error" title="无法确认登录状态" :description="authError" @retry="restore" />
        <StatePanel v-else-if="!user" kind="empty" title="登录后查看自己的地图" description="在这座城市留下一份自己的手记。">
          <RouterLink class="button button--primary" :to="loginRoute">去登录</RouterLink>
        </StatePanel>
        <MyMapsPanel v-else :key="`${cityId}:${user.id}`" :city-id="cityId" :maps="owned.maps.value" :state="owned.state.value" :error="owned.error.value"
          :selected-map-id="mapId" @retry="owned.reload" @created="createdMap" />
        <StatePanel v-if="mapId && authState === 'loading'" kind="loading" title="正在确认地图访问权限" description="登录状态恢复后自动读取地图。" />
        <StatePanel v-else-if="mapId && authState === 'error'" kind="error" title="无法确认地图访问权限" :description="authError" @retry="restore" />
        <StatePanel v-else-if="mapMissing" kind="empty" title="找不到这张地图" description="地图可能不存在、不可访问，或不属于当前城市。">
          <RouterLink v-if="!user" class="text-link" :to="loginRoute">如果这是你的私人地图，请先登录</RouterLink>
          <RouterLink class="text-link" :to="{ name: 'city-map', params: { cityId }, query: mine ? { tab: 'mine' } : {} }">返回城市底图</RouterLink>
        </StatePanel>
        <MapSettingsPanel v-else-if="detail && (ownedMap || adminCanRename)" :key="`${detail.id}:${user?.id}`" :map="detail" :owned="ownedMap"
          :admin-can-rename="adminCanRename" @updated="updatedMap" />
        <MapDetailPanel v-if="mapId && !mapMissing && authState === 'ready'" :detail="detail" :state="detailState" :error="detailError"
          :selected-pin-id="selectedPinId" @retry="reloadDetail" @select-pin="focusPin" />
        <div v-if="!mapId" class="sidebar-note"><span class="sidebar-note-mark" aria-hidden="true">⌖</span><h3>先随意逛逛，也很好。</h3><p>拖动地图发现街巷，或选一张地图，沿着地点慢慢探索。</p></div>
      </aside>
      <button ref="sidebarToggle" class="sidebar-toggle" type="button" :aria-expanded="!sidebarCollapsed" aria-controls="map-sidebar"
        :aria-label="sidebarCollapsed ? '展开地图侧栏' : '收起地图侧栏'" :title="sidebarCollapsed ? '展开地图侧栏' : '收起地图侧栏'"
        @click="setSidebarCollapsed(!sidebarCollapsed)">
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false"><path d="m14 6-6 6 6 6" /></svg>
      </button>
      <BasemapViewport ref="viewport" :key="city.id" :city="city" :pins="pins"
        :selected-pin-id="selectedPinId" @select-pin="selectPin" />
    </div>
  </div>
</template>
