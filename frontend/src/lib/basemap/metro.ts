/**
 * 地铁图层的可用性：制包时只有部分地市把地铁线和站点写进了 pmtiles。
 * 这里按地市代码白名单控制样式里是否声明 metro_* 图层——
 * 缺图层的城市若照旧声明，MapLibre 会在每次查询时报错。
 */
const metroCities = new Set(['320100', '320200', '320300', '320400', '320500', '320600', '321100'])

export function hasMetroLayers(code: string): boolean {
  return metroCities.has(code)
}
