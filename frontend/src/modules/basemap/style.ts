import type { ExpressionSpecification, FilterSpecification, LayerSpecification, StyleSpecification } from 'maplibre-gl'
import type { FeatureCollection } from 'geojson'

/** 市界遮罩先以空数据建源，等底图可用后再补数据，避免样式构造依赖网络请求。 */
const emptyBoundary: FeatureCollection = { type: 'FeatureCollection', features: [] }

export interface BasemapStyleOptions {
  /** 该地市的离线包是否内含地铁线路与站点图层。 */
  metro: boolean
}

/**
 * 城市漫游底图：道路负责方向，水系与绿地负责识别，建筑只提供街区肌理。
 * 沿用已有 PMTiles 数据与市界，按缩放减少无关细节；色彩和绘制顺序在此集中维护，
 * 交互生命周期由 createBasemap 负责，业务地点由 pinLayers 在最上方绘制。
 */
export function createBasemapStyle(archiveUrl: string, options: BasemapStyleOptions): StyleSpecification {
  const fonts = ['Microsoft YaHei', 'PingFang SC', 'Noto Sans CJK SC', 'sans-serif']
  const roadClass: ExpressionSpecification = ['get', 'class']
  const majorRoad: ExpressionSpecification = ['in', roadClass, ['literal', ['motorway', 'trunk', 'primary']]]
  // 道路宽度权重：按道路等级收缩，步道最细，未分类取中间值。
  const roadWeight: ExpressionSpecification = ['match', roadClass,
    ['motorway', 'trunk'], 1.25,
    'primary', 1,
    ['secondary', 'tertiary'], 0.8,
    ['footway', 'path', 'steps', 'cycleway'], 0.24,
    0.5]
  const lineLayout = { 'line-join': 'round', 'line-cap': 'round' } as const
  const labels = { 'text-field': ['get', 'name'] as ExpressionSpecification, 'text-font': fonts }
  // 只标注数据里已有名称的要素，空字符串同样跳过。
  const hasName: FilterSpecification = ['all', ['has', 'name'], ['!=', ['get', 'name'], '']]

  // 地铁线与站名是可选图层：没有地铁数据的城市不声明，避免 MapLibre 反复报缺图层。
  const metroLineLayers: LayerSpecification[] = options.metro ? [
    // 每条线路只画一根线，不做描边或并行股道；颜色取数据自带的 colour。
    { id: 'metro-lines', source: 'city', 'source-layer': 'metro_line', type: 'line', layout: lineLayout,
      paint: {
        'line-color': ['coalesce', ['get', 'colour'], '#7f8b9c'],
        'line-width': ['interpolate', ['linear'], ['zoom'], 9, 0.9, 12, 1.9, 15, 3.1, 18, 4.8],
        'line-opacity': 0.95,
      } },
  ] : []
  const metroStationLayers: LayerSpecification[] = options.metro ? [
    // 站点与站名都从 z12 起显示：先标点，再标名字，低缩放时靠名字区分站点。
    { id: 'metro-station-dots', source: 'city', 'source-layer': 'metro_station', type: 'circle', minzoom: 12,
      paint: {
        'circle-radius': ['interpolate', ['linear'], ['zoom'], 12, 1.8, 15, 3.2, 18, 4.4],
        'circle-color': '#ffffff',
        'circle-stroke-color': '#41506b', 'circle-stroke-width': 1.2,
        'circle-opacity': 0.95,
      } },
    { id: 'metro-stations', source: 'city', 'source-layer': 'metro_station', type: 'symbol', minzoom: 12, filter: hasName,
      layout: {
        ...labels, 'text-size': ['interpolate', ['linear'], ['zoom'], 12, 10, 17, 12.5],
        'text-offset': [0, 0.75], 'text-anchor': 'top', 'text-max-width': 6,
        'text-padding': 3,
      },
      paint: { 'text-color': '#33405a', 'text-halo-color': '#ffffff', 'text-halo-width': 1.4 } },
  ] : []

  return {
    version: 8,
    sources: {
      city: {
        type: 'vector', url: `pmtiles://${archiveUrl}`,
        attribution: '<a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">© OpenStreetMap contributors</a>',
      },
      cityLimit: { type: 'geojson', data: emptyBoundary },
    },
    // 使用本机字体绘制文字，不依赖第三方字形服务。
    layers: [
      { id: 'ground', type: 'background', paint: { 'background-color': '#f4f4ee' } },
      { id: 'green', source: 'city', 'source-layer': 'green', type: 'fill',
        paint: { 'fill-color': '#e0e8d9', 'fill-opacity': 0.9 } },
      { id: 'water', source: 'city', 'source-layer': 'water', type: 'fill', filter: ['==', ['geometry-type'], 'Polygon'],
        paint: { 'fill-color': '#c5dedd' } },
      { id: 'streams', source: 'city', 'source-layer': 'water', type: 'line', filter: ['==', ['geometry-type'], 'LineString'],
        layout: lineLayout,
        paint: { 'line-color': '#b3d2d1', 'line-width': ['interpolate', ['linear'], ['zoom'], 11, 0.6, 17, 2] } },
      { id: 'boundary', source: 'city', 'source-layer': 'boundary', type: 'line',
        paint: { 'line-color': '#aeb6a9', 'line-width': 0.7, 'line-dasharray': [3, 3], 'line-opacity': 0.3 } },
      // 建筑仍在道路之下；首屏只露出轮廓肌理，放大后再增强细节，避免压过业务图钉。
      { id: 'buildings', source: 'city', 'source-layer': 'building', type: 'fill', minzoom: 14,
        paint: {
          'fill-color': '#d9d9cf', 'fill-outline-color': '#c8ccbf',
          'fill-opacity': ['interpolate', ['linear'], ['zoom'], 14, 0.22, 16, 0.5, 18, 0.7],
        } },
      // 主干道路底保留暖灰层级，其余道路用低对比描边，避免密集街网形成噪声。
      { id: 'roads-base', source: 'city', 'source-layer': 'road', type: 'line', layout: lineLayout,
        paint: {
          'line-color': ['case', majorRoad, '#d7cdb4', '#dedfd5'],
          'line-opacity': ['interpolate', ['linear'], ['zoom'], 8, 0.4, 13, 0.65, 17, 0.85],
          'line-width': ['interpolate', ['linear'], ['zoom'],
            8, ['*', 1.2, roadWeight], 13, ['*', 3.5, roadWeight], 16, ['*', 8.6, roadWeight], 19, ['*', 22.5, roadWeight]],
        } },
      // 路面以纸白为主，仅主干道略带米色；道路位置和宽度仍来自原有真实数据。
      { id: 'roads', source: 'city', 'source-layer': 'road', type: 'line', layout: lineLayout,
        paint: {
          'line-color': ['match', roadClass,
            ['motorway', 'trunk'], '#eadfbe',
            'primary', '#f1ead5',
            ['footway', 'path', 'steps', 'cycleway'], '#fafaf6',
            '#fffefa'],
          'line-width': ['interpolate', ['linear'], ['zoom'],
            8, ['*', 0.85, roadWeight], 13, ['*', 2.5, roadWeight], 16, ['*', 7, roadWeight], 19, ['*', 19.5, roadWeight]],
        } },
      { id: 'walls', source: 'city', 'source-layer': 'wall', type: 'line', layout: lineLayout,
        paint: { 'line-color': '#a3b09a', 'line-width': ['interpolate', ['linear'], ['zoom'], 12, 0.5, 17, 1.8], 'line-opacity': 0.75 } },
      // 地铁线压在道路之上，保持与真实线路一致的空间关系，但不遮挡地名。
      ...metroLineLayers,
      // 市域以外的压暗层与市界描边，数据在底图就绪后补进来（见 createBasemap）。
      { id: 'outside-city', type: 'fill', source: 'cityLimit', filter: ['==', ['get', 'kind'], 'outside'],
        paint: { 'fill-color': '#f0f1eb', 'fill-opacity': ['interpolate', ['linear'], ['zoom'], 9, 0.72, 13, 0.2] } },
      { id: 'city-outline', type: 'line', source: 'cityLimit', filter: ['==', ['get', 'kind'], 'boundary'],
        paint: {
          'line-color': '#8fa38b', 'line-opacity': 0.7,
          'line-width': ['interpolate', ['linear'], ['zoom'], 8, 1.4, 14, 0.85],
        } },
      { id: 'building-names', source: 'city', 'source-layer': 'building', type: 'symbol', minzoom: 16, filter: hasName,
        layout: { ...labels, 'text-size': ['interpolate', ['linear'], ['zoom'], 16, 11, 19, 12], 'text-max-width': 8, 'text-padding': 8 },
        paint: { 'text-color': '#71796c', 'text-halo-color': '#f8f8f2', 'text-halo-width': 1.2,
          'text-opacity': ['interpolate', ['linear'], ['zoom'], 16, 0.65, 17, 1] } },
      { id: 'road-names', source: 'city', 'source-layer': 'road', type: 'symbol', minzoom: 13, filter: hasName,
        layout: {
          ...labels, 'text-size': ['interpolate', ['linear'], ['zoom'], 13, 11, 18, 13],
          'symbol-placement': 'line', 'symbol-spacing': 360, 'text-padding': 8, 'text-max-angle': 30,
        },
        paint: { 'text-color': '#64705f', 'text-halo-color': '#fffefa', 'text-halo-width': 1.5 } },
      // 站名排在地名之前，确保同一位置冲突时地铁站优先可见。
      ...metroStationLayers,
      // 远景只显示城市和城镇；街道在 z13、一般地点在 z15 后出现，减轻首屏密度。
      { id: 'place-names', source: 'city', 'source-layer': 'place', type: 'symbol', filter: hasName,
        layout: {
          ...labels,
          'text-size': ['interpolate', ['linear'], ['zoom'],
            8, ['match', ['get', 'class'], 'city', 16, 'town', 12, 10.5],
            14, ['match', ['get', 'class'], 'city', 18, 'town', 14, 12],
            18, ['match', ['get', 'class'], 'city', 21, 'town', 16, 14]],
          'text-padding': 10, 'text-max-width': 9,
          'symbol-sort-key': ['match', ['get', 'class'], 'city', 0, 'town', 1, 'museum', 2, 'suburb', 3, 4],
        },
        paint: {
          'text-color': ['match', ['get', 'class'], 'museum', '#77715f', '#53654f'],
          'text-halo-color': '#f8f9f3', 'text-halo-width': 1.5, 'text-halo-blur': 0.3,
          'text-opacity': ['step', ['zoom'],
            ['match', ['get', 'class'], ['city', 'town'], 1, 0],
            13, ['match', ['get', 'class'], ['city', 'town', 'suburb'], 1, 0],
            15, 1],
        } },
    ],
  }
}
