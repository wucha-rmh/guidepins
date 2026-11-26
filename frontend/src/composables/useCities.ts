import { onMounted, onScopeDispose, ref } from 'vue'
import { listCities } from '../api/city'
import type { City } from '../types/city'

/** 两个页面都需要恢复目录；每次挂载重新读取，不缓存过期的地市信息。 */
export function useCities() {
  const cities = ref<City[]>([])
  const state = ref<'loading' | 'ready' | 'error'>('loading')
  const error = ref('')
  let activeRequest: AbortController | undefined
  let activeTimeout: ReturnType<typeof setTimeout> | undefined

  async function reload() {
    activeRequest?.abort()
    clearTimeout(activeTimeout)
    const request = new AbortController()
    activeRequest = request
    let timedOut = false
    activeTimeout = setTimeout(() => { timedOut = true; request.abort() }, 15000)
    const timeout = activeTimeout
    cities.value = []
    state.value = 'loading'
    error.value = ''
    try {
      const result = await listCities(request.signal)
      if (activeRequest !== request) return
      cities.value = result
      state.value = 'ready'
    } catch (cause) {
      // 页面离开或新请求取代旧请求时，不让旧结果覆盖当前页面。
      if (activeRequest !== request) return
      error.value = timedOut ? '地市列表加载超时，请检查网络后重试。' :
        cause instanceof TypeError ? '无法连接服务，请检查网络或服务是否已启动。' :
          cause instanceof Error ? cause.message : '地市列表加载失败，请稍后重试。'
      state.value = 'error'
    } finally {
      clearTimeout(timeout)
    }
  }

  onMounted(reload)
  onScopeDispose(() => {
    const request = activeRequest
    activeRequest = undefined
    request?.abort()
    clearTimeout(activeTimeout)
  })
  return { cities, state, error, reload }
}
