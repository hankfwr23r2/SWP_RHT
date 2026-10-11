// Lập phương án sự cố trên bản đồ (Flow 5): vị trí xe, trạm nghỉ gần nhất, điểm cứu hộ gần chỗ xe, đường thay thế khi tắc nghẽn.
// Coordinator bấm chọn, hệ thống tự vẽ đường đi theo đường bộ và gợi ý giờ đến mới.
import { useMemo, useState } from 'react'
import { useAuth } from '@shared/auth/AuthContext'
import { INCIDENT_ACTION, INCIDENT_KIND, MIN_REST_MINUTES } from '@shared/config/booking-rules'
import { AVG_SPEED_KMH } from '@shared/config/public-pricing'
import { GATES, RESCUE_POINTS, TRANSIT_STATIONS, type GeoPoint } from '@shared/config/network'
import { arrivalOf, bookingPath, findLocation, incidentActionsFor, nearestTo, pointsAhead } from '@shared/lib/booking'
import { formatDateTime } from '@shared/lib/format'
import { haversineKm } from '@shared/lib/pricing'
import { bookingsApi } from '@shared/services/bookings'
import { thinPath, type RoadRoute } from '@shared/services/routing'
import { useRoadAlternatives, useRoadRoute } from '@shared/services/useRoadRoute'
import type { Booking, Incident, PlanLine } from '@shared/types/booking'
import { Modal } from '@shared/ui/Modal'
import { useToast } from '@shared/ui/toast'
import { IncidentMap, type MapLine } from '../../../shared/IncidentMap'
import s from '../../../shared/booking.module.css'

