import type { LngLatLike } from 'maplibre-gl'
import type { City } from '../../types/city'

/** 各城市的主城区观察点：进入地市时先看街区，避免全市视野只看到水系与零散地名。 */
const urbanViews: Record<string, { center: [number, number]; zoom: number }> = {
  // 南京：新街口一带，z14.2 起建筑与道路名数据都已存在。
  '320100': { center: [118.79, 32.055], zoom: 14.2 },
  // 其余 12 城以各市主城为观察点，级别略低，先看清水系与街网。
  '320200': { center: [120.3119, 31.4912], zoom: 13.4 },
  '320300': { center: [117.1848, 34.2618], zoom: 13.2 },
  '320400': { center: [119.9741, 31.8112], zoom: 13.2 },
  '320500': { center: [120.6199, 31.3116], zoom: 13.2 },
  '320600': { center: [120.8943, 31.9803], zoom: 13.2 },
  '320700': { center: [119.2216, 34.5967], zoom: 13.2 },
  '320800': { center: [119.0153, 33.6104], zoom: 13.2 },
  '320900': { center: [120.1395, 33.3477], zoom: 13.2 },
  '321000': { center: [119.4129, 32.3942], zoom: 13.2 },
  '321100': { center: [119.425, 32.1895], zoom: 13.2 },
  '321200': { center: [119.9229, 32.4558], zoom: 13.2 },
  '321300': { center: [118.2752, 33.9631], zoom: 13.2 },
}

/** 没有预置观察点的城市，退回行政区范围中心，用较低级别先看轮廓。 */
const fallbackZoom = 12.6

export function urbanViewFor(city: City): { center: LngLatLike; zoom: number } {
  const preset = urbanViews[city.code]
  if (preset) return preset
  const { west, south, east, north } = city.bounds
  return { center: [(west + east) / 2, (south + north) / 2], zoom: fallbackZoom }
}
