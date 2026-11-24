import { readApiResponse } from './apiResponse'

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'
  body?: unknown
  signal?: AbortSignal
}

/** 同源 JSON 请求；整个操作（含 CSRF）共用超时，写操作不自动重放。 */
export async function apiRequest(path: string, options: RequestOptions = {}): Promise<unknown> {
  const controller = new AbortController()
  const abort = () => controller.abort()
  let timedOut = false
  const timer = setTimeout(() => { timedOut = true; controller.abort() }, 15000)
  options.signal?.addEventListener('abort', abort, { once: true })
  if (options.signal?.aborted) controller.abort()
  try {
    const headers: Record<string, string> = { Accept: 'application/json' }
    if (options.method && options.method !== 'GET') {
      // 每次写入前获取掩码令牌，登录／退出后不复用旧令牌，也不读取 HttpOnly Cookie。
      const csrfResponse = await fetch('/api/auth/csrf', { credentials: 'same-origin', signal: controller.signal })
      const csrf = await readApiResponse(csrfResponse, '获取请求令牌失败，请重试。')
      if (typeof csrf !== 'object' || csrf === null || !('headerName' in csrf) || !('token' in csrf) ||
        typeof csrf.headerName !== 'string' || !/^X-[A-Za-z0-9-]+$/i.test(csrf.headerName) ||
        typeof csrf.token !== 'string' || !csrf.token) throw new Error('请求令牌格式有误，请重试。')
      headers[csrf.headerName] = csrf.token
      if (options.body !== undefined) headers['Content-Type'] = 'application/json'
    }
    const response = await fetch(path, {
      method: options.method ?? 'GET', credentials: 'same-origin', headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: controller.signal,
    })
    return await readApiResponse(response, `请求失败（HTTP ${response.status}），请重试。`)
  } catch (cause) {
    if (timedOut) throw new Error('请求超时，请刷新确认操作结果后再重试。')
    if (cause instanceof TypeError) throw new Error('无法连接服务，请检查网络或服务是否已启动。')
    throw cause
  } finally {
    clearTimeout(timer)
    options.signal?.removeEventListener('abort', abort)
  }
}

export function errorMessage(cause: unknown): string {
  return cause instanceof Error ? cause.message : '操作失败，请稍后重试。'
}
