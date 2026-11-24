import { watch } from 'vue'
import { describeRequestError, getPublicMapDetail } from './api'
import { useRemoteResource } from './useRemoteResource'

/**
 * 读取地址中指定地图的详情与图钉。
 * 未打开地图时保持 idle；切换地图或重试时取消旧请求，避免上一张地图的内容闪现。
 */
export function usePublicMapDetail(mapId: () => string, viewer: () => string = () => '') {
  const resource = useRemoteResource(
    (signal) => getPublicMapDetail(mapId(), signal),
    (cause, timedOut) => describeRequestError(cause, timedOut, '地图详情'),
  )
  // 身份变化时同步丢弃详情与在途请求，私人地图不可沿用上一账号的结果。
  watch([mapId, viewer], ([value]) => {
    if (value) void resource.reload()
    else resource.reset()
  }, { immediate: true, flush: 'sync' })
  return { detail: resource.data, ...resource }
}
