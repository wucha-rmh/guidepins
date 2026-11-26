import { ref, shallowRef } from 'vue'
import { errorMessage } from '../api/client'
import { useAuth } from './useAuth'
import type { MapPin } from '../types/publicMap'
import {
  createPin, deletePin, normalizeCoordinates, updatePin,
  validatePinCoordinates, validatePinDescription, validatePinName,
} from '../api/pinEditing'
import type { PinCoordinates, PinEditingTarget } from '../types/pinEditing'

/** 编辑流程的三个阶段：idle 不编辑，placing 等在地图上点位置，editing 在表单里确认。 */
export type PinEditingPhase = 'idle' | 'placing' | 'editing'

interface Options {
  /** 当前地图 ID；编辑期间切换地图时丢弃旧结果。 */
  mapId: () => string
  /** 写入成功后的回调，由地图页面刷新详情并保持选中状态。 */
  onSaved: (pin: MapPin, kind: 'create' | 'edit') => void
  onDeleted: (pinId: string) => void
}

/**
 * 图钉编辑的业务状态：只负责阶段、草稿坐标与写请求，
 * 表单字段由面板自己持有，界面与数据访问互不混杂。
 */
export function usePinEditing({ mapId, onSaved, onDeleted }: Options) {
  const auth = useAuth()
  const phase = ref<PinEditingPhase>('idle')
  const target = shallowRef<PinEditingTarget | null>(null)
  const coordinates = shallowRef<PinCoordinates | null>(null)
  const busy = ref(false)
  const error = ref('')
  const notice = ref('')
  // 进入编辑时记住地图；请求返回后地图已换走，结果不再影响新地图。
  let editingMapId = ''

  function clearMessages() {
    error.value = ''
    notice.value = ''
  }

  function close() {
    phase.value = 'idle'
    target.value = null
    coordinates.value = null
  }

  function cancel() {
    if (busy.value) return
    // 取消只放弃本次编辑，上一次的成功提示继续保留给用户看。
    error.value = ''
    close()
  }

  function startPlacing() {
    if (busy.value) return
    clearMessages()
    editingMapId = mapId()
    target.value = null
    coordinates.value = null
    phase.value = 'placing'
  }

  /** 地图上点选位置后进入表单；坐标先收进合法范围再显示。 */
  function placeAt(point: PinCoordinates) {
    if (phase.value !== 'placing') return
    coordinates.value = normalizeCoordinates(point)
    target.value = { kind: 'create' }
    phase.value = 'editing'
  }

  function startEditing(pin: MapPin) {
    if (busy.value) return
    clearMessages()
    editingMapId = mapId()
    target.value = { kind: 'edit', pinId: pin.id }
    coordinates.value = { longitude: pin.longitude, latitude: pin.latitude }
    phase.value = 'editing'
  }

  /** 拖动虚线圆环时同步坐标；拖动中不触发校验错误，只做范围收敛。 */
  function moveTo(point: PinCoordinates) {
    if (phase.value !== 'editing') return
    coordinates.value = normalizeCoordinates(point)
  }

  async function save(name: string, description: string) {
    if (busy.value || phase.value !== 'editing' || !target.value || !coordinates.value) return
    if (auth.state.value !== 'ready' || !auth.user.value) {
      error.value = '登录状态已失效，请重新登录后再保存。'
      return
    }
    const currentMapId = mapId()
    if (currentMapId !== editingMapId) { close(); return }
    let payload: { name: string; description: string | null; coordinates: PinCoordinates }
    try {
      payload = {
        name: validatePinName(name),
        description: validatePinDescription(description),
        coordinates: validatePinCoordinates(coordinates.value.longitude, coordinates.value.latitude),
      }
    } catch (cause) {
      error.value = errorMessage(cause)
      return
    }
    busy.value = true
    error.value = ''
    notice.value = ''
    const editing = target.value
    try {
      const result = editing.kind === 'create'
        ? await createPin(currentMapId, payload.name, payload.description, payload.coordinates)
        : await updatePin(currentMapId, editing.pinId, payload.name, payload.description, payload.coordinates)
      if (mapId() !== currentMapId) return
      close()
      notice.value = editing.kind === 'create' ? '图钉已添加。' : '图钉已保存。'
      onSaved(result, editing.kind === 'create' ? 'create' : 'edit')
    } catch (cause) {
      if (mapId() !== currentMapId) return
      error.value = errorMessage(cause)
      auth.handleAuthError(cause)
    } finally {
      busy.value = false
    }
  }

  async function remove() {
    if (busy.value || !target.value || target.value.kind !== 'edit') return
    if (auth.state.value !== 'ready' || !auth.user.value) {
      error.value = '登录状态已失效，请重新登录后再删除。'
      return
    }
    const currentMapId = mapId()
    if (currentMapId !== editingMapId) { close(); return }
    const pinId = target.value.pinId
    busy.value = true
    error.value = ''
    notice.value = ''
    try {
      await deletePin(currentMapId, pinId)
      if (mapId() !== currentMapId) return
      close()
      notice.value = '图钉已删除。'
      onDeleted(pinId)
    } catch (cause) {
      if (mapId() !== currentMapId) return
      error.value = errorMessage(cause)
      auth.handleAuthError(cause)
    } finally {
      busy.value = false
    }
  }

  return { phase, target, coordinates, busy, error, notice, startPlacing, placeAt, startEditing, moveTo, cancel, save, remove }
}
