import { apiRequest } from './client'
import { isMapPin } from './publicMap'
import type { MapPin } from '../types/publicMap'
import type { PinCoordinates } from '../types/pinEditing'

// 图钉写入接口与前端校验。长度上限与后端 PinBody 保持一致，先拦一次再请求，避免不必要的往返。
const NAME_LIMIT = 120
const DESCRIPTION_LIMIT = 2000

function pinRecord(value: unknown): MapPin {
  if (!isMapPin(value)) throw new Error('图钉数据格式有误，请刷新确认。')
  return value
}

/** POST /api/maps/{mapId}/pins：在地图上新增一枚图钉。 */
export async function createPin(mapId: string, name: string, description: string | null, coordinates: PinCoordinates) {
  const data = await apiRequest(`/api/maps/${encodeURIComponent(mapId)}/pins`, {
    method: 'POST',
    body: { name, description, longitude: coordinates.longitude, latitude: coordinates.latitude },
  })
  return pinRecord(data)
}

/** PATCH /api/maps/{mapId}/pins/{pinId}：整体替换名称、说明与坐标。 */
export async function updatePin(mapId: string, pinId: string, name: string, description: string | null,
  coordinates: PinCoordinates) {
  const data = await apiRequest(
    `/api/maps/${encodeURIComponent(mapId)}/pins/${encodeURIComponent(pinId)}`, {
      method: 'PATCH',
      body: { name, description, longitude: coordinates.longitude, latitude: coordinates.latitude },
    })
  return pinRecord(data)
}

/** DELETE /api/maps/{mapId}/pins/{pinId}：成功后统一外层 data 为 null，无需解析。 */
export async function deletePin(mapId: string, pinId: string): Promise<void> {
  await apiRequest(`/api/maps/${encodeURIComponent(mapId)}/pins/${encodeURIComponent(pinId)}`, { method: 'DELETE' })
}

export function validatePinName(name: string): string {
  const value = name.trim()
  if (!value) throw new Error('图钉名称不能为空。')
  if (value.length > NAME_LIMIT) throw new Error(`图钉名称不能超过 ${NAME_LIMIT} 个字符。`)
  return value
}

/** 说明允许留空；留空时提交 null，与后端「null 表示清空说明」一致。 */
export function validatePinDescription(description: string): string | null {
  const value = description.trim()
  if (!value) return null
  if (value.length > DESCRIPTION_LIMIT) throw new Error(`图钉说明不能超过 ${DESCRIPTION_LIMIT} 个字符。`)
  return value
}

export function validatePinCoordinates(longitude: number, latitude: number): PinCoordinates {
  if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180) throw new Error('经度需在 -180 至 180 之间。')
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) throw new Error('纬度需在 -90 至 90 之间。')
  return { longitude, latitude }
}

/** 拖动时把坐标收进合法范围并保留 6 位小数（约 0.1 米），避免提交浮点噪声。 */
export function normalizeCoordinates({ longitude, latitude }: PinCoordinates): PinCoordinates {
  const round = (value: number) => Math.round(value * 1e6) / 1e6
  return {
    longitude: round(Math.min(180, Math.max(-180, longitude))),
    latitude: round(Math.min(90, Math.max(-90, latitude))),
  }
}
