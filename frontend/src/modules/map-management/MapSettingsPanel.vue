<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { errorMessage } from '../../shared/apiRequest'
import { useAuth } from '../auth/useAuth'
import type { PublicMapDetail } from '../public-map/types'
import { changeVisibility, formatMapTime, renameMap, validateMapName } from './api'
import type { ManagedMap } from './types'

const props = defineProps<{ map: PublicMapDetail; owned?: ManagedMap; adminCanRename: boolean }>()
const emit = defineEmits<{ updated: [map: ManagedMap] }>()
const auth = useAuth()
const name = ref(props.map.name)
const busy = ref(false)
const error = ref('')
const success = ref('')
const visibility = ref<ManagedMap['visibility']>(props.owned?.visibility ?? 'private')
const canRename = computed(() => !!props.owned || props.adminCanRename)
let active = true
onBeforeUnmount(() => { active = false })
watch(() => props.map.name, (value) => { name.value = value })
watch(() => props.owned?.visibility, (value) => { if (value) visibility.value = value })

async function save(kind: 'name' | 'visibility') {
  if (busy.value || auth.busy.value || auth.state.value !== 'ready' || !auth.user.value ||
    (kind === 'visibility' ? !props.owned : !canRename.value)) return
  error.value = ''
  success.value = ''
  const ownerId = auth.user.value.id
  const mapId = props.map.id
  busy.value = true
  try {
    const result = kind === 'name' ? await renameMap(mapId, validateMapName(name.value))
      : await changeVisibility(mapId, visibility.value)
    if (!active || auth.state.value !== 'ready' || auth.user.value?.id !== ownerId || props.map.id !== mapId) return
    if (result.id !== mapId || result.cityId !== props.map.cityId) throw new Error('地图响应不匹配，请刷新确认。')
    success.value = kind === 'name' ? '名称已保存。' : result.visibility === 'public' ? '地图已公开，访客可以浏览。' : '地图已设为私人，仅自己可见。'
    emit('updated', result)
  } catch (cause) {
    if (!active) return
    error.value = errorMessage(cause)
    auth.handleAuthError(cause)
  } finally { busy.value = false }
}
</script>

<template>
  <section class="map-settings-panel" aria-labelledby="map-settings-heading">
    <div class="panel-heading"><h2 id="map-settings-heading">{{ owned ? '管理地图' : '管理员维护' }}</h2><span v-if="owned" class="visibility-label">{{ owned.visibility === 'private' ? '私人' : '公开' }}</span></div>
    <p v-if="!owned" class="form-help">可维护公共地图名称，公开状态由所有者决定。</p>
    <form v-if="canRename" class="stack-form" @submit.prevent="save('name')">
      <label class="form-field" for="managed-map-name">地图名称<input id="managed-map-name" v-model="name" required maxlength="120" :disabled="busy" /></label>
      <button class="button button--secondary" type="submit" :disabled="busy || name.trim() === map.name">保存名称</button>
    </form>
    <form v-if="owned" class="stack-form visibility-form" @submit.prevent="save('visibility')">
      <label class="form-field" for="map-visibility">谁可以浏览<select id="map-visibility" v-model="visibility" :disabled="busy"><option value="private">私人 · 仅自己</option><option value="public">公开 · 所有人</option></select></label>
      <p class="form-help">公开后，地图会出现在该城市的公共地图列表。</p>
      <button class="button button--secondary" type="submit" :disabled="busy || visibility === owned.visibility">保存公开状态</button>
    </form>
    <p v-if="busy" class="form-help" role="status">正在保存…</p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="success" class="form-success" role="status">{{ success }}</p>
    <dl v-if="owned" class="map-times"><dt>创建时间</dt><dd>{{ formatMapTime(owned.createdAt) }}</dd><dt>最近更新</dt><dd>{{ formatMapTime(owned.updatedAt) }}</dd></dl>
  </section>
</template>
