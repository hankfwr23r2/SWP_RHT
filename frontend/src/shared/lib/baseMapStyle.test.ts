import { validateStyleMin } from '@maplibre/maplibre-gl-style-spec'
import type { StyleSpecification } from 'maplibre-gl'
import { describe, expect, it } from 'vitest'
import { adaptBaseMapStyle, ARCHIPELAGO_ZONES } from './baseMapStyle'

// Rút gọn từ style OpenFreeMap "liberty": đủ các loại lớp cần chỉnh
function libertyLikeStyle(): StyleSpecification {
  const osmName = ['case', ['has', 'name:nonlatin'], ['concat', ['get', 'name:latin'], '\n', ['get', 'name:nonlatin']], ['coalesce', ['get', 'name_en'], ['get', 'name']]]
  return {
    version: 8,
    glyphs: 'https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf',
    sources: { openmaptiles: { type: 'vector', url: 'https://tiles.openfreemap.org/planet' } },
    layers: [
      { id: 'background', type: 'background', paint: { 'background-color': '#f8f4f0' } },
      { id: 'water', type: 'fill', source: 'openmaptiles', 'source-layer': 'water', paint: { 'fill-color': '#9ebdff' } },
      { id: 'boundary_3', type: 'line', source: 'openmaptiles', 'source-layer': 'boundary', filter: ['all', ['>=', ['get', 'admin_level'], 3], ['!=', ['get', 'maritime'], 1]] },
      { id: 'boundary_disputed', type: 'line', source: 'openmaptiles', 'source-layer': 'boundary', filter: ['==', ['get', 'disputed'], 1] },
      { id: 'highway-shield-non-us', type: 'symbol', source: 'openmaptiles', 'source-layer': 'transportation_name', layout: { 'text-field': ['to-string', ['get', 'ref']], 'text-font': ['Noto Sans Regular'] } },
      { id: 'poi_r1', type: 'symbol', source: 'openmaptiles', 'source-layer': 'poi', layout: { 'text-field': osmName, 'text-font': ['Noto Sans Regular'] } },
      { id: 'label_city', type: 'symbol', source: 'openmaptiles', 'source-layer': 'place', filter: ['==', ['get', 'class'], 'city'], layout: { 'text-field': osmName, 'text-font': ['Noto Sans Regular'] } },
    ],
  } as StyleSpecification
}

function inZones([x, y]: [number, number]) {
  return ARCHIPELAGO_ZONES.coordinates.some(([ring]) => {
    let inside = false
    for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
      const [xi, yi] = ring[i]
      const [xj, yj] = ring[j]
      if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside
    }
    return inside
  })
}

const layer = (style: StyleSpecification, id: string) => style.layers.find((l) => l.id === id) as Record<string, any>
const excludesZones = (filter: unknown) => JSON.stringify(filter).includes(JSON.stringify(['>', ['distance', ARCHIPELAGO_ZONES], 0]))

describe('bản đồ nền OpenFreeMap', () => {
  it('style sau khi chỉnh vẫn hợp lệ', () => {
    expect(validateStyleMin(adaptBaseMapStyle(libertyLikeStyle()))).toEqual([])
  })

  it('nhãn địa danh ưu tiên tên tiếng Việt, không dùng tên chữ gốc (Khmer, Lào...)', () => {
    const style = adaptBaseMapStyle(libertyLikeStyle())
    for (const id of ['poi_r1', 'label_city']) {
      expect(layer(style, id).layout['text-field']).toEqual(['coalesce', ['get', 'name:vi'], ['get', 'name:latin'], ['get', 'name:en'], ''])
    }
    expect(layer(style, 'highway-shield-non-us').layout['text-field']).toEqual(['to-string', ['get', 'ref']])
  })

  it('ẩn mọi nhãn và đường ranh giới chạm vào vùng hai quần đảo, giữ điều kiện lọc cũ', () => {
    const style = adaptBaseMapStyle(libertyLikeStyle())
    for (const id of ['boundary_3', 'boundary_disputed', 'highway-shield-non-us', 'poi_r1', 'label_city']) {
      expect(excludesZones(layer(style, id).filter), id).toBe(true)
    }
    expect(JSON.stringify(layer(style, 'label_city').filter)).toContain(JSON.stringify(['==', ['get', 'class'], 'city']))
    expect(layer(style, 'water').filter).toBeUndefined()
  })

  it('vùng ẩn phủ Hoàng Sa, Trường Sa và không lấn sang đất liền hay đảo nước khác', () => {
    const covered: [number, number][] = [
      [112.33, 16.83], // đảo Phú Lâm
      [111.6, 16.53], // đảo Hoàng Sa
      [111.92, 8.645], // đảo Trường Sa Lớn
      [114.36, 10.38], // đảo Ba Bình
      [114.33, 11.43], // đảo Song Tử Tây
      [113.84, 7.37], // đá Hoa Lau
    ]
    const outside: [number, number][] = [
      [108.2, 16.05], // Đà Nẵng
      [109.2, 12.24], // Nha Trang
      [106.6, 8.68], // Côn Đảo
      [109.5, 18.25], // Tam Á, Hải Nam
      [118.74, 9.74], // Puerto Princesa, Palawan
      [116.07, 5.98], // Kota Kinabalu
    ]
    for (const p of covered) expect(inZones(p), String(p)).toBe(true)
    for (const p of outside) expect(inZones(p), String(p)).toBe(false)
  })

  it('thêm nhãn Hoàng Sa, Trường Sa thuộc Việt Nam, luôn hiện', () => {
    const style = adaptBaseMapStyle(libertyLikeStyle())
    const source = style.sources.archipelago_labels as { data: GeoJSON.FeatureCollection<GeoJSON.Point> }
    const labels = source.data.features.map((f) => [f.properties?.name, inZones(f.geometry.coordinates as [number, number])])
    expect(labels).toEqual([
      ['Quần đảo Hoàng Sa\n(Việt Nam)', true],
      ['Quần đảo Trường Sa\n(Việt Nam)', true],
    ])
    const top = style.layers[style.layers.length - 1] as Record<string, any>
    expect(top).toMatchObject({ id: 'archipelago_labels', type: 'symbol', source: 'archipelago_labels' })
    expect(top.layout).toMatchObject({ 'text-allow-overlap': true, 'text-ignore-placement': true })
  })
})
