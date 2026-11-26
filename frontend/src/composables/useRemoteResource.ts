import { onScopeDispose, ref, shallowRef, type Ref, type ShallowRef } from 'vue'
import { NotFoundError } from '../api/publicMap'

/** 一次远端读取的界面状态；idle 表示当前没有要读取的资源。 */
export type RemoteState = 'idle' | 'loading' | 'ready' | 'error'

export interface RemoteResource<T> {
  data: ShallowRef<T | null>
  state: Ref<RemoteState>
  error: Ref<string>
  /** 后端明确表示资源不存在；界面据此给出返回入口而不是重试。 */
  notFound: Ref<boolean>
  reload: () => Promise<void>
  reset: () => void
}

const REQUEST_TIMEOUT = 15000

/**
 * 公共地图列表与详情共用的加载状态机：统一 15 秒超时、取消被替代或已卸载的请求，
 * 并把异常交给调用方转换成提示文案。请求与数据校验本身留在 api.ts。
 */
export function useRemoteResource<T>(
  fetchResource: (signal: AbortSignal) => Promise<T>,
  describeError: (cause: unknown, timedOut: boolean) => string,
): RemoteResource<T> {
  const data = shallowRef<T | null>(null)
  const state = ref<RemoteState>('idle')
  const error = ref('')
  const notFound = ref(false)
  let activeRequest: AbortController | undefined
  let activeTimeout: ReturnType<typeof setTimeout> | undefined

  function reset() {
    activeRequest?.abort()
    activeRequest = undefined
    clearTimeout(activeTimeout)
    data.value = null
    state.value = 'idle'
    error.value = ''
    notFound.value = false
  }

  async function reload() {
    activeRequest?.abort()
    clearTimeout(activeTimeout)
    const request = new AbortController()
    activeRequest = request
    let timedOut = false
    activeTimeout = setTimeout(() => { timedOut = true; request.abort() }, REQUEST_TIMEOUT)
    const timeout = activeTimeout
    data.value = null
    state.value = 'loading'
    error.value = ''
    notFound.value = false
    try {
      const result = await fetchResource(request.signal)
      if (activeRequest !== request) return
      data.value = result
      state.value = 'ready'
    } catch (cause) {
      // 页面离开或新请求取代旧请求时，不让旧结果覆盖当前页面。
      if (activeRequest !== request) return
      error.value = describeError(cause, timedOut)
      notFound.value = !timedOut && cause instanceof NotFoundError
      state.value = 'error'
    } finally {
      clearTimeout(timeout)
    }
  }

  onScopeDispose(() => {
    const request = activeRequest
    activeRequest = undefined
    request?.abort()
    clearTimeout(activeTimeout)
  })

  return { data, state, error, notFound, reload, reset }
}
