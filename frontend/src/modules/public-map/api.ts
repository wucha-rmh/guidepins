import type { MapPin, PublicMap, PublicMapDetail } from './types'

/** 资源不存在或地址无效；界面据此给出返回入口，而不是“重试”。 */
export class NotFoundError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'NotFoundError'
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0
}

function isPublicMap(value: unknown): value is PublicMap {
  return isRecord(value) && isNonEmptyString(value.id) &&
    isNonEmptyString(value.cityId) && isNonEmptyString(value.name)
}

function isMapPin(value: unknown): value is MapPin {
  if (!isRecord(value) || !isNonEmptyString(value.id) || !isNonEmptyString(value.name)) return false
  // 说明允许为空，但出现时必须是字符串，避免把模板内容直接注入界面。
  if (value.description !== null && typeof value.description !== 'string') return false
  const { longitude, latitude } = value
  return typeof longitude === 'number' && typeof latitude === 'number' &&
    Number.isFinite(longitude) && Number.isFinite(latitude) &&
    longitude >= -180 && longitude <= 180 && latitude >= -90 && latitude <= 90
}

function isPublicMapDetail(value: unknown): value is PublicMapDetail {
  if (!isRecord(value)) return false
  const { pins } = value
  return isPublicMap(value) && Array.isArray(pins) && pins.every(isMapPin)
}

/** GET /api/cities/{cityId}/maps：该地市下站点发布的公共地图。 */
export async function listPublicMaps(cityId: string, signal: AbortSignal): Promise<PublicMap[]> {
  const response = await fetch(`/api/cities/${encodeURIComponent(cityId)}/maps`, {
    signal, headers: { Accept: 'application/json' },
  })
  if (response.status === 404) throw new NotFoundError('没有找到这个地市，可能已下线。')
  if (!response.ok) throw new Error(`公共地图列表请求失败（HTTP ${response.status}），请稍后重试。`)
  const data: unknown = await response.json()
  if (!Array.isArray(data) || !data.every(isPublicMap)) {
    throw new Error('公共地图数据格式有误，请稍后重试。')
  }
  return data
}

/** GET /api/maps/{mapId}：地图详情及其全部图钉。 */
export async function getPublicMapDetail(mapId: string, signal: AbortSignal): Promise<PublicMapDetail> {
  const response = await fetch(`/api/maps/${encodeURIComponent(mapId)}`, {
    signal, headers: { Accept: 'application/json' },
  })
  if (response.status === 404) throw new NotFoundError('这张公共地图不存在或已下线。')
  // 地址中的 ID 不是合法 UUID 时后端返回 400，属于地址无效而非服务故障。
  if (response.status === 400) throw new NotFoundError('地图地址无效，请从公共地图列表重新打开。')
  if (!response.ok) throw new Error(`地图详情请求失败（HTTP ${response.status}），请稍后重试。`)
  const data: unknown = await response.json()
  if (!isPublicMapDetail(data)) throw new Error('地图详情数据格式有误，请稍后重试。')
  return data
}

/** 把请求异常转成界面提示；超时、服务不可达与其他错误分别描述。 */
export function describeRequestError(cause: unknown, timedOut: boolean, subject: string): string {
  if (timedOut) return `${subject}加载超时，请检查网络后重试。`
  if (cause instanceof NotFoundError) return cause.message
  if (cause instanceof TypeError) return '无法连接服务，请检查网络或服务是否已启动。'
  if (cause instanceof Error) return cause.message
  return `${subject}加载失败，请稍后重试。`
}
