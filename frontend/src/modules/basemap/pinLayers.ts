import { Popup, type GeoJSONSource, type Map, type MapMouseEvent } from 'maplibre-gl'
import type { Feature, FeatureCollection, Point } from 'geojson'
import type { MapPin } from '../public-map/types'

// 图钉模块只负责数据投影、绘制和命中；选中状态仍由地图页面统一维护。
const SOURCE_ID = 'public-map-pins'
const PIN_LAYER = 'public-map-pins-numbered'
const PIN_COLOR = '#315f4e'
const PIN_SELECTED_COLOR = '#a65330'
const IMAGE_RATIO = 2

interface PinProperties {
  id: string
  selected: boolean
  image: string
}

export interface PinOverlay {
  /** 地图样式加载完成后调用；重复调用安全。 */
  install: () => void
  update: (pins: MapPin[], selectedId: string) => void
  destroy: () => void
}

function imageId(number: number, selected: boolean): string {
  return `public-map-pin-${number}-${selected ? 'selected' : 'normal'}`
}

/**
 * 编号绘入本机 Canvas，再作为地图图标注册，不依赖数字字形服务。
 * 图像留出透明边距供点击命中，水滴尖端固定在同一位置，切换选中时不偏移坐标。
 */
function drawPin(number: number, selected: boolean): ImageData {
  const canvas = document.createElement('canvas')
  canvas.width = 40 * IMAGE_RATIO
  canvas.height = 48 * IMAGE_RATIO
  const context = canvas.getContext('2d')
  if (!context) throw new Error('无法绘制地图图钉，请刷新后重试。')
  context.scale(IMAGE_RATIO, IMAGE_RATIO)

  const radius = selected ? 16 : 14
  const centerY = selected ? 22 : 24
  context.beginPath()
  context.moveTo(20, 46)
  context.bezierCurveTo(16, 38, 20 - radius, centerY + 8, 20 - radius, centerY)
  context.arc(20, centerY, radius, Math.PI, Math.PI * 2)
  context.bezierCurveTo(20 + radius, centerY + 8, 24, 38, 20, 46)
  context.closePath()
  context.fillStyle = selected ? PIN_SELECTED_COLOR : PIN_COLOR
  context.shadowColor = 'rgba(30, 45, 37, 0.18)'
  context.shadowBlur = 3 * IMAGE_RATIO
  context.shadowOffsetY = IMAGE_RATIO
  context.fill()
  context.shadowColor = 'transparent'
  context.strokeStyle = '#fffefa'
  context.lineWidth = selected ? 3 : 2
  context.stroke()

  const label = String(number)
  context.fillStyle = '#fffefa'
  context.font = `500 ${label.length > 2 ? 10 : 13}px "Microsoft YaHei", sans-serif`
  context.textAlign = 'center'
  context.textBaseline = 'middle'
  context.fillText(label, 20, centerY, radius * 2 - 6)
  return context.getImageData(0, 0, canvas.width, canvas.height)
}

function toFeatures(pins: MapPin[], selectedId: string): FeatureCollection<Point, PinProperties> {
  return {
    type: 'FeatureCollection',
    features: pins.map((pin, index): Feature<Point, PinProperties> => ({
      type: 'Feature',
      geometry: { type: 'Point', coordinates: [pin.longitude, pin.latitude] },
      properties: { id: pin.id, selected: pin.id === selectedId, image: imageId(index + 1, pin.id === selectedId) },
    })),
  }
}

/** 所有地点内容均用文本节点写入，说明中的 HTML 不会被执行。 */
function popupContent(pin: MapPin, number: number, close: () => void): HTMLElement {
  const content = document.createElement('div')
  content.className = 'pin-popup-content'
  content.setAttribute('role', 'region')
  content.setAttribute('aria-label', `地点 ${number}：${pin.name}`)

  const label = document.createElement('span')
  label.className = 'pin-popup-kicker'
  label.textContent = `地点 ${number}`
  const name = document.createElement('strong')
  name.textContent = pin.name
  const closeButton = document.createElement('button')
  closeButton.type = 'button'
  closeButton.className = 'pin-popup-close'
  closeButton.setAttribute('aria-label', '关闭地点详情')
  closeButton.textContent = '×'
  closeButton.addEventListener('click', (event) => {
    event.stopPropagation()
    close()
  })
  content.append(label, name, closeButton)
  if (pin.description) {
    const text = document.createElement('p')
    text.textContent = pin.description
    content.append(text)
  }
  content.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
      event.stopPropagation()
      close()
    }
  })
  return content
}

