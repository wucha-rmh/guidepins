import type { City } from './types'

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isCity(value: unknown): value is City {
  if (!isRecord(value) || !isRecord(value.bounds)) return false
  if (!['id', 'code', 'name', 'basemapPath'].every((key) =>
    typeof value[key] === 'string' && value[key].trim().length > 0)) return false
  const { west, south, east, north } = value.bounds
  return typeof west === 'number' && typeof east === 'number' &&
    typeof south === 'number' && typeof north === 'number' &&
    [west, south, east, north].every(Number.isFinite) &&
    west >= -180 && east <= 180 && south >= -90 && north <= 90 && west < east && south < north
}

/** 只负责目录请求与数据契约校验；加载、超时、取消状态由 useCities 管理。 */
export async function listCities(signal: AbortSignal): Promise<City[]> {
  const response = await fetch('/api/cities', { signal, headers: { Accept: 'application/json' } })
  if (!response.ok) throw new Error(`地市列表请求失败（HTTP ${response.status}），请稍后重试。`)
  const data: unknown = await response.json()
  if (!Array.isArray(data) || !data.every(isCity)) {
    throw new Error('地市数据格式有误，请稍后重试。')
  }
  return data
}
