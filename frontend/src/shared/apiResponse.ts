/** 统一解析后端响应外层；各功能模块继续校验 data 中自己的业务数据。 */
export async function readApiResponse(response: Response, failureMessage: string): Promise<unknown> {
  let body: unknown
  try {
    body = await response.json()
  } catch (cause) {
    // 保留中止和网络异常，让现有加载状态继续区分超时、取消与连接失败。
    if (!(cause instanceof SyntaxError)) throw cause
    throw new Error(failureMessage)
  }
  if (typeof body !== 'object' || body === null || !('code' in body) || !('message' in body) || !('data' in body) ||
    !Number.isInteger(body.code) || body.code !== response.status ||
    typeof body.message !== 'string' || body.message.trim().length === 0) {
    throw new Error('接口响应格式有误，请稍后重试。')
  }
  if (!response.ok) throw new Error(body.message)
  return body.data
}
