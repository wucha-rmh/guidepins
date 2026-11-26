/** 保留 HTTP 状态，业务模块据此区分会话失效、无权限与数据冲突。 */
export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

/** 统一解析后端响应外层；各功能模块继续校验 data 中自己的业务数据。 */
export async function readApiResponse(response: Response, failureMessage: string): Promise<unknown> {
  let body: unknown
  try {
    body = await response.json()
  } catch (cause) {
    // 保留中止和网络异常，让现有加载状态继续区分超时、取消与连接失败。
    if (!(cause instanceof SyntaxError)) throw cause
    throw new ApiError(response.status, failureMessage)
  }
  if (typeof body !== 'object' || body === null || !('code' in body) || !('message' in body) || !('data' in body) ||
    !Number.isInteger(body.code) || body.code !== response.status ||
    typeof body.message !== 'string' || body.message.trim().length === 0) {
    throw new ApiError(response.status, '接口响应格式有误，请稍后重试。')
  }
  if (!response.ok) {
    if (body.data !== null) throw new ApiError(response.status, '接口响应格式有误，请稍后重试。')
    throw new ApiError(response.status, body.message)
  }
  return body.data
}
