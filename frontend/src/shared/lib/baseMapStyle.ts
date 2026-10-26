import type { ExpressionSpecification, FilterSpecification, StyleSpecification } from 'maplibre-gl'

// Vùng quần đảo Hoàng Sa và Trường Sa: dữ liệu OSM ở đây ghi theo đơn vị hành chính nước khác
// (Tam Sa, Tây Sa, Nam Sa...) nên ẩn hết nhãn và ranh giới, thay bằng nhãn của mình
export const ARCHIPELAGO_ZONES: GeoJSON.MultiPolygon = {
  type: 'MultiPolygon',
  coordinates: [
    [[[110.9, 15.5], [113.2, 15.5], [113.2, 17.4], [110.9, 17.4], [110.9, 15.5]]],
    [[[111.0, 12.0], [117.2, 12.0], [117.2, 10.6], [116.2, 8.3], [115.0, 6.5], [113.0, 6.0], [111.0, 7.5], [111.0, 12.0]]],
  ],
}

const ARCHIPELAGO_LABELS: GeoJSON.FeatureCollection = {
  type: 'FeatureCollection',
  features: [
    { type: 'Feature', properties: { name: 'Quần đảo Hoàng Sa\n(Việt Nam)' }, geometry: { type: 'Point', coordinates: [112.0, 16.5] } },
    { type: 'Feature', properties: { name: 'Quần đảo Trường Sa\n(Việt Nam)' }, geometry: { type: 'Point', coordinates: [114.0, 9.8] } },
  ],
}

// Không lấy `name`/`name_en` vì có thể là chữ Khmer, Lào mà font không có (name_en tự lấy lại name khi thiếu)
const VI_NAME: ExpressionSpecification = ['coalesce', ['get', 'name:vi'], ['get', 'name:latin'], ['get', 'name:en'], '']
// distance = 0 khi đối tượng chạm vào vùng, nên ẩn cả đường chỉ cắt ngang một phần
const OUTSIDE_ZONES: ExpressionSpecification = ['>', ['distance', ARCHIPELAGO_ZONES], 0]

export function adaptBaseMapStyle(style: StyleSpecification): StyleSpecification {
  for (const layer of style.layers) {
    const isLabel = layer.type === 'symbol' && layer.layout?.['text-field'] !== undefined
    if (!isLabel && !('source-layer' in layer && layer['source-layer'] === 'boundary')) continue
    if (isLabel && !layer.id.includes('shield')) layer.layout!['text-field'] = VI_NAME
    const own = layer as { filter?: FilterSpecification }
    own.filter = own.filter ? ['all', own.filter as ExpressionSpecification, OUTSIDE_ZONES] : OUTSIDE_ZONES
  }
  style.sources.archipelago_labels = { type: 'geojson', data: ARCHIPELAGO_LABELS }
  style.layers.push({
    id: 'archipelago_labels',
    type: 'symbol',
    source: 'archipelago_labels',
    layout: {
      'text-field': ['get', 'name'],
      'text-font': ['Noto Sans Italic'],
      'text-size': ['interpolate', ['linear'], ['zoom'], 3, 10, 8, 15],
      'text-allow-overlap': true,
      'text-ignore-placement': true,
    },
    paint: { 'text-color': '#334', 'text-halo-color': '#fff', 'text-halo-width': 1.5 },
  })
  return style
}
