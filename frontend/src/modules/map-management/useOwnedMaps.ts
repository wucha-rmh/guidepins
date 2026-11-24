import { computed, onScopeDispose, ref, shallowRef, watch } from 'vue'
import { useAuth } from '../auth/useAuth'
import { errorMessage } from '../../shared/apiRequest'
import { listOwnedMaps } from './api'
import type { ManagedMap } from './types'

/** 身份与地市共同决定个人列表；同步清空旧数据，取消并忽略已被替代的读取。 */
export function useOwnedMaps(cityId: () => string) {
  const auth = useAuth()
  const maps = shallowRef<ManagedMap[]>([])
  const state = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
  const error = ref('')
  let active: AbortController | undefined

  function reset() {
    active?.abort()
    active = undefined
    maps.value = []
    error.value = ''
    state.value = 'idle'
  }

  async function reload() {
    reset()
    const ownerId = auth.user.value?.id
    const city = cityId()
    if (!ownerId || !city || auth.state.value !== 'ready') return
    const request = new AbortController()
    active = request
    state.value = 'loading'
    try {
      const result = await listOwnedMaps(city, ownerId, request.signal)
      if (active !== request) return
      maps.value = result
      state.value = 'ready'
    } catch (cause) {
      if (active !== request) return
      error.value = errorMessage(cause)
      state.value = 'error'
      auth.handleAuthError(cause)
    }
  }

  /**
   * 图钉写入会推进地图更新时间：只替换数据、不切换到加载态，
   * 避免正在显示的编辑表单和成功反馈被卸载。失败时保留原列表。
   */
  async function refresh() {
    const ownerId = auth.user.value?.id
    const city = cityId()
    if (!ownerId || !city || auth.state.value !== 'ready' || state.value !== 'ready') return
    try {
      maps.value = await listOwnedMaps(city, ownerId, new AbortController().signal)
    } catch {
      // 更新时间只是辅助信息，读取失败不打扰当前操作。
    }
  }

  watch([cityId, () => auth.user.value?.id, () => auth.state.value], () => { void reload() }, { immediate: true, flush: 'sync' })
  onScopeDispose(reset)

  function applyMap(map: ManagedMap) {
    if (map.ownerId !== auth.user.value?.id || map.cityId !== cityId()) return
    if (state.value !== 'ready') { void reload(); return }
    // 使用写接口的权威结果更新列表，避免刷新时卸载正在显示成功反馈的编辑表单。
    maps.value = [...maps.value.filter((item) => item.id !== map.id), map].sort((a, b) =>
      Date.parse(b.updatedAt) - Date.parse(a.updatedAt) || a.id.localeCompare(b.id))
  }
  return { maps: computed(() => maps.value), state, error, reload, refresh, applyMap }
}
