import type { LngLatLike } from 'maplibre-gl'
import type { City } from '../city/types'

/** 各城市的主城区观察点：进入地市时先看街区，避免全市视野只看到水系与零散地名。 */
const urbanViews: Record<string, { center: [number, number]; zoom: number }> = {
  // 南京：新街口一带，z14.2 起建筑与道路名数据都已存在。
  '320100': { center: [118.79, 32.055], zoom: 14.2 },
}

/** 没有预置观察点的城市，退回行政区范围中心，用较低级别先看轮廓。 */
const fallbackZoom = 13

export function urbanViewFor(city: City): { center: LngLatLike; zoom: number } {
  const preset = urbanViews[city.code]
  if (preset) return preset
  const { west, south, east, north } = city.bounds
  return { center: [(west + east) / 2, (south + north) / 2], zoom: fallbackZoom }
}
