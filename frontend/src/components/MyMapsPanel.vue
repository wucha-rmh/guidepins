<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { RouterLink } from 'vue-router'
import StatePanel from './StatePanel.vue'
import FormField from './FormField.vue'
import { errorMessage } from '../api/client'
import { useAuth } from '../composables/useAuth'
import { createMap, formatMapTime, validateMapName } from '../api/mapManagement'
import type { ManagedMap } from '../types/mapManagement'

const props = defineProps<{ cityId: string; maps: readonly ManagedMap[]; state: string; error: string; selectedMapId: string }>()
const emit = defineEmits<{ retry: []; created: [map: ManagedMap] }>()
const auth = useAuth()
const name = ref('')
const busy = ref(false)
const failure = ref('')
let active = true
onBeforeUnmount(() => { active = false })

async function create() {
  if (busy.value || auth.busy.value || auth.state.value !== 'ready' || !auth.user.value) return
  failure.value = ''
  const ownerId = auth.user.value.id
  const cityId = props.cityId
  busy.value = true
  try {
    const result = await createMap(cityId, validateMapName(name.value))
    if (!active || auth.state.value !== 'ready' || auth.user.value?.id !== ownerId || props.cityId !== cityId) return
    if (result.ownerId !== ownerId || result.cityId !== cityId) throw new Error('地图归属不匹配，请刷新确认。')
    name.value = ''
    emit('created', result)
  } catch (cause) {
    if (!active) return
    failure.value = errorMessage(cause)
    auth.handleAuthError(cause)
  } finally { busy.value = false }
}
</script>

<template>
  <section class="public-map-panel" aria-labelledby="my-maps-heading">
    <div class="panel-heading"><h2 id="my-maps-heading">我的地图</h2><span v-if="state === 'ready'" class="panel-count">{{ maps.length }} 张</span></div>
    <p class="panel-description">留一份自己的城市手记。</p>
    <form class="stack-form create-map-form" @submit.prevent="create">
      <FormField label="地图名称" control-id="new-map-name">
        <input id="new-map-name" v-model="name" required maxlength="120" placeholder="例如：周末秦淮散步" :disabled="busy" />
      </FormField>
      <p class="form-help">新地图默认私人，仅自己可见。</p>
      <p v-if="failure" class="form-error" role="alert">{{ failure }}</p>
      <button class="button button--primary" :disabled="busy || auth.busy.value" type="submit">{{ busy ? '正在创建…' : '创建地图' }}</button>
    </form>
    <StatePanel v-if="state === 'loading'" kind="loading" title="正在加载我的地图" description="请稍候。" />
    <StatePanel v-else-if="state === 'error'" kind="error" title="我的地图加载失败" :description="error" @retry="emit('retry')" />
    <StatePanel v-else-if="state === 'ready' && maps.length === 0" kind="empty" title="还没有自己的地图" description="从一个名字开始，创建第一张地图。" />
    <ul v-else class="owned-map-list">
      <li v-for="map in maps" :key="map.id">
        <RouterLink class="owned-map-card" :class="{ 'owned-map-card--selected': map.id === selectedMapId }" :aria-current="map.id === selectedMapId ? 'page' : undefined"
          :to="{ name: 'public-map', params: { cityId, mapId: map.id }, query: { tab: 'mine' } }">
          <span class="owned-map-title">{{ map.name }}</span>
          <span class="visibility-label">{{ map.visibility === 'private' ? '私人' : '公开' }}</span>
          <small>更新于 {{ formatMapTime(map.updatedAt) }}</small>
        </RouterLink>
      </li>
    </ul>
  </section>
</template>
