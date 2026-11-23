import {
  LngLatBounds, Map, NavigationControl, ScaleControl, addProtocol, removeProtocol, setWorkerUrl,
  type GeoJSONSource, type LngLatBoundsLike, type LngLatLike,
} from 'maplibre-gl'
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?url'
import type { FeatureCollection } from 'geojson'
import { FetchSource, PMTiles, Protocol } from 'pmtiles'
import boundaryUrl from './nanjing-boundary.json?url'
import type { City } from '../city/types'
import type { MapPin } from '../public-map/types'
import { createPinOverlay } from './pinLayers'
import { createBasemapStyle } from './style'
import { urbanViewFor } from './views'

// 交由 Vite 输出独立 Worker 资源，避免依赖预构建改变 MapLibre 的相对路径。
setWorkerUrl(workerUrl)

interface BasemapEvents {
  ready: () => void
  failed: (message: string) => void
  moved: (zoom: number) => void
  /** 点击图钉或被点击空白处清空选中时回调，空串表示清空。 */
  pinSelected: (pinId: string) => void
}

function failureMessage(cause: unknown): string {
  if (typeof cause === 'object' && cause !== null && 'status' in cause && cause.status === 404) {
    return '底图文件不存在，请确认文件已就绪后重试。'
  }
  return '底图读取失败，请检查网络和地图服务后重试。'
}

