import { ref, shallowRef, watch } from 'vue'
import { errorMessage } from '../api/client'
import { useAuth } from './useAuth'
import { createGuide, deleteGuide, listGuides, setGuidePins, updateGuide } from '../api/guideEditing'
import type { GuideDetail, GuideValues } from '../types/guideEditing'

/** 攻略列表的加载状态；idle 表示当前没有打开地图。 */
export type GuidesState = 'idle' | 'loading' | 'ready' | 'error'

interface Options {
  /** 当前地图 ID；切换地图时丢弃旧请求与旧提示。 */
  mapId: () => string
  /** 攻略或其关联发生变化后回调，由地图页面刷新图钉详情与更新时间。 */
  onChanged: () => void
}

/**
 * 攻略列表与写入的业务状态：读取、保存、删除与关联重设都在这里，
 * 表单字段与拖动排序留在面板里，界面与数据访问互不混杂。
 */
export function useGuides({ mapId, onChanged }: Options) {
  const auth = useAuth()
  const guides = shallowRef<GuideDetail[]>([])
  const state = ref<GuidesState>('idle')
  const error = ref('')
  const busy = ref(false)
  const actionError = ref('')
  const notice = ref('')
  let active: AbortController | undefined

  function reset() {
    active?.abort()
    active = undefined
    guides.value = []
    state.value = 'idle'
    error.value = ''
  }

  async function reload() {
    const currentMapId = mapId()
    if (!currentMapId) { reset(); return }
    active?.abort()
    const request = new AbortController()
    active = request
    state.value = 'loading'
    error.value = ''
    try {
      const result = await listGuides(currentMapId, request.signal)
      if (active !== request) return
      guides.value = result
      state.value = 'ready'
    } catch (cause) {
      // 地图已换走或被更新的请求取代时，旧结果不再影响界面。
      if (active !== request) return
      error.value = errorMessage(cause)
      state.value = 'error'
    }
  }

  watch(mapId, () => {
    actionError.value = ''
    notice.value = ''
    void reload()
  }, { immediate: true })

  /** 保存攻略并重设关联；两步都成功才算保存完成，失败时保留面板里的草稿。 */
  async function save(values: GuideValues, guideId: string | null, pinIds: string[]): Promise<GuideDetail | null> {
    if (busy.value) return null
    const currentMapId = mapId()
    if (auth.state.value !== 'ready' || !auth.user.value) {
      actionError.value = '登录状态已失效，请重新登录后再保存。'
      return null
    }
    busy.value = true
    actionError.value = ''
    notice.value = ''
    try {
      const saved = guideId
        ? await updateGuide(currentMapId, guideId, values)
        : await createGuide(currentMapId, values)
      // 关联整体重设：后端按数组顺序写入站序，关联不变时不会推进地图更新时间。
      const linked = await setGuidePins(currentMapId, saved.id, pinIds)
      if (mapId() !== currentMapId) return null
      await reload()
      notice.value = guideId ? '攻略已保存。' : '攻略已添加。'
      onChanged()
      return linked
    } catch (cause) {
      if (mapId() !== currentMapId) return null
      actionError.value = errorMessage(cause)
      auth.handleAuthError(cause)
      return null
    } finally {
      busy.value = false
    }
  }

  async function remove(guideId: string): Promise<boolean> {
    if (busy.value) return false
    const currentMapId = mapId()
    if (auth.state.value !== 'ready' || !auth.user.value) {
      actionError.value = '登录状态已失效，请重新登录后再删除。'
      return false
    }
    busy.value = true
    actionError.value = ''
    notice.value = ''
    try {
      await deleteGuide(currentMapId, guideId)
      if (mapId() !== currentMapId) return false
      await reload()
      notice.value = '攻略已删除。'
      onChanged()
      return true
    } catch (cause) {
      if (mapId() !== currentMapId) return false
      actionError.value = errorMessage(cause)
      auth.handleAuthError(cause)
      return false
    } finally {
      busy.value = false
    }
  }

  return { guides, state, error, busy, actionError, notice, reload, save, remove }
}
