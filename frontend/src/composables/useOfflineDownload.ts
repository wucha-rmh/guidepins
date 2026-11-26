import { onBeforeUnmount, ref, watch } from 'vue'
import { fetchOfflineMap } from '../api/offline'
import { errorMessage } from '../api/client'
import { useAuth } from './useAuth'

/** 下载状态与地图、身份绑定，离开或退出后不保存迟到的私人地图文件。 */
export function useOfflineDownload(mapId: () => string) {
  const auth = useAuth()
  const busy = ref(false), error = ref(''), notice = ref('')
  let active: AbortController | undefined
  function cancel() {
    active?.abort(); active = undefined; busy.value = false; error.value = ''; notice.value = ''
  }
  watch([mapId, () => auth.user.value?.id, () => auth.state.value], cancel, { flush: 'sync' })
  onBeforeUnmount(cancel)
  async function download() {
    if (busy.value || !mapId() || auth.state.value !== 'ready') return
    const id = mapId(), controller = new AbortController()
    active = controller; busy.value = true; error.value = ''; notice.value = ''
    let timedOut = false
    const timer = setTimeout(() => { timedOut = true; controller.abort() }, 120000)
    try {
      const file = await fetchOfflineMap(id, controller.signal)
      if (active !== controller || controller.signal.aborted) return
      const url = URL.createObjectURL(file)
      const anchor = document.createElement('a'); anchor.href = url; anchor.download = `拾点地图-南京-${id}.html`
      document.body.append(anchor); anchor.click(); anchor.remove()
      setTimeout(() => URL.revokeObjectURL(url), 60000)
      notice.value = '文件已交给浏览器保存。保存后双击 HTML 文件，即可离线查看。'
    } catch (cause) {
      if (active !== controller) return
      auth.handleAuthError(cause)
      // 会话过期可能同步触发 cancel，不再向已切换的界面回填提示。
      if (active !== controller) return
      error.value = timedOut ? '生成或下载超时，请稍后重新下载。' : errorMessage(cause)
    } finally {
      clearTimeout(timer)
      if (active === controller) { active = undefined; busy.value = false }
    }
  }
  return { busy, error, notice, download }
}
