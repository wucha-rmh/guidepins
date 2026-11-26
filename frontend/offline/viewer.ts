import { Map as LibreMap, Marker, Popup, NavigationControl, ScaleControl, LngLatBounds, addProtocol, setWorkerUrl } from 'maplibre-gl'
import type { LayerSpecification, LngLatBoundsLike } from 'maplibre-gl'
import { createBasemapStyle } from '../src/lib/basemap/style'
import { registerMetroStationIcon } from '../src/lib/basemap/metroStationIcon'
import type { City } from '../src/types/city'
import type { MapPin } from '../src/types/publicMap'
import type { GuideDetail } from '../src/types/guideEditing'

declare const __OFFLINE_WORKER__: string
interface OfflineData {
  snapshot: { version: number; name: string; exportedAt: string; mapUpdatedAt: string; city: City; pins: MapPin[]; guides: GuideDetail[] }
  basemap: { tiles: Record<string, number>; blobs: string[]; compression: number; minZoom: number;
    overviewZoom: number; detailZoom: number; coverage: { west: number; south: number; east: number; north: number }[] }
}
const byId = (id: string) => document.getElementById(id)!
const textElement = (tag: string, text: string, className = '') => {
  const element = document.createElement(tag); element.textContent = text; element.className = className; return element
}
function fromBase64(value: string) { return Uint8Array.from(atob(value), ch => ch.charCodeAt(0)) }
async function unzip(bytes: Uint8Array): Promise<ArrayBuffer> {
  return new Response(new Blob([bytes as BlobPart]).stream().pipeThrough(new DecompressionStream('gzip'))).arrayBuffer()
}

