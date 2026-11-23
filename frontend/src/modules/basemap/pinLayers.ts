import { Popup, type GeoJSONSource, type Map, type MapMouseEvent } from 'maplibre-gl'
import type { Feature, FeatureCollection, Point } from 'geojson'
import type { MapPin } from '../public-map/types'

// 图钉是业务数据的视图：调用方每次传入完整列表和选中 ID，这里只负责转换成
// GeoJSON 并更新图层，不额外保存业务状态。
const SOURCE_ID = 'public-map-pins'
const RING_LAYER = 'public-map-pins-ring'
const HALO_LAYER = 'public-map-pins-halo'
const DOT_LAYER = 'public-map-pins-dot'
// 点击判定使用三个可见图层，避免为命中区域再增加一层透明圆。
const HIT_LAYERS = [RING_LAYER, HALO_LAYER, DOT_LAYER]
const PIN_COLOR = '#176c60'
const PIN_SELECTED_COLOR = '#b85a2b'

interface PinProperties {
  id: string
  selected: boolean
}

export interface PinOverlay {
  /** 地图样式加载完成后调用；重复调用安全。 */
  install: () => void
  update: (pins: MapPin[], selectedId: string) => void
  destroy: () => void
}

function toFeatures(pins: MapPin[], selectedId: string): FeatureCollection<Point, PinProperties> {
  return {
    type: 'FeatureCollection',
    features: pins.map((pin): Feature<Point, PinProperties> => ({
      type: 'Feature',
      geometry: { type: 'Point', coordinates: [pin.longitude, pin.latitude] },
      properties: { id: pin.id, selected: pin.id === selectedId },
    })),
  }
}

/** 说明气泡用 DOM 文本节点拼装，站点的说明内容不会被当作 HTML 执行。 */
function popupContent(pin: MapPin): HTMLElement {
  const content = document.createElement('div')
  content.className = 'pin-popup-content'
  const name = document.createElement('strong')
  name.textContent = pin.name
  content.append(name)
  if (pin.description) {
    const text = document.createElement('p')
    text.textContent = pin.description
    content.append(text)
  }
  return content
}

/**
 * 在底图上叠加公共图钉，并在选中时显示说明气泡。
 * onSelect 收到图钉 ID；点击空白处收到空串，由页面负责清空选中。
 */
export function createPinOverlay(map: Map, onSelect: (pinId: string) => void): PinOverlay {
  let installed = false
  let pins: MapPin[] = []
  let selectedId = ''
  const popup = new Popup({ closeButton: false, offset: 16, maxWidth: '260px', className: 'pin-popup' })

  function hit(event: MapMouseEvent) {
    if (!installed) return undefined
    const [feature] = map.queryRenderedFeatures(event.point, { layers: HIT_LAYERS })
    return typeof feature?.properties?.id === 'string' ? feature.properties.id : undefined
  }

  function syncPopup() {
    const pin = pins.find((item) => item.id === selectedId)
    if (!pin) {
      popup.remove()
      return
    }
    popup.setLngLat([pin.longitude, pin.latitude]).setDOMContent(popupContent(pin)).addTo(map)
  }

  function syncSource() {
    if (!installed) return
    const source = map.getSource(SOURCE_ID) as GeoJSONSource | undefined
    source?.setData(toFeatures(pins, selectedId))
  }

  function onClick(event: MapMouseEvent) {
    onSelect(hit(event) ?? '')
  }

  function onMouseMove(event: MapMouseEvent) {
    map.getCanvas().style.cursor = hit(event) ? 'pointer' : ''
  }

  function install() {
    if (installed) return
    installed = true
    map.addSource(SOURCE_ID, { type: 'geojson', data: toFeatures(pins, selectedId) })
    // 选中环画在最下层，避免遮住图钉本体。
    map.addLayer({
      id: RING_LAYER, type: 'circle', source: SOURCE_ID,
      filter: ['==', ['get', 'selected'], true],
      paint: {
        'circle-radius': 15, 'circle-color': 'rgba(0, 0, 0, 0)',
        'circle-stroke-color': PIN_SELECTED_COLOR, 'circle-stroke-width': 2,
        'circle-stroke-opacity': 0.9,
      },
    })
    map.addLayer({
      id: HALO_LAYER, type: 'circle', source: SOURCE_ID,
      paint: {
        'circle-radius': 9, 'circle-color': '#ffffff',
        'circle-stroke-color': PIN_COLOR, 'circle-stroke-width': 2,
        'circle-opacity': 0.96,
      },
    })
    map.addLayer({
      id: DOT_LAYER, type: 'circle', source: SOURCE_ID,
      paint: {
        'circle-radius': 3.6,
        'circle-color': ['case', ['get', 'selected'], PIN_SELECTED_COLOR, PIN_COLOR],
      },
    })
    map.on('click', onClick)
    map.on('mousemove', onMouseMove)
  }

  function update(nextPins: MapPin[], nextSelectedId: string) {
    pins = nextPins
    selectedId = nextSelectedId
    syncSource()
    syncPopup()
  }

  function destroy() {
    map.off('click', onClick)
    map.off('mousemove', onMouseMove)
    popup.remove()
    if (!installed) return
    installed = false
    // 样式已随地图销毁时不再逐个移除，避免重复删除报错。
    for (const layer of HIT_LAYERS) {
      if (map.getLayer(layer)) map.removeLayer(layer)
    }
    if (map.getSource(SOURCE_ID)) map.removeSource(SOURCE_ID)
    map.getCanvas().style.cursor = ''
  }

  return { install, update, destroy }
}
