import { computed, watch } from 'vue'
import { describeRequestError, listPublicMaps } from './api'
import { useRemoteResource } from './useRemoteResource'

/**
 * 读取当前地市的公共地图列表。
 * 地市变化时重新请求；地市为空（目录尚未就绪或地址无效）时回到 idle，不发出无意义的请求。
 */
export function usePublicMaps(cityId: () => string) {
  const resource = useRemoteResource(
    (signal) => listPublicMaps(cityId(), signal),
    (cause, timedOut) => describeRequestError(cause, timedOut, '公共地图列表'),
  )
  watch(cityId, (value) => {
    if (value) void resource.reload()
    else resource.reset()
  }, { immediate: true })
  const maps = computed(() => resource.data.value ?? [])
  return { maps, ...resource }
}
