import type { FeatureCollection, MultiPolygon, Polygon } from 'geojson'

/** 市界只可能是多边形：单块城市用 Polygon，带飞地或岛屿的城市用 MultiPolygon。 */
export type CityBoundaryGeometry = Polygon | MultiPolygon
export type CityBoundary = FeatureCollection<CityBoundaryGeometry>

function isPosition(value: unknown): boolean {
  return Array.isArray(value) && value.length >= 2 &&
    typeof value[0] === 'number' && typeof value[1] === 'number' &&
    Number.isFinite(value[0]) && Number.isFinite(value[1])
}

function isRing(value: unknown): boolean {
  // 闭合环至少 4 个点，且首尾相同；这里只校验形状与数值，具体闭合由制包脚本保证。
  return Array.isArray(value) && value.length >= 4 && value.every(isPosition)
}

function isPolygonCoordinates(value: unknown): boolean {
  return Array.isArray(value) && value.length >= 1 && value.every(isRing)
}

function isGeometry(value: unknown): value is CityBoundaryGeometry {
  if (typeof value !== 'object' || value === null) return false
  const geometry = value as { type?: unknown; coordinates?: unknown }
  if (geometry.type === 'Polygon') return isPolygonCoordinates(geometry.coordinates)
  if (geometry.type === 'MultiPolygon') {
    return Array.isArray(geometry.coordinates) && geometry.coordinates.length >= 1 &&
      geometry.coordinates.every(isPolygonCoordinates)
  }
  return false
}

/** 结构异常的市界宁可不用（没有遮罩），也不让错误的几何把整幅地图画坏。 */
export function isCityBoundary(value: unknown): value is CityBoundary {
  if (typeof value !== 'object' || value === null) return false
  const collection = value as { type?: unknown; features?: unknown }
  if (collection.type !== 'FeatureCollection' || !Array.isArray(collection.features)) return false
  return collection.features.every((feature) =>
    typeof feature === 'object' && feature !== null && isGeometry((feature as { geometry?: unknown }).geometry))
}

/** 读取某地市的市界 GeoJSON；接口对访客开放，返回体不走统一响应外壳。 */
export async function fetchCityBoundary(code: string, signal: AbortSignal): Promise<CityBoundary> {
  const response = await fetch(`/api/cities/${encodeURIComponent(code)}/boundary`, {
    signal,
    headers: { Accept: 'application/geo+json' },
  })
  if (!response.ok) throw new Error(`市界数据请求失败（HTTP ${response.status}），请稍后重试。`)
  const data: unknown = await response.json()
  if (!isCityBoundary(data)) throw new Error('市界数据格式有误，请稍后重试。')
  return data
}