/** 当前只有一个地图视口；每次创建独立协议缓存，重试时不复用失败的归档请求。 */
export function createBasemap(container: HTMLElement, city: City, events: BasemapEvents) {
  const archiveUrl = new URL(city.basemapPath, window.location.origin)
  if (!['http:', 'https:'].includes(archiveUrl.protocol)) throw new Error('底图地址无效，请联系管理员。')
  const { west, south, east, north } = city.bounds
  const bounds: LngLatBoundsLike = [[west, south], [east, north]]
  // 首屏落在主城区，全市轮廓改由「查看全市」按钮进入。
  const urban = urbanViewFor(city)
  const lifecycle = new AbortController()
  const source = new FetchSource(archiveUrl.href)
  const protocol = new Protocol()
  // PMTiles 头部请求也绑定视口生命周期，避免超时或离开页面后仍悬挂。
  protocol.add(new PMTiles({
    getKey: () => source.getKey(),
    getBytes: (offset, length, signal, etag) => {
      const request = new AbortController()
      const abort = () => request.abort()
      lifecycle.signal.addEventListener('abort', abort, { once: true })
      signal?.addEventListener('abort', abort, { once: true })
      if (lifecycle.signal.aborted || signal?.aborted) abort()
      return source.getBytes(offset, length, request.signal, etag).finally(() => {
        lifecycle.signal.removeEventListener('abort', abort)
        signal?.removeEventListener('abort', abort)
      })
    },
  }))
  addProtocol('pmtiles', protocol.tile)
  let map: Map
  try {
    map = new Map({
      container,
      style: createBasemapStyle(archiveUrl.href),
      center: urban.center,
      zoom: urban.zoom,
      minZoom: 7, maxZoom: 20,
      renderWorldCopies: false,
      dragRotate: false, pitchWithRotate: false,
      // URL Hash 留给页面路由，地图交互不改写路由。
      hash: false,
      localIdeographFontFamily: 'Microsoft YaHei, PingFang SC, sans-serif',
      locale: {
        'NavigationControl.ZoomIn': '放大', 'NavigationControl.ZoomOut': '缩小',
        'AttributionControl.ToggleAttribution': '地图数据来源', 'Map.Title': `${city.name}底图`,
      },
    })
  } catch {
    lifecycle.abort()
    removeProtocol('pmtiles')
    throw new Error('无法初始化地图，请检查浏览器是否支持 WebGL，或刷新后重试。')
  }

  let disposed = false
  let failed = false
  let timeout: ReturnType<typeof setTimeout> | undefined
  function fail(message: string) {
    if (disposed || failed) return
    failed = true
    clearTimeout(timeout)
    lifecycle.abort()
    events.failed(message)
  }
  function watchLoading() {
    if (disposed || failed || timeout !== undefined) return
    timeout = setTimeout(() => fail('底图加载超时，请检查网络后重试。'), 20000)
  }
  const resizeObserver = new ResizeObserver(() => { if (!disposed) map.resize() })
  map.on('error', ({ error }) => {
    if ('name' in error && error.name === 'AbortError') return
    // 个别标注缺字形只影响那一行文字，不该让整幅底图进入失败态。
    if (error instanceof Error && error.message.includes('glyph')) return
    fail(failureMessage(error))
  })
  map.on('dataloading', watchLoading)
  map.on('idle', () => {
    clearTimeout(timeout)
    timeout = undefined
    if (!disposed && !failed) events.ready()
  })
  map.on('moveend', () => { if (!disposed) events.moved(map.getZoom()) })
  // 缩放动画与滚轮过程中也刷新级别读数，避免动画结束时停在中间值。
  map.on('zoom', () => { if (!disposed) events.moved(map.getZoom()) })
  map.on('webglcontextlost', () => fail('地图绘制已中断，请重试加载底图。'))
  // 图钉图层要等样式加载完成才能添加；此前收到的图钉由 overlay 自行保存。
  const pinOverlay = createPinOverlay(map, (pinId) => { if (!disposed) events.pinSelected(pinId) })
  /** 市界遮罩只是低缩放级别下的观感参考，取不到数据就保持无遮罩，不影响底图可用。 */
  async function loadBoundary() {
    try {
      const response = await fetch(boundaryUrl, { signal: lifecycle.signal })
      if (!response.ok || disposed) return
      const data = (await response.json()) as FeatureCollection
      if (disposed) return
      const source = map.getSource('cityLimit') as GeoJSONSource | undefined
      source?.setData(data)
    } catch {
      // 让路给底图本身；失败原因不在这里提示。
    }
  }
  map.on('load', () => {
    if (disposed) return
    pinOverlay.install()
    void loadBoundary()
  })
  map.addControl(new NavigationControl({ showCompass: false }), 'top-right')
  map.addControl(new ScaleControl({ maxWidth: 110, unit: 'metric' }), 'bottom-left')
  resizeObserver.observe(container)
  watchLoading()
  events.moved(map.getZoom())

  let currentPins: MapPin[] = []
  return {
    fitCity: () => map.fitBounds(bounds, { padding: 32, duration: 300 }),
    /** 回到主城区首屏；从全市轮廓切回街区细节。 */
    fitUrban: () => map.flyTo({ center: urban.center, zoom: urban.zoom, duration: 600 }),
    setPins(nextPins: MapPin[], selectedId: string) {
      currentPins = nextPins
      pinOverlay.update(nextPins, selectedId)
    },
    /** 打开公共地图时把视野收到图钉范围；没有图钉时保持地市范围。 */
    fitPins(nextPins: MapPin[]) {
      if (nextPins.length === 0 || disposed) return
      const target = new LngLatBounds()
      for (const pin of nextPins) target.extend([pin.longitude, pin.latitude])
      map.fitBounds(target, { padding: 96, maxZoom: 16, duration: 300 })
    },
    flyToPin(pinId: string) {
      const pin = currentPins.find((item) => item.id === pinId)
      if (!pin || disposed) return
      const center: LngLatLike = [pin.longitude, pin.latitude]
      map.flyTo({ center, zoom: Math.max(map.getZoom(), 16), duration: 600 })
    },
    destroy() {
      disposed = true
      clearTimeout(timeout)
      lifecycle.abort()
      resizeObserver.disconnect()
      pinOverlay.destroy()
      map.remove()
      removeProtocol('pmtiles')
    },
  }
}
