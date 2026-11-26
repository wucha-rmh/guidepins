import { readApiResponse } from './response'

/** 文件下载单独处理二进制响应，业务错误仍复用统一响应解析；不自动重试。 */
export async function fetchOfflineMap(mapId: string, signal: AbortSignal): Promise<Blob> {
  const response = await fetch(`/api/maps/${encodeURIComponent(mapId)}/offline`, {
    credentials: 'same-origin', signal, headers: { Accept: 'text/html' },
  })
  if (!response.ok) await readApiResponse(response, `下载失败（HTTP ${response.status}），请稍后重试。`)
  if (!response.ok || !response.headers.get('Content-Type')?.startsWith('text/html')) {
    throw new Error('下载文件格式有误，请稍后重试。')
  }
  const file = await response.blob()
  if (file.size === 0) throw new Error('下载文件为空，请稍后重试。')
  return file
}
