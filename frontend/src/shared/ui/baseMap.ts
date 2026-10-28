import L from 'leaflet'
import { setWorkerUrl, type StyleSpecification } from 'maplibre-gl'
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url'
import 'maplibre-gl/dist/maplibre-gl.css'
import '@maplibre/maplibre-gl-leaflet'
import { adaptBaseMapStyle } from '@shared/lib/baseMapStyle'

// Vite gộp maplibre-gl nên đường dẫn worker mặc định bị sai; chỉ rõ file worker
setWorkerUrl(workerUrl)

// Nền OpenFreeMap (dữ liệu OpenStreetMap), không cần key; tải style một lần cho mọi bản đồ
const STYLE_URL = 'https://tiles.openfreemap.org/styles/liberty'
let stylePromise: Promise<StyleSpecification> | undefined

export const BASE_MAP_OPTIONS: L.MapOptions = {
  maxBounds: [[180, -Infinity], [-180, Infinity]],
  maxBoundsViscosity: 1,
  minZoom: 1,
}

export function addBaseLayer(map: L.Map): void {
  stylePromise ??= fetch(STYLE_URL)
    .then((res) => {
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json() as Promise<StyleSpecification>
    })
    .catch((err) => {
      stylePromise = undefined
      throw err
    })
  stylePromise
    .then((style) => {
      // Bản đồ đã bị gỡ (rời trang) trước khi tải xong style
      if (!map.getPane('tilePane')) return
      L.maplibreGL({ style: adaptBaseMapStyle(structuredClone(style)) }).addTo(map)
    })
    .catch((err) => console.error('Không tải được bản đồ nền OpenFreeMap:', err))
}
