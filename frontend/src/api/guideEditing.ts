import { apiRequest } from './client'
import { isGuideSummary } from './publicMap'
import type { GuideDetail, GuidePinLink, GuideValues } from '../types/guideEditing'

// 攻略写入接口与前端校验。上限与后端 GuideBody、数据库 CHECK 保持一致，先拦一次再请求。
const TITLE_LIMIT = 120
const BODY_LIMIT = 5000
const URL_LIMIT = 2048
const URL_PATTERN = /^https?:\/\/\S+$/

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isPinLink(value: unknown): value is GuidePinLink {
  return isRecord(value) && typeof value.pinId === 'string' && value.pinId.trim().length > 0 &&
    typeof value.position === 'number' && Number.isInteger(value.position) && value.position >= 0
}

function isGuideDetail(value: unknown): value is GuideDetail {
  if (!isGuideSummary(value)) return false
  const record = value as unknown as Record<string, unknown>
  return typeof record.mapId === 'string' && typeof record.origin === 'string' &&
    typeof record.body === 'string' && typeof record.createdAt === 'string' &&
    typeof record.updatedAt === 'string' && Array.isArray(record.pins) && record.pins.every(isPinLink)
}

function guidesPath(mapId: string): string {
  return `/api/maps/${encodeURIComponent(mapId)}/guides`
}

function guidePath(mapId: string, guideId: string): string {
  return `${guidesPath(mapId)}/${encodeURIComponent(guideId)}`
}

function guideRecord(value: unknown): GuideDetail {
  if (!isGuideDetail(value)) throw new Error('攻略数据格式有误，请刷新确认。')
  return value
}

/** GET /api/maps/{mapId}/guides：当前地图的攻略列表，含各自关联图钉。 */
export async function listGuides(mapId: string, signal: AbortSignal): Promise<GuideDetail[]> {
  const data = await apiRequest(guidesPath(mapId), { signal })
  if (!Array.isArray(data) || !data.every(isGuideDetail)) throw new Error('攻略列表数据格式有误，请稍后重试。')
  return data
}

/** POST /api/maps/{mapId}/guides：新建一篇还没关联图钉的攻略。 */
export async function createGuide(mapId: string, values: GuideValues): Promise<GuideDetail> {
  const data = await apiRequest(guidesPath(mapId), {
    method: 'POST',
    body: { title: values.title, body: values.body, sourceUrl: values.sourceUrl },
  })
  return guideRecord(data)
}

/** PATCH /api/maps/{mapId}/guides/{guideId}：整体替换标题、正文与来源链接。 */
export async function updateGuide(mapId: string, guideId: string, values: GuideValues): Promise<GuideDetail> {
  const data = await apiRequest(guidePath(mapId, guideId), {
    method: 'PATCH',
    body: { title: values.title, body: values.body, sourceUrl: values.sourceUrl },
  })
  return guideRecord(data)
}

/** DELETE /api/maps/{mapId}/guides/{guideId}：删除攻略本身，关联行由数据库级联清理。 */
export async function deleteGuide(mapId: string, guideId: string): Promise<void> {
  await apiRequest(guidePath(mapId, guideId), { method: 'DELETE' })
}

/** PUT /api/maps/{mapId}/guides/{guideId}/pins：整体重设关联，数组顺序即站序。 */
export async function setGuidePins(mapId: string, guideId: string, pinIds: string[]): Promise<GuideDetail> {
  const data = await apiRequest(`${guidePath(mapId, guideId)}/pins`, { method: 'PUT', body: { pinIds } })
  return guideRecord(data)
}

export function validateGuideTitle(title: string): string {
  const value = title.trim()
  if (!value) throw new Error('攻略标题不能为空。')
  if (value.length > TITLE_LIMIT) throw new Error(`攻略标题不能超过 ${TITLE_LIMIT} 个字符。`)
  return value
}

export function validateGuideBody(body: string): string {
  const value = body.trim()
  if (!value) throw new Error('攻略正文不能为空。')
  if (value.length > BODY_LIMIT) throw new Error(`攻略正文不能超过 ${BODY_LIMIT} 个字符。`)
  return value
}

/** 来源链接允许留空；留空时提交 null，与后端「null 表示没有出处链接」一致。 */
export function validateGuideSourceUrl(sourceUrl: string): string | null {
  const value = sourceUrl.trim()
  if (!value) return null
  if (value.length > URL_LIMIT) throw new Error(`来源链接不能超过 ${URL_LIMIT} 个字符。`)
  if (!URL_PATTERN.test(value)) throw new Error('来源链接需以 http:// 或 https:// 开头，且不含空格。')
  return value
}

/** 表单一次性校验，返回可直接提交的载荷。 */
export function validateGuideValues(title: string, body: string, sourceUrl: string): GuideValues {
  return {
    title: validateGuideTitle(title),
    body: validateGuideBody(body),
    sourceUrl: validateGuideSourceUrl(sourceUrl),
  }
}
