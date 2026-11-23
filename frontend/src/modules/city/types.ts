/** 地市目录契约；坐标统一采用 WGS84，经度在前。 */
export interface City {
  id: string
  code: string
  name: string
  /** 供浏览器读取的 PMTiles 地址。 */
  basemapPath: string
  bounds: CityBounds
}

export interface CityBounds {
  west: number
  south: number
  east: number
  north: number
}
