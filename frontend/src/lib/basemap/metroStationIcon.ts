import type { Map } from 'maplibre-gl'
import iconUrl from '../../assets/metro-station.png?inline'

export const METRO_STATION_ICON = 'metro-station'
let decoded: Promise<HTMLImageElement> | undefined

/** 已确认的蓝灰车头图标内联为小纹理；网站和离线查看器共用，不请求外部图片。 */
function image() {
  if (!decoded) {
    const icon = new Image()
    icon.src = iconUrl
    decoded = icon.decode().then(() => icon).catch(cause => { decoded = undefined; throw cause })
  }
  return decoded
}

/** MapLibre 等待异步解码后再绘制，避免首屏缺图；销毁后不向旧地图回填。 */
export function registerMetroStationIcon(map: Map) {
  let active = true
  map.setMissingStyleImageResolver(async id => {
    if (id !== METRO_STATION_ICON) return
    const icon = await image()
    if (active && !map.hasImage(id)) map.addImage(id, icon, { pixelRatio: 2 })
  })
  return () => { active = false; map.setMissingStyleImageResolver(null) }
}
