import type { Feature, FeatureCollection, MultiLineString, Polygon, Position } from 'geojson'
import type { CityBoundary } from '../city/boundary'
import type { City } from '../city/types'

/**
 * 市界遮罩与描边：外框挖去城市各环，得到「城市以外」的面，
 * 描边取全部环线。两者合起来就是原先静态 nanjing-boundary.json 的语义，
 * 现在改为按所选地市实时生成，13 座城市共用同一套逻辑。
 */
export interface CityBoundaryLayers {
  mask: Feature<Polygon>
  outline: Feature<MultiLineString>
}

/** 外框留白按城市跨度取比例：低缩放时遮罩要盖住视口，高缩放时又不至于压住邻市。 */
const paddingRatio = 0.6
const minPadding = 0.4

function ringArea(ring: Position[]): number {
  let area = 0
  for (let index = 0; index < ring.length - 1; index += 1) {
    const [x1, y1] = ring[index]
    const [x2, y2] = ring[index + 1]
    area += x1 * y2 - x2 * y1
  }
  return area / 2
}

/** GeoJSON 要求外环逆时针、内环顺时针；把环统一到指定绕向才能让挖洞生效。 */
function orient(ring: Position[], clockwise: boolean): Position[] {
  // 本函数用鞋带公式，逆时针为正；面积小于 0 才是顺时针。
  const isClockwise = ringArea(ring) < 0
  return isClockwise === clockwise ? ring : [...ring].reverse()
}

export function buildBoundaryLayers(boundary: CityBoundary, city: City): CityBoundaryLayers | null {
  const rings: Position[][] = []
  for (const feature of boundary.features) {
    const polygons = feature.geometry.type === 'Polygon'
      ? [feature.geometry.coordinates]
      : feature.geometry.coordinates
    for (const polygon of polygons) {
      for (const ring of polygon) {
        if (ring.length >= 4) rings.push(ring)
      }
    }
  }
  if (rings.length === 0) return null

  const { west, south, east, north } = city.bounds
  const padX = Math.max((east - west) * paddingRatio, minPadding)
  const padY = Math.max((north - south) * paddingRatio, minPadding)
  const left = west - padX
  const right = east + padX
  const bottom = south - padY
  const top = north + padY
  const box: Position[] = [[left, bottom], [right, bottom], [right, top], [left, top], [left, bottom]]

  return {
    mask: {
      type: 'Feature',
      properties: { kind: 'outside' },
      geometry: {
        type: 'Polygon',
        // 外框逆时针，城市所有环作为洞；内环（飞地）同样成为洞，正好露出不属于本市的地块。
        coordinates: [orient(box, false), ...rings.map((ring) => orient(ring, true))],
      },
    },
    outline: {
      type: 'Feature',
      properties: { kind: 'boundary' },
      geometry: { type: 'MultiLineString', coordinates: rings },
    },
  }
}

export function boundaryCollection(layers: CityBoundaryLayers): FeatureCollection {
  return { type: 'FeatureCollection', features: [layers.mask, layers.outline] }
}
