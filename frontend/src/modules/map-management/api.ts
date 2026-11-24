import { apiRequest } from '../../shared/apiRequest'
import type { ManagedMap } from './types'

function isManagedMap(value: unknown): value is ManagedMap {
  if (typeof value !== 'object' || value === null) return false
  const item = value as Partial<ManagedMap>
  return [item.id, item.cityId, item.name, item.ownerId].every((v) => typeof v === 'string' && v.trim().length > 0) &&
    (item.visibility === 'private' || item.visibility === 'public') &&
    typeof item.createdAt === 'string' && Number.isFinite(Date.parse(item.createdAt)) &&
    typeof item.updatedAt === 'string' && Number.isFinite(Date.parse(item.updatedAt))
}

function mapRecord(value: unknown): ManagedMap {
  if (!isManagedMap(value)) throw new Error('地图管理数据格式有误，请刷新确认。')
  return value
}

export async function listOwnedMaps(cityId: string, ownerId: string, signal: AbortSignal): Promise<ManagedMap[]> {
  const data = await apiRequest(`/api/me/maps?cityId=${encodeURIComponent(cityId)}`, { signal })
  if (!Array.isArray(data) || !data.every(isManagedMap) ||
    !data.every((map) => map.cityId === cityId && map.ownerId === ownerId)) {
    throw new Error('我的地图数据格式有误，请稍后重试。')
  }
  return data
}

export async function createMap(cityId: string, name: string) {
  // 创建只提交当前需要的字段，公开状态使用后端的私人默认值。
  return mapRecord(await apiRequest(`/api/cities/${encodeURIComponent(cityId)}/maps`, { method: 'POST', body: { name } }))
}

export async function renameMap(id: string, name: string) {
  return mapRecord(await apiRequest(`/api/maps/${encodeURIComponent(id)}/name`, { method: 'PATCH', body: { name } }))
}

export async function changeVisibility(id: string, visibility: ManagedMap['visibility']) {
  return mapRecord(await apiRequest(`/api/maps/${encodeURIComponent(id)}/visibility`, { method: 'PATCH', body: { visibility } }))
}

/** 对齐后端 UTF-16 长度约定，提交前去掉名称首尾空白。 */
export function validateMapName(name: string): string {
  const value = name.trim()
  if (!value || value.length > 120) throw new Error('地图名称需为 1 至 120 个字符。')
  return value
}

export function formatMapTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