/** 仅从内嵌数据读取。所有业务文本用 textContent，避免攻略内容成为可执行 HTML。 */
async function start() {
  if (!globalThis.DecompressionStream) throw new Error('请使用较新版本的 Edge 或 Chrome 打开此文件。')
  const encoded = byId('payload').textContent!.trim()
  byId('payload').remove()
  const data: OfflineData = JSON.parse(new TextDecoder().decode(await unzip(fromBase64(encoded))))
  const { snapshot, basemap } = data
  if (snapshot.version !== 1 || snapshot.city.code !== '320100') throw new Error('离线文件版本或城市不受支持。')
  byId('title').textContent = snapshot.name
  document.title = `${snapshot.name} · 南京离线地图`
  byId('time').textContent = `保存于 ${new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', dateStyle: 'medium', timeStyle: 'short' }).format(new Date(snapshot.exportedAt))}`
  const workerUrl = URL.createObjectURL(new Blob([__OFFLINE_WORKER__], { type: 'text/javascript' }))
  setWorkerUrl(workerUrl)
  // 两个来源各自按正确层级绘制：范围外继续放大全市低倍瓦片，不冒充高倍详细瓦片。
  addProtocol('offline', async request => {
    const key = request.url.replace(/^offline:\/\/(overview|detail)\//, '')
    const index = basemap.tiles[key]
    if (index === undefined) return { data: new ArrayBuffer(0) }
    const bytes = fromBase64(basemap.blobs[index]!)
    return { data: basemap.compression === 2 ? await unzip(bytes) : bytes.buffer as ArrayBuffer }
  })
  const style = createBasemapStyle('unused', { metro: true })
  const attribution = '© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap contributors</a>'
  style.sources.city = { type: 'vector', tiles: ['offline://overview/{z}/{x}/{y}'],
    minzoom: basemap.minZoom, maxzoom: basemap.overviewZoom, attribution }
  style.sources.detail = { type: 'vector', tiles: ['offline://detail/{z}/{x}/{y}'],
    minzoom: basemap.overviewZoom + 1, maxzoom: basemap.detailZoom }
  const isMetro = (layer: LayerSpecification) => 'source-layer' in layer && layer['source-layer']?.startsWith('metro_')
  // 地铁只取全市来源，不再叠加详细来源，避免两套缩放几何产生双线和重复站点。
  const metroLayers = style.layers.filter(isMetro)
  const detailed = style.layers.filter(layer => 'source' in layer && layer.source === 'city' && !isMetro(layer))
    .map(layer => ({ ...layer, id: `detail-${layer.id}`, source: 'detail', minzoom: Math.max(13, layer.minzoom ?? 0) } as LayerSpecification))
  style.layers = [...style.layers.filter(layer => !isMetro(layer)), ...detailed, ...metroLayers]
  // 显示实际入包的 z16 瓦片边界，避免把近似半径误认为精确覆盖边界。
  const coverageTiles = Object.keys(basemap.tiles).filter(key => key.startsWith('16/'))
  function tileLon(x: number) { return x / 65536 * 360 - 180 }
  function tileLat(y: number) { return Math.atan(Math.sinh(Math.PI * (1 - 2 * y / 65536))) * 180 / Math.PI }
  const outlines = coverageTiles.flatMap(key => {
    const [, xString, yString] = key.split('/'); const x = Number(xString), y = Number(yString)
    const segments = [
      [`16/${x - 1}/${y}`, [[tileLon(x), tileLat(y)], [tileLon(x), tileLat(y + 1)]]],
      [`16/${x + 1}/${y}`, [[tileLon(x + 1), tileLat(y)], [tileLon(x + 1), tileLat(y + 1)]]],
      [`16/${x}/${y - 1}`, [[tileLon(x), tileLat(y)], [tileLon(x + 1), tileLat(y)]]],
      [`16/${x}/${y + 1}`, [[tileLon(x), tileLat(y + 1)], [tileLon(x + 1), tileLat(y + 1)]]],
    ] as const
    return segments.filter(([neighbor]) => basemap.tiles[neighbor] === undefined)
      .map(([, coordinates]) => ({ type: 'Feature' as const, properties: {}, geometry: { type: 'LineString' as const, coordinates: coordinates.map(point => [...point]) } }))
  })
  style.sources.coverage = { type: 'geojson', data: { type: 'FeatureCollection', features: outlines } }
  style.layers.push({ id: 'coverage-outline', source: 'coverage', type: 'line', minzoom: 11,
    paint: { 'line-color': '#b96540', 'line-width': 1.3, 'line-opacity': 0.65, 'line-dasharray': [4, 4] } })
  const b = snapshot.city.bounds
  const cityBounds: LngLatBoundsLike = [[b.west, b.south], [b.east, b.north]]
  const map = new LibreMap({ container: 'map', style, center: [118.79, 32.055], zoom: 12,
    // 全市适配需要容许市界外留白；严格 maxBounds 会把狭长城市强制放大并裁掉南北两端。
    minZoom: basemap.minZoom, maxZoom: 20, renderWorldCopies: false,
    dragRotate: false, pitchWithRotate: false, localIdeographFontFamily: 'Microsoft YaHei, PingFang SC, sans-serif' })
  map.addControl(new NavigationControl({ showCompass: false }), 'top-right')
  const disposeMetroIcon = registerMetroStationIcon(map)
  map.addControl(new ScaleControl({ unit: 'metric' }), 'bottom-left')
  let failed = false
  byId('map').setAttribute('aria-busy', 'true')
  map.on('movestart', () => byId('map').setAttribute('aria-busy', 'true'))
  map.on('dataloading', () => byId('map').setAttribute('aria-busy', 'true'))
  map.on('error', event => { failed = true; byId('map').setAttribute('aria-busy', 'false'); byId('status').textContent = `地图绘制失败：${event.error.message}`; byId('status').className = 'error' })
  map.on('webglcontextlost', () => { failed = true; byId('map').setAttribute('aria-busy', 'false'); byId('status').textContent = '地图绘制已中断，请重新打开此文件。' })
  map.on('idle', () => { byId('map').setAttribute('aria-busy', 'false'); if (!failed) byId('status').textContent = `离线可用 · ${snapshot.pins.length} 个地点 · ${snapshot.guides.length} 篇攻略` })
  const controls = new globalThis.Map<string, HTMLButtonElement[]>()
  let popup: Popup | undefined
  function select(pin: MapPin, fly = true) {
    controls.forEach((buttons, id) => buttons.forEach(button => button.setAttribute('aria-pressed', String(id === pin.id))))
    popup?.remove()
    const content = document.createElement('div')
    content.append(textElement('h3', pin.name))
    if (pin.description) content.append(textElement('p', pin.description))
    popup = new Popup({ maxWidth: '280px' }).setLngLat([pin.longitude, pin.latitude]).setDOMContent(content).addTo(map)
    if (fly) map.flyTo({ center: [pin.longitude, pin.latitude], zoom: 16, duration: 400 })
  }
  const guides = new globalThis.Map<string, HTMLDetailsElement>()
  for (const guide of snapshot.guides) {
    const element = document.createElement('details')
    element.append(textElement('summary', guide.title), textElement('p', guide.body, 'body'))
    if (guide.sourceUrl && /^https?:\/\//i.test(guide.sourceUrl)) {
      const link = document.createElement('a'); link.href = guide.sourceUrl; link.textContent = '查看出处原文（需联网）'; link.target = '_blank'; link.rel = 'noopener noreferrer'; element.append(link)
    }
    const stops = document.createElement('div'); stops.className = 'guide-stops'
    for (const stop of [...guide.pins].sort((a, b) => a.position - b.position)) {
      const pin = snapshot.pins.find(item => item.id === stop.pinId)
      if (!pin) continue
      const button = textElement('button', `${stop.position + 1}. ${pin.name}`)
      button.onclick = () => select(pin); stops.append(button)
    }
    element.append(stops); guides.set(guide.id, element); byId('guides').append(element)
  }
  if (!snapshot.guides.length) byId('guides').append(textElement('p', '这张地图暂无攻略。', 'hint'))
  snapshot.pins.forEach((pin, index) => {
    const item = document.createElement('li')
    const button = document.createElement('button'); button.textContent = pin.name; button.setAttribute('aria-pressed', 'false'); button.onclick = () => select(pin)
    item.append(button)
    if (pin.description) item.append(textElement('p', pin.description, 'description'))
    const sources = document.createElement('div'); sources.className = 'sources'
    for (const guide of snapshot.guides.filter(guide => guide.pins.some(stop => stop.pinId === pin.id))) {
      const source = textElement('button', guide.title)
      source.onclick = () => { const target = guides.get(guide.id)!; target.open = true; target.scrollIntoView({ behavior: 'smooth', block: 'nearest' }) }; sources.append(source)
    }
    item.append(sources); byId('pins').append(item)
    const markerButton = document.createElement('button'); markerButton.className = 'pin-marker'; markerButton.textContent = String(index + 1)
    markerButton.setAttribute('aria-label', `${index + 1}. ${pin.name}`); markerButton.setAttribute('aria-pressed', 'false'); markerButton.onclick = () => select(pin, false)
    controls.set(pin.id, [button, markerButton]); new Marker({ element: markerButton }).setLngLat([pin.longitude, pin.latitude]).addTo(map)
  })
  if (!snapshot.pins.length) byId('pins').append(textElement('li', '这张地图暂无地点。', 'hint'))
  const fitPins = () => {
    if (!snapshot.pins.length) { map.fitBounds(cityBounds, { padding: 30, duration: 0 }); return }
    const bounds = new LngLatBounds(); snapshot.pins.forEach(pin => bounds.extend([pin.longitude, pin.latitude]))
    map.fitBounds(bounds, { padding: 70, maxZoom: 15, duration: 0 })
  }
  byId('all').onclick = () => map.fitBounds(cityBounds, { padding: 30 })
  byId('places').onclick = fitPins
  fitPins()
  window.addEventListener('pagehide', () => { disposeMetroIcon(); map.remove(); URL.revokeObjectURL(workerUrl) }, { once: true })
}
start().catch(error => { byId('title').textContent = '离线地图打开失败'; byId('status').textContent = error instanceof Error ? error.message : '文件内容无效，请重新下载。'; byId('status').className = 'error' })
