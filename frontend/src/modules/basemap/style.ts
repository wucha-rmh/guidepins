import type { ExpressionSpecification, FilterSpecification, StyleSpecification } from 'maplibre-gl'
import type { FeatureCollection } from 'geojson'

/** 市界遮罩先以空数据建源，等底图可用后再补数据，避免样式构造依赖网络请求。 */
const emptyBoundary: FeatureCollection = { type: 'FeatureCollection', features: [] }

/**
 * 南京底图样式，对齐 map/南京全市离线地图/nanjing-city-map-v2.html 的「城区细节版」。
 * 要点：建筑画在道路下面并随缩放提高不透明度，主干道与次干道分色，三类标注（地名/道路名/建筑名）
 * 分级别显示。色彩与绘制顺序集中在这里，交互生命周期由 createBasemap 负责。
 */
export function createBasemapStyle(archiveUrl: string): StyleSpecification {
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
      { id: 'ground', type: 'background', paint: { 'background-color': '#f5f3ee' } },
      { id: 'green', source: 'city', 'source-layer': 'green', type: 'fill',
        paint: { 'fill-color': '#dce8d5', 'fill-opacity': 0.96 } },
      { id: 'water', source: 'city', 'source-layer': 'water', type: 'fill', filter: ['==', ['geometry-type'], 'Polygon'],
        paint: { 'fill-color': '#b8dce8' } },
      { id: 'streams', source: 'city', 'source-layer': 'water', type: 'line', filter: ['==', ['geometry-type'], 'LineString'],
        layout: lineLayout,
        paint: { 'line-color': '#a8cfdf', 'line-width': ['interpolate', ['linear'], ['zoom'], 11, 0.7, 17, 2] } },
      { id: 'boundary', source: 'city', 'source-layer': 'boundary', type: 'line',
        paint: { 'line-color': '#a4b3a1', 'line-width': 0.8, 'line-dasharray': [3, 3], 'line-opacity': 0.45 } },
      // 建筑数据从 z14 起才有，放在道路之前绘制，让街区肌理从主城区首屏（z14.2）就可见。
      { id: 'buildings', source: 'city', 'source-layer': 'building', type: 'fill', minzoom: 14,
        paint: {
          'fill-color': '#cec8bc', 'fill-outline-color': '#ada697',
          'fill-opacity': ['interpolate', ['linear'], ['zoom'], 14, 0.58, 15.5, 0.84, 17, 0.94],
        } },
      // 路底：主干道偏暖褐，其余偏灰绿，压住建筑描边形成层次。
      { id: 'roads-base', source: 'city', 'source-layer': 'road', type: 'line', layout: lineLayout,
        paint: {
          'line-color': ['case', majorRoad, '#d5bd91', '#d1d4c8'],
          'line-opacity': ['interpolate', ['linear'], ['zoom'], 8, 0.45, 13, 0.85, 17, 0.95],
          'line-width': ['interpolate', ['linear'], ['zoom'],
            8, ['*', 1.2, roadWeight], 13, ['*', 3.5, roadWeight], 16, ['*', 8.6, roadWeight], 19, ['*', 22.5, roadWeight]],
        } },
      // 路面：高速与快速路暖黄，主干道浅米色，步道近乎背景色，其余白色。
      { id: 'roads', source: 'city', 'source-layer': 'road', type: 'line', layout: lineLayout,
        paint: {
          'line-color': ['match', roadClass,
            ['motorway', 'trunk'], '#ecd09a',
            'primary', '#f1dfb6',
            ['footway', 'path', 'steps', 'cycleway'], '#fdfaf4',
            '#ffffff'],
          'line-width': ['interpolate', ['linear'], ['zoom'],
            8, ['*', 0.85, roadWeight], 13, ['*', 2.5, roadWeight], 16, ['*', 7, roadWeight], 19, ['*', 19.5, roadWeight]],
        } },
      { id: 'walls', source: 'city', 'source-layer': 'wall', type: 'line', layout: lineLayout,
        paint: { 'line-color': '#9eaa90', 'line-width': ['interpolate', ['linear'], ['zoom'], 12, 0.6, 17, 2], 'line-opacity': 0.9 } },
      // 市域以外的压暗层与市界描边，数据在底图就绪后补进来（见 createBasemap）。
      { id: 'outside-city', type: 'fill', source: 'cityLimit', filter: ['==', ['get', 'kind'], 'outside'],
        paint: { 'fill-color': '#f0f2ed', 'fill-opacity': ['interpolate', ['linear'], ['zoom'], 9, 0.72, 13, 0.24] } },
      { id: 'city-outline', type: 'line', source: 'cityLimit', filter: ['==', ['get', 'kind'], 'boundary'],
        paint: {
          'line-color': '#8da58a', 'line-opacity': 0.8,
          'line-width': ['interpolate', ['linear'], ['zoom'], 8, 1.4, 14, 0.85],
        } },
      { id: 'building-names', source: 'city', 'source-layer': 'building', type: 'symbol', minzoom: 15, filter: hasName,
        layout: { ...labels, 'text-size': ['interpolate', ['linear'], ['zoom'], 15, 10, 18, 12], 'text-max-width': 8, 'text-padding': 4 },
        paint: { 'text-color': '#777267', 'text-halo-color': '#faf8f3', 'text-halo-width': 1.3 } },
      { id: 'road-names', source: 'city', 'source-layer': 'road', type: 'symbol', minzoom: 13, filter: hasName,
        layout: {
          ...labels, 'text-size': ['interpolate', ['linear'], ['zoom'], 13, 10, 18, 12.5],
          'symbol-placement': 'line', 'symbol-spacing': 250, 'text-padding': 4, 'text-max-angle': 30,
        },
        paint: { 'text-color': '#626c60', 'text-halo-color': '#fffefa', 'text-halo-width': 1.5 } },
      // 地名按等级排序：城市名先出现，其次是街道与博物馆一类的地点。
      { id: 'place-names', source: 'city', 'source-layer': 'place', type: 'symbol', filter: hasName,
        layout: {
          ...labels,
          'text-size': ['interpolate', ['linear'], ['zoom'],
            8, ['match', ['get', 'class'], 'city', 16, 'town', 12, 10.5],
            14, ['match', ['get', 'class'], 'city', 18, 'town', 14, 12],
            18, ['match', ['get', 'class'], 'city', 21, 'town', 16, 14]],
          'text-padding': 4, 'text-max-width': 9,
          'symbol-sort-key': ['match', ['get', 'class'], 'city', 0, 'town', 1, 'museum', 2, 'suburb', 3, 4],
        },
        paint: {
          'text-color': ['match', ['get', 'class'], 'museum', '#8c6848', '#455c49'],
          'text-halo-color': '#fafbf7', 'text-halo-width': 1.7, 'text-halo-blur': 0.4,
        } },
    ],
  }
}
