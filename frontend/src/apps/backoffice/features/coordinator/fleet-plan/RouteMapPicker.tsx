// Chọn trạm nghỉ trên bản đồ: có điểm đón, điểm trả, cửa khẩu (tuyến quốc tế) và các trạm của hệ thống.
// Bấm một trạm để chọn và đặt thời gian nghỉ ở góc trên bên trái; hệ thống tự xếp thứ tự trạm và vẽ đường đi qua các điểm.
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useMemo, useRef, useState } from 'react'
import { MAX_CONTINUOUS_HOURS, MIN_REST_MINUTES } from '@shared/config/booking-rules'
import { countryCodeOf, type GeoPoint, type TransitStation } from '@shared/config/network'
import { routeOutline } from '@shared/lib/booking'
import { MAX_ROUTE_STOPS, ROADSIDE, restTitle, stationsForBooking } from '@shared/lib/routePreview'
import type { RestStop } from '@shared/types/booking'
import { Modal } from '@shared/ui/Modal'
import { addBaseLayer, BASE_MAP_OPTIONS } from '@shared/ui/baseMap'
import './RouteMapPicker.css'

type Point = GeoPoint & { name: string; area?: string }
const MINUTES = [MIN_REST_MINUTES, 45, 60, 90, 120]
const DEFAULT_MINUTES = 45
const short = (name: string) => name.split(' — ')[0]
const pin = (cls: string, label: string) => L.divIcon({ className: 'rm-icon', html: `<span class="rm-pin ${cls}">${label}</span>`, iconSize: [30, 30], iconAnchor: [15, 15] })
const stationIcon = (order: number, active: boolean) => L.divIcon({
  className: 'rm-icon', iconSize: [34, 34], iconAnchor: [17, 17],
  html: order ? `<span class="rm-st rm-on${active ? ' rm-active' : ''}">${order}</span>` : '<span class="rm-st"></span>',
})
import { routesApi, type RoutePreviewResponse } from '@shared/api/routes'

const STOP_EVENTS = new Set(['REST', 'ROADSIDE_REST', 'OVERNIGHT'])

interface Props {
  origin: Point
  dest: Point
  gate?: Point
  international: boolean // đơn nội địa chỉ thấy trạm ở Việt Nam
  value: RestStop[]
  // Tính lộ trình xe tải thật cho các trạm đang chọn (cùng cách tính với trang lập kế hoạch)
  estimate?: (stations: RestStop[], signal: AbortSignal) => Promise<RoutePreviewResponse>
  onApply: (rests: RestStop[]) => void
  onClose: () => void
}

