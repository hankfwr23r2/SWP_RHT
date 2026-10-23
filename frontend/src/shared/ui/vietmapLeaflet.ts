import L from 'leaflet'
import '@vietmap/vietmap-gl-js/dist/vietmap-gl.css'
import '@vietmap/vietmap-gl-leaflet'

const apiKey = import.meta.env.VITE_VIETMAP_API_KEY?.trim()

export const VIETMAP_MAP_OPTIONS: L.MapOptions = {
  maxBounds: [[180, -Infinity], [-180, Infinity]],
  maxBoundsViscosity: 1,
  minZoom: 1,
}

export function addVietmapBaseLayer(map: L.Map): boolean {
  if (!apiKey) {
    console.error('Thiếu VITE_VIETMAP_API_KEY; không thể tải bản đồ Vietmap.')
    return false
  }

  L.vietmapGL({
    style: `https://maps.vietmap.vn/maps/styles/tm/style.json?apikey=${encodeURIComponent(apiKey)}`,
  }).addTo(map)
  return true
}