const MINUTES = [MIN_REST_MINUTES, 45, 60, 90, 120]
const toLocal = (ms: number) => new Date(ms - new Date().getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
const lngLat = (p: GeoPoint): [number, number] => [p.lat, p.lng]
// Đường nối thẳng khi chưa lấy được đường bộ (km nhân hệ số đường vòng 1,3)
const estLine = (a: GeoPoint, z: GeoPoint): PlanLine => { const km = haversineKm(a, z) * 1.3; return { path: [lngLat(a), lngLat(z)], km: Math.round(km * 10) / 10, hours: Math.round((km / AVG_SPEED_KMH) * 100) / 100 } }
const lineOf = (r: RoadRoute | null | undefined, a: GeoPoint, z: GeoPoint): PlanLine => (r ? { path: thinPath(r.path), km: Math.round(r.km * 10) / 10, hours: Math.round(r.hours * 100) / 100 } : estLine(a, z))
const chainHours = (pts: GeoPoint[]) => pts.slice(1).reduce((t, p, i) => t + estLine(pts[i], p).hours, 0)
const hm = (h: number) => (h < 1 ? `${Math.round(h * 60)} phút` : `${h.toFixed(1)} giờ`)

export function IncidentPlanner({ item, onClose, onDone }: { item: { b: Booking; i: Incident }; onClose: () => void; onDone: () => void }) {
  const toast = useToast()
  const { session } = useAuth()
  const { b, i } = item
  const kind = i.kind
  const action = incidentActionsFor(kind)[0]
  const loc = i.location
  const [now] = useState(() => Date.now())
  const origin = findLocation(b.origin.id)!, dest = findLocation(b.dest.id)!
  const gate = b.gate ? GATES.find(g => g.name === b.gate) : undefined

  // Điểm xe còn phải đi qua và các trạm / điểm cứu hộ xếp theo khoảng cách tới chỗ xe
  const path = useMemo(() => bookingPath(b), [b])
  const ahead = useMemo(() => pointsAhead(path, loc), [path, loc])
  const next: GeoPoint = ahead[0] ?? dest
  const stations = useMemo(() => nearestTo(loc, TRANSIT_STATIONS), [loc])
  const rescues = useMemo(() => nearestTo(loc, RESCUE_POINTS), [loc])

  const [stationName, setStationName] = useState(i.plan?.station ?? stations[0].name)
  const [rescueName, setRescueName] = useState(i.plan?.rescue?.name ?? rescues[0].name)
  const [rest, setRest] = useState(i.plan?.restMinutes ?? 60)
  const [altIdx, setAltIdx] = useState(0)
  const [note, setNote] = useState(i.plan?.note ?? '')
  const [allStations, setAllStations] = useState(false) // xem mọi trạm, không chỉ các trạm gần xe nhất
  const [etaText, setEtaText] = useState<string | null>(null) // null = dùng giờ hệ thống gợi ý
  const [busy, setBusy] = useState(false)

  const stationPt = TRANSIT_STATIONS.find(x => x.name === stationName)!
  const rescuePt = RESCUE_POINTS.find(x => x.name === rescueName)!
  const horses = kind !== 'traffic_jam'

  // Đường bộ thật: xe → trạm, cứu hộ → xe, các đường thay thế tới điểm kế tiếp, và đường đi tiếp sau đó
  const toStation = useRoadRoute(horses ? [loc, stationPt] : undefined, now)
  const rescueRoad = useRoadRoute(kind === 'vehicle_breakdown' ? [rescuePt, loc] : undefined, now)
  const alts = useRoadAlternatives(kind === 'traffic_jam' ? loc : undefined, kind === 'traffic_jam' ? next : undefined, now)
  const afterPts = horses ? [stationPt, ...ahead] : ahead
  const afterRoad = useRoadRoute(afterPts.length > 1 ? afterPts : undefined, now)
  const loading = toStation.loading || rescueRoad.loading || alts.loading || afterRoad.loading

  const altList: RoadRoute[] = alts.routes
  const altChosen = altList[Math.min(altIdx, Math.max(0, altList.length - 1))] as RoadRoute | undefined
  const toStationLine = horses ? lineOf(toStation.route, loc, stationPt) : undefined
  const rescueLine = kind === 'vehicle_breakdown' ? lineOf(rescueRoad.route, rescuePt, loc) : undefined
  const detour = kind === 'traffic_jam' ? lineOf(altChosen, loc, next) : undefined
  const afterHours = afterRoad.route?.hours ?? chainHours(afterPts)

  // Giờ đến mới gợi ý = các đoạn đường của phương án + thời gian nghỉ + phần đường còn lại; Coordinator sửa được
  const hours = (rescueLine?.hours ?? 0) + (toStationLine?.hours ?? 0) + (horses ? rest / 60 : 0) + (detour?.hours ?? 0) + afterHours
  const suggested = Math.ceil((now + hours * 3_600_000) / 300_000) * 300_000
  const etaValue = etaText ?? toLocal(suggested)
  const newEta = new Date(etaValue).getTime()
  const oldEta = arrivalOf(b.route)

  const lines: MapLine[] = [
    { path: path.map(lngLat), color: '#94a3b8', dashed: true, weight: 3, faded: true, label: 'Lộ trình đã lập' },
    ...(rescueLine ? [{ path: rescueLine.path, color: '#059669', flow: true, label: `Cứu hộ tới chỗ xe: ${rescueLine.km} km` }] : []),
    ...(toStationLine ? [{ path: toStationLine.path, color: '#ea580c', flow: true, weight: 6, label: `Đưa ngựa tới ${stationName}: ${toStationLine.km} km` }] : []),
    ...(kind === 'traffic_jam'
      ? (altList.length ? altList.map((r, idx): MapLine => ({ path: r.path, color: idx === altIdx ? '#2563eb' : '#64748b', weight: idx === altIdx ? 6 : 4, flow: idx === altIdx, faded: idx !== altIdx, label: `Đường ${idx + 1}: ${Math.round(r.km)} km · ${hm(r.hours)}`, onClick: () => setAltIdx(idx) }))
        : detour ? [{ path: detour.path, color: '#2563eb', weight: 5, dashed: true, label: 'Đường nối thẳng (chưa lấy được đường thay thế)' }] : [])
      : []),
    ...(afterRoad.route ? [{ path: afterRoad.route.path, color: '#2563eb', dashed: true, weight: 3, faded: true, label: 'Đường đi tiếp tới điểm trả' }] : []),
  ]

  const send = async () => {
    setBusy(true)
    try {
      await bookingsApi.planIncident(b.id, i.id, session!.name, {
        action, note, newEta,
        ...(horses ? { station: stationName, restMinutes: rest, toStation: toStationLine } : {}),
        ...(kind === 'vehicle_breakdown' ? { rescue: { name: rescuePt.name, phone: rescuePt.phone, lat: rescuePt.lat, lng: rescuePt.lng }, rescueLine } : {}),
        ...(kind === 'traffic_jam' ? { detour } : {}),
      })
      toast(`Đã trình phương án ${i.id} lên Quản lý`)
      onDone()
    } catch (e) { toast(e instanceof Error ? e.message : 'Không gửi được', 'error'); setBusy(false) }
  }

  // Danh sách chọn: 3 điểm gần nhất (cứu hộ), và điểm đang chọn nếu nằm ngoài
  const top = <T extends { name: string }>(list: (T & { km: number })[], chosen: string) => [...list.slice(0, 3), ...list.slice(3).filter(x => x.name === chosen)]
  // Trạm nghỉ gần chỗ xe nhất đánh số 1, 2, 3… cả trên bản đồ lẫn trong danh sách, như lúc lập lộ trình; bấm vào bản đồ hoặc danh sách đều chọn được
  const NEAR = 5
  const shownStations = allStations ? stations : stations.slice(0, NEAR)
  const stationOptions = (
    <>
      <div className="im-list" style={allStations ? { maxHeight: 260, overflowY: 'auto' } : undefined}>
        {shownStations.map((x, idx) => (
          <button key={x.name} type="button" className={`im-opt ${x.name === stationName ? 'on' : ''}`} onClick={() => setStationName(x.name)}>
            <b>{idx < NEAR && <span className="im-rank">{idx + 1}</span>}{x.name}</b><small>{x.area} · cách xe {x.km.toFixed(0)} km (đường chim bay)</small>{idx === 0 && <span className="im-tag">Gần nhất</span>}
          </button>
        ))}
        {!allStations && stationName && !shownStations.some(x => x.name === stationName) && (
          <button type="button" className="im-opt on"><b>{stationName}</b><small>Trạm đang chọn, ngoài {NEAR} trạm gần nhất</small></button>
        )}
      </div>
      <button type="button" className="btn btn-ghost btn-sm" style={{ marginTop: 6 }} onClick={() => setAllStations(v => !v)}>
        {allStations ? `Chỉ xem ${NEAR} trạm gần nhất` : `Xem tất cả ${stations.length} trạm nghỉ`}
      </button>
    </>
  )
  const restChips = (
    <div><h4>Thời gian nghỉ ngựa tại trạm</h4><div className="rm-chips" style={{ marginTop: 6 }}>{MINUTES.map(m => <button key={m} type="button" className={rest === m ? 'on' : ''} onClick={() => setRest(m)}>{m} phút</button>)}</div></div>
  )

  return (
    <Modal wide onClose={onClose} title={`Lập phương án ${i.id}`} subtitle={`${b.id} · ${INCIDENT_KIND[kind].label} · xe ${i.tripId}`}
      footer={<><button className="btn btn-ghost" onClick={onClose}>Đóng</button><button className="btn btn-primary" disabled={busy || Number.isNaN(newEta)} onClick={send}><i className="fa-solid fa-paper-plane" /> Trình Quản lý duyệt</button></>}>
      {i.rejection && <div className="alert alert-danger" style={{ marginBottom: 10 }}><i className="fa-solid fa-rotate-left" /><div><b>Quản lý trả về:</b> {i.rejection.reason}</div></div>}
      <IncidentMap
        height="min(64vh, 600px)" location={loc} origin={{ ...origin, name: b.origin.name }} dest={{ ...dest, name: b.dest.name }} gate={gate ? { ...gate } : undefined}
        stations={horses ? stations : []} nearest={NEAR} rescues={kind === 'vehicle_breakdown' ? RESCUE_POINTS : []}
        selectedStation={horses ? stationName : undefined} selectedRescue={kind === 'vehicle_breakdown' ? rescueName : undefined}
        onStation={setStationName} onRescue={setRescueName} lines={lines}
      >
        <div className="im-head"><span className="im-icon"><i className={`fa-solid ${INCIDENT_KIND[kind].icon}`} /></span><div><b>{INCIDENT_KIND[kind].label}</b><small>Xe {i.tripId} · báo {formatDateTime(i.reportedAt)}</small></div></div>
        <p className="im-note">{INCIDENT_ACTION[action]}.</p>

        {kind === 'vehicle_breakdown' && (
          <div>
            <h4>1. Gọi cứu hộ gần chỗ xe</h4>
            <div className="im-list" style={{ marginTop: 6 }}>
              {top(rescues, rescueName).map((x, idx) => (
                <button key={x.name} type="button" className={`im-opt ${x.name === rescueName ? 'on' : ''}`} onClick={() => setRescueName(x.name)}>
                  <b>{x.name}</b><small>{x.phone} · cách xe {x.km.toFixed(0)} km</small>{idx === 0 && x.name === rescues[0].name && <span className="im-tag">Gần nhất</span>}
                </button>
              ))}
            </div>
            {rescueLine && <div className="im-road"><span>Cứu hộ tới chỗ xe: <b>{rescueLine.km} km · {hm(rescueLine.hours)}</b></span></div>}
          </div>
        )}
        {horses && (
          <>
            <div><h4>{kind === 'vehicle_breakdown' ? '2. Chọn trạm nghỉ gần chỗ xe để đưa ngựa tới' : 'Chọn trạm nghỉ gần chỗ xe'}</h4><div style={{ marginTop: 6 }}>{stationOptions}</div></div>
            {toStationLine && <div className="im-road"><span>Đưa ngựa tới trạm: <b>{toStationLine.km} km · {hm(toStationLine.hours)}</b></span><small>{toStation.route ? (toStation.route.traffic ? 'Theo Google, có giao thông' : 'Theo đường bộ') : 'Đường nối thẳng, chưa lấy được đường bộ'}</small></div>}
            {restChips}
          </>
        )}
        {kind === 'traffic_jam' && (
          <div>
            <h4>Lộ trình thay thế tới {ahead[0] ? 'điểm kế tiếp' : 'điểm trả'}</h4>
            <div className="im-list" style={{ marginTop: 6 }}>
              {altList.length ? altList.map((r, idx) => (
                <button key={idx} type="button" className={`im-opt ${idx === altIdx ? 'on' : ''}`} onClick={() => setAltIdx(idx)}>
                  <b>Đường {idx + 1}: {Math.round(r.km)} km · {hm(r.hours)}</b><small>{r.traffic ? 'Theo Google, có giao thông' : 'Theo đường bộ, chưa tính giao thông'}</small>{idx === 0 && <span className="im-tag">Nhanh nhất</span>}
                </button>
              )) : <p className="im-note">{alts.loading ? 'Đang tìm đường thay thế…' : 'Chưa lấy được đường thay thế, đang dùng đường nối thẳng.'}</p>}
            </div>
            <p className="im-note" style={{ marginTop: 6 }}>Giữ nguyên cửa khẩu đã chốt{gate ? ` (${gate.name})` : ''}; chỉ đổi đường đi tới điểm kế tiếp.</p>
          </div>
        )}
        {loading && <p className="im-note"><i className="fa-solid fa-spinner fa-spin" /> Đang tính đường đi…</p>}
      </IncidentMap>

      <div className={s.form2} style={{ marginTop: 14, gridTemplateColumns: 'repeat(2, minmax(0, 1fr))' }}>
        <div className="form-group">
          <label htmlFor="pe">Giờ đến đích mới</label>
          <input id="pe" type="datetime-local" className="form-control" value={etaValue} onChange={e => setEtaText(e.target.value)} />
          <div className="form-hint">{etaText === null ? 'Hệ thống gợi ý theo đường vừa vẽ và thời gian nghỉ.' : <button type="button" className="btn btn-ghost btn-sm" onClick={() => setEtaText(null)}>Dùng giờ hệ thống gợi ý</button>}{oldEta && !Number.isNaN(newEta) ? ` Chậm ${hm(Math.max(0, (newEta - oldEta) / 3_600_000))} so với dự kiến cũ (${formatDateTime(oldEta)}).` : ''}</div>
        </div>
        <div className="form-group"><label htmlFor="pn">Ghi chú</label><input id="pn" className="form-control" placeholder="Số điện thoại liên hệ, lưu ý cho tài xế" value={note} onChange={e => setNote(e.target.value)} /></div>
      </div>
    </Modal>
  )
}
