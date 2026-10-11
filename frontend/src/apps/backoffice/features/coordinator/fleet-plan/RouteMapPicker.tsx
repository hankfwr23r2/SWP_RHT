// Chọn trạm nghỉ trên bản đồ: có điểm đón, điểm trả, cửa khẩu (tuyến quốc tế) và các trạm của hệ thống.
// Bấm một trạm để chọn và đặt thời gian nghỉ ở góc trên bên trái; hệ thống tự xếp thứ tự trạm và vẽ đường đi qua các điểm.
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useMemo, useRef, useState } from 'react'
import { MIN_REST_MINUTES } from '@shared/config/booking-rules'
import { TRANSIT_STATIONS, type GeoPoint } from '@shared/config/network'
import { routeOutline } from '@shared/lib/booking'
import { useRoadRoute } from '@shared/services/useRoadRoute'
import type { RestStop } from '@shared/types/booking'
import { Modal } from '@shared/ui/Modal'
import './RouteMapPicker.css'

type Point = GeoPoint & { name: string }
const MINUTES = [MIN_REST_MINUTES, 45, 60, 90, 120]
const DEFAULT_MINUTES = 45
const short = (name: string) => name.split(' — ')[0]
const pin = (cls: string, label: string) => L.divIcon({ className: 'rm-icon', html: `<span class="rm-pin ${cls}">${label}</span>`, iconSize: [30, 30], iconAnchor: [15, 15] })
const stationIcon = (order: number, active: boolean) => L.divIcon({
  className: 'rm-icon', iconSize: [34, 34], iconAnchor: [17, 17],
  html: order ? `<span class="rm-st rm-on${active ? ' rm-active' : ''}">${order}</span>` : '<span class="rm-st"></span>',
})
const haversine = (a: GeoPoint, b: GeoPoint) => {
  const r = Math.PI / 180, dLat = (b.lat - a.lat) * r, dLng = (b.lng - a.lng) * r
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * r) * Math.cos(b.lat * r) * Math.sin(dLng / 2) ** 2
  return 12742 * Math.asin(Math.sqrt(h))
}

interface Props {
  origin: Point
  dest: Point
  gate?: Point
  departAt?: number // giờ khởi hành, để Google tính giao thông theo giờ đó
  value: RestStop[]
  onApply: (rests: RestStop[]) => void
  onClose: () => void
}

