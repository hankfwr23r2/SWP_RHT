// Theo dõi lộ trình trên bản đồ: xe nằm đúng chỗ theo xác nhận thủ công của Driver
// (đang dừng tại trạm thì xe ở trạm, đang chạy thì xe ở giữa hai mốc). Không dùng GPS.
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useMemo, useRef } from 'react'
import { GATES, TRANSIT_STATIONS, type GeoPoint } from '../config/network'
import { bookingPath, findLocation, projectOnPath, spotLabel, vehiclePoint } from '../lib/booking'
import { useRoadRoute } from '../services/useRoadRoute'
import type { Booking, VehicleTrip } from '../types/booking'
import './TripTrackMap.css'

type Props = { b: Pick<Booking, 'origin' | 'dest' | 'gate' | 'route'>; trips: Pick<VehicleTrip, 'tripId' | 'run' | 'departedAt'>[]; height?: number; plain?: boolean } // plain: chỉ vẽ lộ trình đã lập, không có xe
const pin = (cls: string, text: string) => L.divIcon({ className: 'tt-icon', html: `<span class="tt-pin ${cls}">${text}</span>`, iconSize: [24, 24], iconAnchor: [12, 12] })
const ll = (p: GeoPoint): [number, number] => [p.lat, p.lng]

export function TripTrackMap({ b, trips, height = 360, plain }: Props) {
  const box = useRef<HTMLDivElement>(null)
  const map = useRef<L.Map | null>(null)
  const layer = useRef<L.LayerGroup | null>(null)
  const fitted = useRef(0)
  const pts = useMemo(() => bookingPath(b), [b])
  const road = useRoadRoute(pts.length > 1 ? pts : undefined)
  const line = road.route?.path ?? pts.map(ll)
  const cars = trips.map((t, i) => ({ t, i, at: vehiclePoint(b, t) })).filter(x => x.at)
  // Các mốc đã qua (trạm nghỉ, cửa khẩu) của xe đi xa nhất
  const passed = new Set(trips.flatMap(t => (t.run?.checkpoints ?? []).filter(c => c.arrivedAt && c.type !== 'pickup' && c.type !== 'delivery').map(c => c.place)))
  const sig = cars.map(c => `${c.at!.lat.toFixed(4)},${c.at!.lng.toFixed(4)}`).join('|') + [...passed].join(',') + line.length

  useEffect(() => {
    if (!box.current) return
    const m = L.map(box.current, { zoomControl: false })
    L.control.zoom({ position: 'topright' }).addTo(m)
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 18, attribution: '© OpenStreetMap' }).addTo(m)
    m.setView([10.8, 106.7], 9)
    layer.current = L.layerGroup().addTo(m)
    map.current = m
    const timers = [60, 350].map(ms => setTimeout(() => m.invalidateSize(), ms))
    return () => { timers.forEach(clearTimeout); m.remove(); map.current = null; layer.current = null; fitted.current = 0 }
  }, [])

  useEffect(() => {
    const g = layer.current, m = map.current
    if (!g || !m || line.length < 2) return
    g.clearLayers()
    L.polyline(line, { color: '#fff', weight: 9, opacity: 0.9 }).addTo(g)
    L.polyline(line, { color: '#475569', weight: 4, opacity: 0.95, dashArray: '9 8' }).addTo(g)
    // Đoạn đã đi: từ điểm đón tới xe đi xa nhất
    const far = cars.map(c => ({ c, at: projectOnPath(line.map(([lat, lng]) => ({ lat, lng })), c.at!).segment })).sort((x, z) => z.at - x.at)[0]
    if (far) L.polyline([...line.slice(0, far.at + 1), ll(far.c.at!)], { color: '#ea580c', weight: 7, opacity: 1 }).addTo(g)
    const a = findLocation(b.origin.id), z = findLocation(b.dest.id)
    if (a) L.marker(ll(a), { icon: pin('tt-a', 'A') }).bindTooltip(`Điểm đón: ${b.origin.name.split(' — ')[0]}`, { direction: 'top', offset: [0, -12] }).addTo(g)
    if (z) L.marker(ll(z), { icon: pin('tt-b', 'B') }).bindTooltip(`Điểm giao: ${b.dest.name.split(' — ')[0]}`, { direction: 'top', offset: [0, -12] }).addTo(g)
    const gate = b.gate ? GATES.find(x => x.name === b.gate) : undefined
    if (gate) L.marker(ll(gate), { icon: pin('tt-g', '⚑') }).bindTooltip(`Cửa khẩu ${gate.name}`, { direction: 'top', offset: [0, -12] }).addTo(g)
    ;(b.route?.rests ?? []).forEach(r => {
      const s = TRANSIT_STATIONS.find(x => x.name === r.name)
      if (!s) return
      L.marker(ll(s), { icon: L.divIcon({ className: 'tt-icon', html: `<span class="tt-st ${passed.has(r.name) ? 'tt-done' : ''}"></span>`, iconSize: [34, 34], iconAnchor: [17, 17] }) }).bindTooltip(`${r.name} · nghỉ ${r.minutes} phút${passed.has(r.name) ? ' (đã qua)' : ''}`, { direction: 'top', offset: [0, -12] }).addTo(g)
    })
    cars.forEach(({ t, at }) => {
      L.marker(ll(at!), { zIndexOffset: 1000, icon: L.divIcon({ className: 'tt-icon', html: '<span class="tt-truck"><i class="fa-solid fa-truck"></i></span>', iconSize: [40, 40], iconAnchor: [20, 20] }) }).bindTooltip(`${t.tripId}: ${spotLabel(t)}`, { direction: 'top', offset: [0, -20] }).addTo(g)
    })
    if (fitted.current !== line.length) { m.invalidateSize(); m.fitBounds(L.latLngBounds(line), { padding: [40, 40] }); fitted.current = line.length }
  }, [sig, b.origin.id, b.dest.id]) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div>
      <div className="tt-wrap" style={{ height }}>
        <div ref={box} className="tt-map" role="application" aria-label="Bản đồ theo dõi xe" />
        <div className="tt-legend">{!plain && <span><i className="fa-solid fa-truck" style={{ color: '#ea580c' }} /> Xe</span>}<span>● Trạm đã qua / chưa qua</span><span>⚑ Cửa khẩu</span></div>
      </div>
      {!plain && <div className="tt-spots">
        {trips.map((t, i) => <div key={t.tripId}><i className="fa-solid fa-location-crosshairs" /><span>{trips.length > 1 ? `Xe ${i + 1}: ` : ''}<b>{spotLabel(t)}</b></span></div>)}
      </div>}
      {!plain && <p className="form-hint" style={{ marginTop: 6 }}>Vị trí cập nhật khi tài xế xác nhận từng mốc (không phải GPS thời gian thực).</p>}
    </div>
  )
}
