// Bản đồ sự cố dùng chung: vị trí xe lúc báo sự cố, trạm nghỉ, điểm cứu hộ và các đường của phương án.
// Điều phối viên lập phương án (bấm chọn trạm, cứu hộ); Quản lý, tài xế, hộ tống xem lại (chỉ đọc).
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useRef, type ReactNode } from 'react'
import type { GeoPoint, RescuePoint, TransitStation } from '@shared/config/network'
import '../features/coordinator/fleet-plan/RouteMapPicker.css'
import './IncidentMap.css'

export interface MapLine { path: [number, number][]; color: string; weight?: number; dashed?: boolean; flow?: boolean; faded?: boolean; label?: string; onClick?: () => void }
type Named = GeoPoint & { name: string }

interface Props {
  height?: number | string
  location: GeoPoint // vị trí xe lúc báo sự cố
  origin: Named
  dest: Named
  gate?: Named
  stations?: TransitStation[]
  rescues?: RescuePoint[]
  selectedStation?: string
  nearest?: number // trạm đầu danh sách (đã xếp theo khoảng cách tới xe) được đánh số 1, 2, 3… trên bản đồ
  selectedRescue?: string
  lines?: MapLine[]
  onStation?: (name: string) => void
  onRescue?: (name: string) => void
  children?: ReactNode // bảng điều khiển ở góc trên bên trái
}

const short = (name: string) => name.split(' — ')[0]
const pin = (cls: string, label: string, size = 30) => L.divIcon({ className: 'rm-icon', html: `<span class="rm-pin ${cls}">${label}</span>`, iconSize: [size, size], iconAnchor: [size / 2, size / 2] })
const dot = (on: boolean, rank = 0) => L.divIcon({ className: 'rm-icon', iconSize: [34, 34], iconAnchor: [17, 17], html: on ? '<span class="rm-st rm-on rm-active">✓</span>' : rank ? `<span class="rm-st rm-on">${rank}</span>` : '<span class="rm-st"></span>' })

export function IncidentMap({ height = 460, location, origin, dest, gate, stations = [], rescues = [], selectedStation, nearest = 0, selectedRescue, lines = [], onStation, onRescue, children }: Props) {
  const box = useRef<HTMLDivElement>(null)
  const map = useRef<L.Map | null>(null)
  const layer = useRef<L.LayerGroup | null>(null)
  const fitted = useRef(false)

  useEffect(() => {
    if (!box.current) return
    const m = L.map(box.current, { zoomControl: false })
    L.control.zoom({ position: 'topright' }).addTo(m)
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 18, attribution: '© OpenStreetMap' }).addTo(m)
    m.setView([location.lat, location.lng], 10)
    layer.current = L.layerGroup().addTo(m)
    map.current = m
    const timers = [60, 350].map(ms => setTimeout(() => m.invalidateSize(), ms))
    return () => { timers.forEach(clearTimeout); m.remove(); map.current = null; layer.current = null; fitted.current = false }
  }, [location.lat, location.lng])

  // Vẽ lại mỗi khi đổi lựa chọn hoặc đường
  useEffect(() => {
    const g = layer.current, m = map.current
    if (!g || !m) return
    g.clearLayers()
    lines.forEach(l => {
      const opts = { color: l.color, interactive: !!l.onClick }
      L.polyline(l.path, { ...opts, weight: (l.weight ?? 5) + 7, opacity: l.faded ? 0.06 : 0.18, interactive: false }).addTo(g)
      const line = L.polyline(l.path, { ...opts, weight: l.weight ?? 5, opacity: l.faded ? 0.55 : 0.95, dashArray: l.dashed ? '6 8' : undefined, className: l.flow ? 'rm-route' : '' }).addTo(g)
      if (l.label) line.bindTooltip(l.label, { sticky: true })
      if (l.onClick) line.on('click', l.onClick)
    })
    stations.forEach((s, idx) => {
      const on = s.name === selectedStation
      const rank = idx < nearest ? idx + 1 : 0
      const mk = L.marker([s.lat, s.lng], { icon: dot(on, rank), zIndexOffset: on ? 600 : rank ? 300 : 0, title: s.name, interactive: !!onStation }).bindTooltip(s.name, { direction: 'top', offset: [0, -12] }).addTo(g)
      if (onStation) mk.on('click', () => onStation(s.name))
    })
    rescues.forEach(r => {
      const on = r.name === selectedRescue
      const mk = L.marker([r.lat, r.lng], { icon: pin(on ? 'rm-rs rm-rs-on' : 'rm-rs', 'CH', on ? 30 : 24), zIndexOffset: on ? 700 : 100, title: r.name, interactive: !!onRescue }).bindTooltip(`${r.name} · ${r.phone}`, { direction: 'top', offset: [0, -12] }).addTo(g)
      if (onRescue) mk.on('click', () => onRescue(r.name))
    })
    L.marker([origin.lat, origin.lng], { icon: pin('rm-a', 'A', 24), zIndexOffset: 400 }).bindTooltip(`Điểm đón: ${short(origin.name)}`, { direction: 'top', offset: [0, -12] }).addTo(g)
    L.marker([dest.lat, dest.lng], { icon: pin('rm-b', 'B', 24), zIndexOffset: 400 }).bindTooltip(`Điểm trả: ${short(dest.name)}`, { direction: 'top', offset: [0, -12] }).addTo(g)
    if (gate) L.marker([gate.lat, gate.lng], { icon: pin('rm-g', '⚑', 24), zIndexOffset: 400 }).bindTooltip(`Cửa khẩu ${gate.name}`, { direction: 'top', offset: [0, -12] }).addTo(g)
    L.marker([location.lat, location.lng], { icon: pin('rm-sos', '!', 36), zIndexOffset: 1000 }).bindTooltip('Vị trí xe lúc báo sự cố', { direction: 'top', offset: [0, -16], permanent: true }).addTo(g)
    // Lần vẽ đầu: zoom vào vị trí xe và các điểm của phương án
    if (!fitted.current) {
      const pts: [number, number][] = [[location.lat, location.lng], ...stations.slice(0, nearest).map((s): [number, number] => [s.lat, s.lng]), ...lines.filter(l => !l.faded && !l.dashed).flatMap(l => l.path)]
      if (pts.length > 1) m.fitBounds(L.latLngBounds(pts), { paddingTopLeft: [320, 50], paddingBottomRight: [50, 50], maxZoom: 11 })
      fitted.current = true
    }
  }, [lines, stations, rescues, selectedStation, nearest, selectedRescue, origin, dest, gate, location, onStation, onRescue])

  return (
    <div className="im-wrap" style={{ height }}>
      <div ref={box} className="rm-map im-map" role="application" aria-label="Bản đồ sự cố" />
      {children && <div className="im-panel">{children}</div>}
      <div className="rm-legend">
        <span><i className="rm-pin rm-sos">!</i> Vị trí xe</span>
        <span><i className="rm-st" /> Trạm nghỉ</span>
        {rescues.length > 0 && <span><i className="rm-pin rm-rs">CH</i> Cứu hộ</span>}
        <span><i className="rm-pin rm-a">A</i> Đón</span><span><i className="rm-pin rm-b">B</i> Trả</span>
      </div>
    </div>
  )
}