export function RouteMapPicker({ origin, dest, gate, departAt, value, onApply, onClose }: Props) {
  const box = useRef<HTMLDivElement>(null)
  const map = useRef<L.Map | null>(null)
  const layer = useRef<L.LayerGroup | null>(null)
  // Các trạm đang chọn và số phút nghỉ của từng trạm
  const [picked, setPicked] = useState<Map<string, number>>(() => new Map(value.filter(r => TRANSIT_STATIONS.some(s => s.name === r.name)).map(r => [r.name, r.minutes])))
  const [active, setActive] = useState<string | null>(null)

  const chosen = useMemo(() => TRANSIT_STATIONS.filter(s => picked.has(s.name)), [picked])
  const outline = useMemo(() => routeOutline(origin, gate, dest, chosen), [origin, gate, dest, chosen])
  const straightKm = outline.path.slice(1).reduce((t, p, i) => t + haversine(outline.path[i], p), 0)
  // Đường bộ thật qua các điểm đã chọn: vẽ theo đường này, chưa lấy được thì vẽ nét thẳng
  const { route: road, loading: roadLoading } = useRoadRoute(outline.path, departAt)
  const order = (name: string) => outline.stations.findIndex(s => s.name === name) + 1

  const choose = (name: string) => {
    setPicked(m => (m.has(name) ? m : new Map(m).set(name, DEFAULT_MINUTES)))
    setActive(name)
  }
  const setMinutes = (name: string, minutes: number) => setPicked(m => new Map(m).set(name, minutes))
  const remove = (name: string) => { setPicked(m => { const n = new Map(m); n.delete(name); return n }); setActive(null) }

  // Dựng bản đồ một lần
  useEffect(() => {
    if (!box.current) return
    const m = L.map(box.current, { zoomControl: false, attributionControl: true })
    L.control.zoom({ position: 'topright' }).addTo(m)
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 18, attribution: '© OpenStreetMap' }).addTo(m)
    // Mở ra đã zoom vào tuyến (điểm đón, điểm trả, cửa khẩu); các trạm xa hơn thì kéo bản đồ để thấy
    const route = [origin, dest, ...(gate ? [gate] : [])]
    m.fitBounds(L.latLngBounds(route.map(p => [p.lat, p.lng] as [number, number])), { padding: [90, 90], maxZoom: 11 })
    layer.current = L.layerGroup().addTo(m)
    map.current = m
    // Cửa sổ vừa mở có hiệu ứng nên cần tính lại kích thước bản đồ
    const timers = [60, 350].map(ms => setTimeout(() => m.invalidateSize(), ms))
    return () => { timers.forEach(clearTimeout); m.remove(); map.current = null; layer.current = null }
  }, [origin, dest, gate])

  // Vẽ lại đường và các điểm mỗi khi đổi trạm được chọn
  useEffect(() => {
    const g = layer.current
    if (!g) return
    g.clearLayers()
    const line = road?.path ?? outline.path.map(p => [p.lat, p.lng] as [number, number])
    const chosenAny = outline.stations.length > 0
    // Có trạm đã chọn: đường cam chuyển động; chưa chọn: đường xám nhạt điểm đón → điểm trả
    L.polyline(line, { color: chosenAny ? '#ea580c' : '#64748b', weight: 12, opacity: 0.18, interactive: false }).addTo(g)
    L.polyline(line, { color: chosenAny ? '#ea580c' : '#64748b', weight: 5, opacity: 0.95, className: chosenAny ? 'rm-route' : '', dashArray: road ? undefined : '6 8', interactive: false }).addTo(g)
    TRANSIT_STATIONS.forEach(s => {
      const n = order(s.name)
      L.marker([s.lat, s.lng], { icon: stationIcon(n, s.name === active), zIndexOffset: n ? 500 : 0, title: s.name })
        .bindTooltip(`${s.name}${picked.has(s.name) ? ` · nghỉ ${picked.get(s.name)} phút` : ''}`, { direction: 'top', offset: [0, -12] })
        .on('click', () => choose(s.name)).addTo(g)
    })
    L.marker([origin.lat, origin.lng], { icon: pin('rm-a', 'A'), zIndexOffset: 900 }).bindTooltip(`Điểm đón: ${short(origin.name)}`, { direction: 'top', offset: [0, -14] }).addTo(g)
    L.marker([dest.lat, dest.lng], { icon: pin('rm-b', 'B'), zIndexOffset: 900 }).bindTooltip(`Điểm trả: ${short(dest.name)}`, { direction: 'top', offset: [0, -14] }).addTo(g)
    if (gate) L.marker([gate.lat, gate.lng], { icon: pin('rm-g', '⚑'), zIndexOffset: 900 }).bindTooltip(`Cửa khẩu ${gate.name}`, { direction: 'top', offset: [0, -14] }).addTo(g)
  }, [outline, picked, active, road]) // eslint-disable-line react-hooks/exhaustive-deps

  const apply = () => { onApply(outline.stations.map((s, i) => ({ afterLeg: i + 1, name: s.name, minutes: picked.get(s.name) ?? DEFAULT_MINUTES }))); onClose() }
  const act = active ? TRANSIT_STATIONS.find(s => s.name === active) : undefined
  const total = [...picked.values()].reduce((t, x) => t + x, 0)

  return (
    <Modal wide onClose={onClose} title="Chọn trạm nghỉ" subtitle="Bấm vào các chấm trạm trên bản đồ; hệ thống tự xếp thứ tự và vẽ đường đi qua các điểm."
      footer={<><button className="btn btn-ghost" onClick={onClose}>Hủy</button><button className="btn btn-primary" onClick={apply}><i className="fa-solid fa-check" /> Áp dụng {outline.stations.length} trạm</button></>}>
      <div className="rm-wrap">
        <div ref={box} className="rm-map" role="application" aria-label="Bản đồ chọn trạm nghỉ" />
        <div className="rm-panel">
          {act ? (
            <>
              <div className="rm-head"><span className="rm-num">{order(act.name)}</span><div><b>{act.name}</b><small>{act.area}</small></div></div>
              <span className="rm-label">Thời gian nghỉ tại trạm</span>
              <div className="rm-chips">{MINUTES.map(m => <button key={m} type="button" className={picked.get(act.name) === m ? 'on' : ''} onClick={() => setMinutes(act.name, m)}>{m} phút</button>)}</div>
              <button type="button" className="rm-remove" onClick={() => remove(act.name)}><i className="fa-regular fa-trash-can" /> Bỏ trạm này</button>
            </>
          ) : <p className="rm-hint"><i className="fa-solid fa-hand-pointer" /> Bấm vào một trạm (chấm trắng viền xanh) để chọn và đặt thời gian nghỉ.</p>}
          <div className="rm-sum"><span><b>{outline.stations.length}</b> trạm</span><span>nghỉ <b>{total}</b> phút</span></div>
          <div className="rm-road" aria-live="polite">
            {roadLoading ? <span className="rm-loading"><i className="fa-solid fa-spinner fa-spin" /> Đang tính đường đi…</span>
              : road ? <><span><b>{Math.round(road.km)}</b> km · lái <b>{road.hours.toFixed(1)}</b> giờ</span><small>{road.traffic ? 'Theo Google, có giao thông theo giờ khởi hành' : 'Theo đường bộ, chưa tính giao thông'}</small></>
              : <><span>≈ <b>{Math.round(straightKm)}</b> km đường chim bay</span><small>Chưa lấy được đường bộ, đang vẽ nét thẳng</small></>}
          </div>
        </div>
        <div className="rm-legend"><span><i className="rm-pin rm-a">A</i> Điểm đón</span><span><i className="rm-pin rm-b">B</i> Điểm trả</span>{gate && <span><i className="rm-pin rm-g">⚑</i> Cửa khẩu</span>}<span><i className="rm-st" /> Trạm</span></div>
      </div>
    </Modal>
  )
}