export function RouteMapPicker({ origin, dest, gate, international, value, estimate, onApply, onClose }: Props) {
  const box = useRef<HTMLDivElement>(null)
  const map = useRef<L.Map | null>(null)
  const layer = useRef<L.LayerGroup | null>(null)
  const [catalog, setCatalog] = useState<TransitStation[]>([])
  const [locationsError, setLocationsError] = useState<string>()

  // Trạm nghỉ do Admin quản lý, có tọa độ trong danh mục địa điểm.
  useEffect(() => {
    let cancelled = false
    routesApi.listLocations({ type: 'RestStop' }).then(locs => {
      if (cancelled) return
      setCatalog(locs.flatMap(loc => loc.status === 'ACTIVE' && loc.latitude != null && loc.longitude != null
        ? [{ name: loc.locationName, lat: loc.latitude, lng: loc.longitude, area: loc.province || loc.city, country: countryCodeOf(loc.country) }]
        : []))
      setLocationsError(undefined)
    }).catch((error: unknown) => {
      if (cancelled) return
      setLocationsError(error instanceof Error ? error.message : 'Không tải được danh sách trạm nghỉ.')
    })
    return () => { cancelled = true }
  }, [])

  const stations = useMemo(() => stationsForBooking(catalog, international), [catalog, international])

  // Giữ lại các trạm đã có trong value (kể cả trạm từ corridor)
  const allAvailableStations = useMemo(() => {
    const list: Point[] = [...stations]
    value.forEach(v => {
      if (v.lat != null && v.lng != null && !list.some(s => s.name === v.name)) {
        list.push({ name: v.name, lat: v.lat, lng: v.lng, area: 'Hành lang chuẩn' })
      }
    })
    return list
  }, [stations, value])

  // Các trạm đang chọn và số phút nghỉ của từng trạm
  const [picked, setPicked] = useState<Map<string, number>>(() => new Map(value.map(r => [r.name, r.minutes])))
  const [active, setActive] = useState<string | null>(null)
  const [pendingMinutes, setPendingMinutes] = useState<Map<string, number>>(new Map())

  const chosen = useMemo(() => allAvailableStations.filter(s => picked.has(s.name)), [allAvailableStations, picked])
  const outline = useMemo(() => routeOutline(origin, gate, dest, chosen), [origin, gate, dest, chosen])
  const order = (name: string) => outline.stations.findIndex(s => s.name === name) + 1
  const chosenRests = useMemo<RestStop[]>(
    () => outline.stations.map((s, i) => ({ afterLeg: i + 1, name: s.name, minutes: picked.get(s.name) ?? DEFAULT_MINUTES, lat: s.lat, lng: s.lng })),
    [outline, picked]
  )

  // Keep the map instance stable when page data refreshes with the same coordinates.
  const routeKey = `${origin.lat.toFixed(4)},${origin.lng.toFixed(4)}_${dest.lat.toFixed(4)},${dest.lng.toFixed(4)}_${gate ? `${gate.lat.toFixed(4)},${gate.lng.toFixed(4)}` : 'none'}`

  // Recalculate truck times when the route or a selected stop's duration changes.
  const estimateKey = JSON.stringify([routeKey, chosenRests.map(r => [r.name, r.minutes, r.lat, r.lng])])
  const [road, setRoad] = useState<{ key: string; preview?: RoutePreviewResponse; error?: string }>()
  useEffect(() => {
    if (!estimate) return
    const controller = new AbortController()
    const timer = setTimeout(() => {
      estimate(chosenRests, controller.signal)
        .then(preview => { if (!controller.signal.aborted) setRoad({ key: estimateKey, preview }) })
        .catch(e => { if (!controller.signal.aborted) setRoad({ key: estimateKey, error: e instanceof Error ? e.message : String(e) }) })
    }, 600)
    return () => { clearTimeout(timer); controller.abort() }
  }, [estimateKey]) // eslint-disable-line react-hooks/exhaustive-deps
  const roadNow = road?.key === estimateKey ? road : undefined
  const roadLoading = !!estimate && !roadNow
  // Chỗ nghỉ hệ thống sẽ thêm (ngoài các trạm đang chọn) để không lái quá giờ liên tục
  const added = (roadNow?.preview?.schedule ?? [])
    .filter(e => STOP_EVENTS.has(e.type) && (e.type === 'ROADSIDE_REST' || !picked.has(e.place)))
    .map(e => restTitle(e.type === 'ROADSIDE_REST' && !e.place.startsWith(ROADSIDE) ? `${ROADSIDE} gần ${e.place}` : e.place))

  // Cửa khẩu chiếm một chỗ trong giới hạn điểm của máy tính đường
  const maxStations = gate ? MAX_ROUTE_STOPS - 1 : MAX_ROUTE_STOPS
  const full = picked.size >= maxStations
  // Khi bấm vào trạm trên bản đồ: CHỈ mở thông tin trạm để xem, KHÔNG tự ý thêm trạm mới
  const choose = (name: string) => {
    setActive(name)
  }
  const addStation = (name: string, minutes: number) => {
    setPicked(m => new Map(m).set(name, minutes))
  }
  const setMinutes = (name: string, minutes: number) => setPicked(m => new Map(m).set(name, minutes))
  const remove = (name: string) => { setPicked(m => { const n = new Map(m); n.delete(name); return n }); setActive(null) }

  // Dựng bản đồ một lần duy nhất khi mở modal
  useEffect(() => {
    if (!box.current) return
    const m = L.map(box.current, { ...BASE_MAP_OPTIONS, zoomControl: false, attributionControl: true })
    L.control.zoom({ position: 'topright' }).addTo(m)
    addBaseLayer(m)
    // Mở ra đã zoom vào tuyến (điểm đón, điểm trả, cửa khẩu); các trạm xa hơn thì kéo bản đồ để thấy
    const route = [origin, dest, ...(gate ? [gate] : [])]
    m.fitBounds(L.latLngBounds(route.map(p => [p.lat, p.lng] as [number, number])), { padding: [90, 90], maxZoom: 11 })
    layer.current = L.layerGroup().addTo(m)
    map.current = m
    // Cửa sổ vừa mở có hiệu ứng nên cần tính lại kích thước bản đồ
    const timers = [60, 350].map(ms => setTimeout(() => m.invalidateSize(), ms))
    return () => {
      timers.forEach(clearTimeout)
      if (map.current) {
        try {
          map.current.stop()
          map.current.closePopup()
          map.current.closeTooltip()
          if (layer.current) {
            layer.current.eachLayer(l => {
              try { (l as L.Marker).unbindTooltip?.() } catch {}
            })
            layer.current.clearLayers()
          }
          map.current.remove()
        } catch (e) {
          console.warn('Map cleanup safely handled', e)
        }
        map.current = null
        layer.current = null
      }
    }
  }, [routeKey]) // eslint-disable-line react-hooks/exhaustive-deps

  // Vẽ lại đường và các điểm mỗi khi đổi trạm được chọn (giữ nguyên kích thước và vị trí bản đồ hiện tại)
  useEffect(() => {
    const g = layer.current
    if (!g) return
    try {
      g.eachLayer(l => {
        try { (l as L.Marker).unbindTooltip?.() } catch {}
      })
    } catch {}
    g.clearLayers()
    // Có đường xe tải thật thì vẽ nét liền; đang tính thì nối thẳng nét đứt các điểm đã chọn
    const truckPath = roadNow?.preview?.path
    const line = truckPath?.length ? truckPath : outline.path.map(p => [p.lat, p.lng] as [number, number])
    const chosenAny = outline.stations.length > 0
    L.polyline(line, { color: chosenAny ? '#ea580c' : '#64748b', weight: 12, opacity: 0.18, interactive: false }).addTo(g)
    L.polyline(line, { color: chosenAny ? '#ea580c' : '#64748b', weight: 5, opacity: 0.95, dashArray: truckPath?.length ? undefined : '6 8', interactive: false }).addTo(g)
    allAvailableStations.forEach(s => {
      const n = order(s.name)
      L.marker([s.lat, s.lng], { icon: stationIcon(n, s.name === active), zIndexOffset: n ? 500 : (s.name === active ? 400 : 0), title: s.name })
        .bindTooltip(`${s.name}${picked.has(s.name) ? ` · nghỉ ${picked.get(s.name)} phút` : ''}`, { direction: 'top', offset: [0, -12], opacity: 0.95 })
        .on('click', () => choose(s.name)).addTo(g)
    })
    L.marker([origin.lat, origin.lng], { icon: pin('rm-a', 'A'), zIndexOffset: 900 }).bindTooltip(`Điểm đón: ${short(origin.name)}`, { direction: 'top', offset: [0, -14], opacity: 0.95 }).addTo(g)
    L.marker([dest.lat, dest.lng], { icon: pin('rm-b', 'B'), zIndexOffset: 900 }).bindTooltip(`Điểm trả: ${short(dest.name)}`, { direction: 'top', offset: [0, -14], opacity: 0.95 }).addTo(g)
    if (gate) L.marker([gate.lat, gate.lng], { icon: pin('rm-g', '⚑'), zIndexOffset: 900 }).bindTooltip(`Cửa khẩu ${gate.name}`, { direction: 'top', offset: [0, -14], opacity: 0.95 }).addTo(g)
  }, [allAvailableStations, outline, picked, active, roadNow]) // eslint-disable-line react-hooks/exhaustive-deps

  const apply = () => {
    onApply(chosenRests)
    onClose()
  }
  const act = active ? allAvailableStations.find(s => s.name === active) : undefined
  const total = [...picked.values()].reduce((t, x) => t + x, 0)

  return (
    <Modal wide onClose={onClose} title="Chọn trạm nghỉ" subtitle="Bấm vào các chấm trạm trên bản đồ; hệ thống tự xếp thứ tự và vẽ đường đi qua các điểm."
      footer={<><button className="btn btn-ghost" onClick={onClose}>Hủy</button><button className="btn btn-primary" onClick={apply}><i className="fa-solid fa-check" /> Áp dụng {outline.stations.length} trạm{added.length ? ` (+${added.length} nghỉ theo quy định)` : ''}</button></>}>
      <div className="rm-wrap">
        <div ref={box} className="rm-map" role="application" aria-label="Bản đồ chọn trạm nghỉ" />
        <div className="rm-panel">
          {act ? (
            picked.has(act.name) ? (
              <>
                <div className="rm-head"><span className="rm-num">{order(act.name)}</span><div><b>{act.name}</b><small>{act.area}</small></div></div>
                <span className="rm-label">Thời gian nghỉ tại trạm</span>
                <div className="rm-chips">{MINUTES.map(m => <button key={m} type="button" className={picked.get(act.name) === m ? 'on' : ''} onClick={() => setMinutes(act.name, m)}>{m} phút</button>)}</div>
                <button type="button" className="rm-remove" onClick={() => remove(act.name)}><i className="fa-regular fa-trash-can" /> Bỏ trạm này</button>
              </>
            ) : (
              <>
                <div className="rm-head">
                  <span className="rm-num" style={{ background: '#2563eb', color: '#fff' }}>
                    <i className="fa-solid fa-location-dot" />
                  </span>
                  <div><b>{act.name}</b><small>{act.area}</small></div>
                </div>
                <span className="rm-label">Chọn thời gian nghỉ dự kiến</span>
                <div className="rm-chips">
                  {MINUTES.map(m => (
                    <button
                      key={m}
                      type="button"
                      className={(pendingMinutes.get(act.name) ?? DEFAULT_MINUTES) === m ? 'on' : ''}
                      onClick={() => setPendingMinutes(prev => new Map(prev).set(act.name, m))}
                    >
                      {m} phút
                    </button>
                  ))}
                </div>
                <button
                  type="button"
                  className="rm-add-btn"
                  disabled={full}
                  onClick={() => addStation(act.name, pendingMinutes.get(act.name) ?? DEFAULT_MINUTES)}
                >
                  <i className="fa-solid fa-plus" /> Thêm trạm này vào lộ trình
                </button>
                {full && <p role="alert" className="rm-limit">Tối đa {maxStations} trạm cho một chuyến.</p>}
              </>
            )
          ) : <p className="rm-hint"><i className="fa-solid fa-hand-pointer" /> Bấm vào một trạm (chấm trắng viền xanh) để xem thông tin và chọn thêm vào lộ trình.</p>}
          <div className="rm-sum"><span><b>{outline.stations.length}</b>/{maxStations} trạm</span><span>nghỉ <b>{total}</b> phút</span></div>
          <div className="rm-road" aria-live="polite">
            {roadNow?.preview ? (
              <>
                <span><b>{Math.round(roadNow.preview.km)}</b> km · <b>{roadNow.preview.driveHours.toFixed(1)}</b> giờ lái xe tải</span>
                {added.length > 0 && (
                  <small>
                    Hệ thống sẽ thêm {added.length} chỗ nghỉ để không lái quá {MAX_CONTINUOUS_HOURS} giờ liên tục:{' '}
                    {added.join('; ')}.
                  </small>
                )}
              </>
            ) : roadNow?.error ? (
              <small role="alert">{roadNow.error}</small>
            ) : (
              <small>{roadLoading ? 'Đang tính đường xe tải…' : 'Nét đứt chỉ nối các điểm đã chọn.'}</small>
            )}
          </div>
          {locationsError && <p role="alert">{locationsError}</p>}
          {!locationsError && stations.length === 0 && <p>Chưa có trạm nghỉ đang hoạt động có tọa độ.</p>}
        </div>
        <div className="rm-legend"><span><i className="rm-pin rm-a">A</i> Điểm đón</span><span><i className="rm-pin rm-b">B</i> Điểm trả</span>{gate && <span><i className="rm-pin rm-g">⚑</i> Cửa khẩu</span>}<span><i className="rm-st" /> Trạm</span></div>
      </div>
    </Modal>
  )
}