/** 编号与接口返回的列表顺序一致，不代表路线、距离或推荐排名。 */
export function createPinOverlay(map: Map, onSelect: (pinId: string) => void): PinOverlay {
  let installed = false
  let pins: MapPin[] = []
  let selectedId = ''
  // 图片只按编号和状态缓存，切换地图可复用；销毁视口时统一释放。
  const images = new Set<string>()
  const popup = new Popup({
    closeButton: false, closeOnClick: false, focusAfterOpen: false,
    maxWidth: '300px', className: 'pin-popup',
    offset: {
      center: [0, -24],
      top: [0, 0], 'top-left': [0, -8], 'top-right': [0, -8],
      bottom: [0, -48], 'bottom-left': [0, -40], 'bottom-right': [0, -40],
      left: [20, -24], right: [-20, -24],
    },
  })

  function hit(event: MapMouseEvent) {
    if (!installed) return undefined
    const { x, y } = event.point
    const [feature] = map.queryRenderedFeatures([[x - 4, y - 4], [x + 4, y + 4]], { layers: [PIN_LAYER] })
    return typeof feature?.properties?.id === 'string' ? feature.properties.id : undefined
  }

  function closePopup() {
    onSelect('')
    // 气泡移除后把键盘焦点还给地图，不让焦点落回页面开头。
    map.getCanvas().focus({ preventScroll: true })
  }

  function syncPopup() {
    if (!installed) return
    const index = pins.findIndex((item) => item.id === selectedId)
    const pin = pins[index]
    if (!pin) {
      popup.remove()
      return
    }
    popup.setLngLat([pin.longitude, pin.latitude]).setDOMContent(popupContent(pin, index + 1, closePopup))
    // 已打开时直接换内容，避免重新挂载触发关闭事件或抢走侧栏焦点。
    if (!popup.isOpen()) popup.addTo(map)
  }

  function ensureImages() {
    pins.forEach((pin, index) => {
      const selected = pin.id === selectedId
      const id = imageId(index + 1, selected)
      if (images.has(id)) return
      map.addImage(id, drawPin(index + 1, selected), { pixelRatio: IMAGE_RATIO })
      images.add(id)
    })
  }

  function syncSource() {
    if (!installed) return
    ensureImages()
    const source = map.getSource(SOURCE_ID) as GeoJSONSource | undefined
    source?.setData(toFeatures(pins, selectedId))
  }

  function onClick(event: MapMouseEvent) {
    onSelect(hit(event) ?? '')
  }

  function onMouseMove(event: MapMouseEvent) {
    map.getCanvas().style.cursor = hit(event) ? 'pointer' : ''
  }

  function onKeyDown(event: KeyboardEvent) {
    if (event.key === 'Escape' && selectedId) onSelect('')
  }

  function install() {
    if (installed) return
    installed = true
    ensureImages()
    map.addSource(SOURCE_ID, { type: 'geojson', data: toFeatures(pins, selectedId) })
    map.addLayer({
      id: PIN_LAYER, type: 'symbol', source: SOURCE_ID,
      layout: {
        'icon-image': ['get', 'image'], 'icon-anchor': 'bottom',
        'icon-allow-overlap': true, 'icon-ignore-placement': true,
        'icon-pitch-alignment': 'viewport', 'icon-rotation-alignment': 'viewport',
        // 重叠时让选中图钉最后绘制；缩放时不因底图文字碰撞而隐藏地点。
        'symbol-sort-key': ['case', ['get', 'selected'], 1, 0], 'symbol-z-order': 'source',
      },
    })
    map.on('click', onClick)
    map.on('mousemove', onMouseMove)
    map.getCanvas().addEventListener('keydown', onKeyDown)
    syncPopup()
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
    map.getCanvas().removeEventListener('keydown', onKeyDown)
    popup.remove()
    if (!installed) return
    installed = false
    if (map.getLayer(PIN_LAYER)) map.removeLayer(PIN_LAYER)
    if (map.getSource(SOURCE_ID)) map.removeSource(SOURCE_ID)
    for (const id of images) {
      if (map.hasImage(id)) map.removeImage(id)
    }
    images.clear()
    map.getCanvas().style.cursor = ''
  }

  return { install, update, destroy }
}
