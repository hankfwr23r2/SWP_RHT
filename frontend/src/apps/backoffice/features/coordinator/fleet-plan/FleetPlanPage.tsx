// Coordinator: chốt xe và lộ trình một đơn trong một trang (PRD mục 2.4, nhánh B).
// Coordinator tự chọn xe (đơn đông ngựa thì nhiều xe), chia ngựa lên xe, lập lộ trình rồi xác nhận. Tài xế và hộ tống do Manager chọn sau.
import { ReadMore } from '@shared/ui/ReadMore'
import { useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { MAX_CONTINUOUS_HOURS, MIN_REST_MINUTES, VEHICLE_CLASS } from '@shared/config/booking-rules'
import { GATES, TRANSIT_STATIONS } from '@shared/config/network'
import { AVG_SPEED_KMH } from '@shared/config/public-pricing'
import { borderOutsideWindow, buildRoutePlan, clashOf, estimateBorderEta, findLocation, gatesFor, layoutLegs, routeKm, suggestGate, routeOutline, schedulesOf, validateRoutePlan, vehicleClassOf } from '@shared/lib/booking'
import { atHour } from '@shared/lib/dates'
import { formatClock, formatDate, formatDateTime } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import { vehiclesApi, type Vehicle } from '@shared/services/fleet'
import { useLoad } from '@shared/services/useLoad'
import type { Booking, RestStop, VehicleTrip } from '@shared/types/booking'
import { BookingStatusBadge } from '@shared/ui/BookingStatusBadge'
import { useToast } from '@shared/ui/toast'
import { HorseConfigList, History, ReviewChips, TripSummary } from '../../../shared/BookingParts'
import s from '../../../shared/booking.module.css'
import { FormSelect } from '@shared/ui/FormSelect'
import { ResourcePicker } from '../../../shared/ResourcePicker'
import { RouteMapPicker } from './RouteMapPicker'
import { roadNote } from '@shared/services/routing'
import { useRoadRoute } from '@shared/services/useRoadRoute'

const pad = (n: number) => String(n).padStart(2, '0')
const toLocal = (t: number) => { const d = new Date(t); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}` }
const fromLocal = (v: string) => (v ? new Date(v).getTime() : NaN)

type Draft = Pick<VehicleTrip, 'vehicleId' | 'horseIds'>

function Plan({ b, vehicles, all, onDone }: { b: Booking; vehicles: Vehicle[]; all: Booking[]; onDone: () => void }) {
  const toast = useToast()
  const navigate = useNavigate()
  const { session } = useAuth()
  const international = b.type === 'international'
  // Lịch các đơn khác đang giữ từng xe: để khóa xe trùng lịch và cho Coordinator biết xe bận gì
  const sched = useMemo(() => schedulesOf(all, b.id), [all, b.id])

  // ----- xe, ngựa -----
  // Chưa chốt phương án thì chưa có xe nào: bắt đầu với một chuyến trống chở cả đơn, Coordinator tự chọn xe (thêm xe nếu một xe không đủ chỗ)
  const [trips, setTrips] = useState<Draft[]>(b.trips?.length ? b.trips.map(t => ({ vehicleId: t.vehicleId, horseIds: t.horseIds })) : [{ vehicleId: '', horseIds: b.horses.map(h => h.horseId) }])
  const [note, setNote] = useState(b.plan?.note ?? '')
  const [working, setWorking] = useState(false)
  const [reason, setReason] = useState<string | null>(null) // không null = đang nhập lý do từ chối đơn
  const [mapOpen, setMapOpen] = useState(false) // popup bản đồ chọn trạm nghỉ
  const [picking, setPicking] = useState<number | null>(null) // chuyến đang mở popup chọn xe
  const setTrip = (i: number, patch: Partial<Draft>) => setTrips(ts => ts.map((t, j) => (j === i ? { ...t, ...patch } : t)))
  const moveHorse = (horseId: string, to: number) => setTrips(ts => ts.map((t, j) => ({ ...t, horseIds: j === to ? [...t.horseIds.filter(x => x !== horseId), horseId] : t.horseIds.filter(x => x !== horseId) })))
  const usedVehicles = new Set(trips.map(t => t.vehicleId))
  const tripErrors = trips.flatMap((t, i) => {
    const out: string[] = []
    const v = vehicles.find(x => x.id === t.vehicleId)
    if (!t.horseIds.length) out.push(`Xe ${i + 1} chưa có ngựa nào.`)
    if (!v) out.push(`Xe ${i + 1} chưa chọn xe.`)
    else if (t.horseIds.length > v.capacity) out.push(`Xe ${i + 1} chỉ có ${v.capacity} ngăn, đang xếp ${t.horseIds.length} ngựa.`)
    // Chọn trước khi lịch đổi (hoặc sửa phương án cũ): vẫn báo nếu trùng lịch
    const clash = v ? clashOf(sched.vehicles.get(t.vehicleId), b.departAt) : undefined
    if (v && clash) out.push(`Xe ${v.plate} trùng lịch với ${clash.order} (khởi hành ${formatDate(clash.departAt)}).`)
    return out
  })

  // Thêm một chuyến trống (chọn xe sau); bỏ một chuyến thì ngựa của chuyến đó chuyển sang chuyến đầu tiên còn lại
  const addTrip = () => setTrips(ts => [...ts, { vehicleId: '', horseIds: [] }])
  const removeTrip = (i: number) => setTrips(ts => {
    if (ts.length < 2) return ts
    const rest = ts.filter((_, j) => j !== i)
    rest[0] = { ...rest[0], horseIds: [...rest[0].horseIds, ...ts[i].horseIds] }
    return rest
  })
  // Chia đều ngựa cho các xe (ví dụ 6 ngựa mà không có xe 6 ngăn thì 2 xe, mỗi xe 3 ngựa)
  const splitEvenly = () => setTrips(ts => { const ids = ts.flatMap(t => t.horseIds); return ts.map((t, j) => ({ ...t, horseIds: ids.filter((_, k) => k % ts.length === j) })) })
  // ----- lộ trình (một lộ trình dùng chung cho mọi xe) -----
  // Cửa khẩu do Coordinator chọn (khách không chọn): mặc định là cửa khẩu có tổng quãng đường ngắn nhất
  const gates = gatesFor(b.origin, b.dest)
  const suggested = suggestGate(b.origin, b.dest)
  const [gate, setGate] = useState(b.gate ?? suggested ?? '')
  const origin = findLocation(b.origin.id), dest = findLocation(b.dest.id)
  const gateGeo = gate ? GATES.find(g => g.name === gate) : undefined
  const estHours = routeKm(b.origin, b.dest, gate || undefined) / AVG_SPEED_KMH // ước lượng khi chưa lấy được đường bộ
  const initial = useMemo(() => b.route ?? buildRoutePlan({ ...b, gate: gate || undefined }, atHour(new Date(b.departAt), 5)), [b]) // eslint-disable-line react-hooks/exhaustive-deps
  const [etd, setEtd] = useState(toLocal(initial.legs[0].departAt))
  const [rests, setRests] = useState<RestStop[]>(initial.rests)
  const [borderText, setBorderText] = useState(initial.borderEta ? toLocal(initial.borderEta) : '')
  const [borderTouched, setBorderTouched] = useState(!!b.route?.borderEta)
  const etdT = fromLocal(etd)
  // Thời gian lái lấy theo đường bộ thật (Google có giao thông theo giờ khởi hành, hoặc OSRM); chưa lấy được thì dùng ước lượng
  const stationsOnRoute = rests.flatMap(r => TRANSIT_STATIONS.filter(x => x.name === r.name))
  const roadPath = origin && dest ? routeOutline(origin, gateGeo, dest, stationsOnRoute).path : undefined
  const { route: road, loading: roadLoading } = useRoadRoute(roadPath, Number.isNaN(etdT) ? undefined : etdT)
  const driveHours = road?.hours ?? estHours
  // Gợi ý lại các trạm nghỉ theo cửa khẩu (đổi cửa khẩu hoặc bấm "Gợi ý lại")
  const resuggest = (g: string) => {
    const plan = buildRoutePlan({ ...b, gate: g || undefined }, Number.isNaN(etdT) ? atHour(new Date(b.departAt), 5) : etdT)
    setRests(plan.rests)
    setBorderTouched(false)
  }
  const pickGate = (g: string) => { setGate(g); resuggest(g) }
  const legs = useMemo(() => (Number.isNaN(etdT) ? initial.legs : layoutLegs(b.origin.name, b.dest.name, etdT, rests, driveHours)), [etdT, rests, b, driveHours, initial.legs])
  const borderEta = international ? (borderTouched ? fromLocal(borderText) : estimateBorderEta(legs)) : undefined
  const route = { legs, rests: rests.map((r, i) => ({ ...r, afterLeg: i + 1 })), borderEta: borderEta && !Number.isNaN(borderEta) ? borderEta : undefined }
  const { errors: routeErrors, warnings } = validateRoutePlan(route, international)
  if (Number.isNaN(etdT)) routeErrors.unshift('Nhập giờ khởi hành hợp lệ.')
  if (international && !gates.some(g => g.name === gate)) routeErrors.unshift('Chọn cửa khẩu cho tuyến này.')
  const errors = [...tripErrors, ...routeErrors]

  const confirm = async () => {
    setWorking(true)
    try {
      await bookingsApi.confirmPlan(b.id, session!.name, { trips, route, gate: international ? gate : undefined, note: note.trim() })
      toast(`Đã xác nhận ${trips.length} xe và lộ trình ${b.id}`)
      onDone()
      navigate('/coordinator/fleet-plan')
    } catch (e) { toast(e instanceof Error ? e.message : 'Không xác nhận được', 'error'); setWorking(false) }
  }

  const reject = async () => {
    setWorking(true)
    try {
      await bookingsApi.rejectOrder(b.id, session!.name, 'coordinator', reason ?? '')
      toast(`Đã từ chối ${b.id}, khách nhận được lý do`)
      onDone()
      navigate('/coordinator/fleet-plan')
    } catch (e) { toast(e instanceof Error ? e.message : 'Không từ chối được', 'error'); setWorking(false) }
  }

  return (
    <>
      {b.sentBack?.to === 'coordinator' && <div className="alert alert-warning"><i className="fa-solid fa-rotate-left" /><div><b>Quản lý trả lại đơn này:</b> {b.sentBack.reason} <span className="sub-text">({b.sentBack.by}, {formatDateTime(b.sentBack.at)})</span></div></div>}
      <div className="card">
        <div className="card-header"><h3><i className="fa-solid fa-truck" /> {trips.length > 1 ? `${trips.length} xe của đơn` : 'Xe của đơn'}</h3><span style={{ display: 'flex', gap: 8 }}>{trips.length > 1 && <button className="btn btn-outline btn-sm" onClick={splitEvenly}><i className="fa-solid fa-scale-balanced" /> Chia đều ngựa</button>}<button className="btn btn-outline btn-sm" onClick={addTrip}><i className="fa-solid fa-plus" /> Thêm xe</button></span></div>
        <ReadMore className={s.hint} text={'Bạn chọn xe cho đơn và chia ngựa lên các xe. Ưu tiên ít xe nhất: có xe đủ chỗ thì dùng một xe; nếu không (ví dụ 6 ngựa mà không còn xe 6 ngăn) thì bấm Thêm xe và chia ngựa, chẳng hạn 2 xe mỗi xe 3 ngựa. Tài xế và nhân viên hộ tống do Quản lý chọn sau, dựa vào số xe bạn đã chọn. Xe đã có đơn trùng lịch (ngày đi cách dưới 3 ngày) bị khóa và nêu rõ trùng với đơn nào.'} />
        {trips.map((t, i) => {
          const v = vehicles.find(x => x.id === t.vehicleId)
          return (
            <div key={i} className={s.pick} style={{ display: 'block', marginBottom: 12 }}>
              <div className={s.pickName} style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}><span>Xe {i + 1}{v ? ` · ${v.plate} (${VEHICLE_CLASS[vehicleClassOf(v.capacity)].label}, ${v.capacity} ngăn)` : ''}</span>{trips.length > 1 && <button className="btn btn-ghost btn-sm" onClick={() => removeTrip(i)}><i className="fa-solid fa-trash" /> Bỏ xe này</button>}</div>
              <div className="form-group" style={{ marginTop: 10, maxWidth: 420 }}>
                <label htmlFor={`vehicle${i}`}>Xe</label>
                <button id={`vehicle${i}`} type="button" className={`form-control ${s.vehBtn}`} onClick={() => setPicking(i)}>
                  <i className="fa-solid fa-truck" aria-hidden="true" />
                  <span key={t.vehicleId} className={s.vehPop}>{v ? <b>{v.plate}</b> : 'Chọn xe'}</span>
                  <em>{v ? 'Đổi' : 'Chọn'}</em>
                </button>
                {t.vehicleId && (() => { const booked = sched.vehicles.get(t.vehicleId); return <span className={`${s.busyNote} ${booked?.length ? s.busyOn : s.busyFree}`}><i className={`fa-solid ${booked?.length ? 'fa-calendar-check' : 'fa-circle-check'}`} />{booked?.length ? `Đang có ${booked.length} đơn khác: ${booked.slice(0, 2).map(x => `${x.order.slice(-4)} (${formatDate(x.departAt)})`).join(', ')}${booked.length > 2 ? '…' : ''}` : 'Rảnh, không bận đơn nào'}</span> })()}
              </div>
              <ul style={{ display: 'grid', gap: 6, marginTop: 8 }}>
                {t.horseIds.map(hid => {
                  const h = b.horses.find(x => x.horseId === hid)
                  return (
                    <li key={hid} style={{ display: 'flex', justifyContent: 'space-between', gap: 10, alignItems: 'center' }}>
                      <span><b>{h?.name}</b> <span className={s.sub}>Chip {h?.microchip}</span></span>
                      {trips.length > 1 && (
                        <FormSelect className="form-control" style={{ width: 150 }} aria-label={`Chuyển ${h?.name} sang xe`} value={i} onChange={e => moveHorse(hid, Number(e.target.value))}>
                          {trips.map((_, j) => <option key={j} value={j}>Xe {j + 1}</option>)}
                        </FormSelect>
                      )}
                    </li>
                  )
                })}
              </ul>
            </div>
          )
        })}
      </div>

      <div className="card">
        <div className="card-header"><h3><i className="fa-solid fa-route" /> Lộ trình chi tiết</h3><span className="sub-text">Tổng thời gian lái ≈ {driveHours.toFixed(1)} giờ ({roadNote(road, roadLoading)}) · {legs.length} chặng · dùng chung cho mọi xe</span></div>
        <div className="form-group" style={{ maxWidth: 320 }}><label htmlFor="etd" className="required">Giờ đón ngựa</label><input id="etd" type="datetime-local" className="form-control" value={etd} onChange={e => setEtd(e.target.value)} /></div>
        <div className="table-wrap">
          <table className="data-table">
            <thead><tr><th>Chặng</th><th>Từ → Đến</th><th>Khởi hành</th><th>Đến</th><th className="text-right">Lái liên tục</th></tr></thead>
            <tbody>
              {legs.map(l => {
                const h = (l.arriveAt - l.departAt) / 3_600_000
                return <tr key={l.no}><td className="font-semibold">{l.no}</td><td>{l.from} → {l.to}</td><td className="nowrap">{formatDateTime(l.departAt)}</td><td className="nowrap">{formatClock(l.arriveAt)}</td><td className="text-right nowrap" style={{ color: h > MAX_CONTINUOUS_HOURS ? 'var(--red)' : undefined, fontWeight: 600 }}>{h.toFixed(1)} giờ</td></tr>
              })}
            </tbody>
          </table>
        </div>
      </div>

      {international && (
        <div className="card">
          <div className="card-header"><h3><i className="fa-solid fa-flag" /> Cửa khẩu</h3><span className="sub-text">Khách không chọn cửa khẩu, bạn chọn theo lộ trình</span></div>
          <div className="form-group" style={{ maxWidth: 420, margin: 0 }}>
            <label htmlFor="gate" className="required">Cửa khẩu đi qua</label>
            <FormSelect id="gate" className="form-control" value={gate} onChange={e => pickGate(e.target.value)}>
              {gates.map(g => <option key={g.name} value={g.name}>{g.name}{g.name === suggested ? ' (tối ưu: quãng đường ngắn nhất)' : ` · ${routeKm(b.origin, b.dest, g.name)} km`}</option>)}
            </FormSelect>
            <div className="form-hint">Chốt lộ trình xong, cửa khẩu bị khóa. Đổi cửa khẩu thì hệ thống gợi ý lại các trạm nghỉ.</div>
          </div>
        </div>
      )}

      <div className="card">
        <div className="card-header"><h3><i className="fa-solid fa-location-dot" /> Trạm nghỉ</h3><span style={{ display: 'flex', gap: 8 }}><button className="btn btn-primary btn-sm" onClick={() => setMapOpen(true)}><i className="fa-solid fa-map-location-dot" /> Chọn trên bản đồ</button><button className="btn btn-outline btn-sm" onClick={() => resuggest(gate)}><i className="fa-solid fa-wand-magic-sparkles" /> Gợi ý lại</button></span></div>
        <p className={s.hint} style={{ marginBottom: 10 }}>Các điểm xe đi qua giữa điểm đón và điểm trả{international ? ', theo cửa khẩu đã chọn' : ''}. Bấm <b>Chọn trên bản đồ</b> để chọn trạm và đặt thời gian nghỉ; hệ thống tự xếp thứ tự và vẽ đường đi. Ngựa không đi liên tục quá {MAX_CONTINUOUS_HOURS} giờ, mỗi trạm nghỉ tối thiểu {MIN_REST_MINUTES} phút.</p>
        {rests.length ? (
          <ol className={s.stopList}>
            {rests.map((r, i) => (
              <li key={r.name + i}>
                <button type="button" className={s.stopItem} onClick={() => setMapOpen(true)} title="Mở bản đồ để đổi trạm hoặc thời gian nghỉ">
                  <span className={s.stopNum}>{i + 1}</span>
                  <span className={s.stopName}><b>{r.name}</b><small>{TRANSIT_STATIONS.find(x => x.name === r.name)?.area ?? 'Trạm ngoài danh mục'}</small></span>
                  <span className={s.stopMin}><i className="fa-regular fa-clock" /> nghỉ {r.minutes} phút</span>
                </button>
                <button className={s.iconBtn} aria-label={`Bỏ trạm ${r.name}`} onClick={() => setRests(x => x.filter((_, j) => j !== i).map((y, j) => ({ ...y, afterLeg: j + 1 })))}><i className="fa-solid fa-xmark" /></button>
              </li>
            ))}
          </ol>
        ) : <div className={s.stopEmpty}><i className="fa-solid fa-map" /> Chưa chọn trạm nào. Bấm <b>Chọn trên bản đồ</b> hoặc <b>Gợi ý lại</b>.</div>}
      </div>

      {international && (
        <div className="card">
          <div className="card-header"><h3><i className="fa-solid fa-flag" /> Giờ tới cửa khẩu {gate}</h3></div>
          <div className="form-group" style={{ maxWidth: 320, margin: 0 }}>
            <label htmlFor="border" className="required">Giờ dự kiến tới cửa khẩu</label>
            <input id="border" type="datetime-local" className="form-control" value={borderTouched ? borderText : borderEta ? toLocal(borderEta) : ''} onChange={e => { setBorderTouched(true); setBorderText(e.target.value) }} />
            <div className="form-hint" style={borderEta && borderOutsideWindow(borderEta) ? { color: 'var(--amber)' } : undefined}>Nên rơi vào 07:30–16:30 để thông quan và khám lâm sàng trong ngày. Hệ thống ước lượng theo hành trình.</div>
          </div>
        </div>
      )}

      <div className="card">
        <div className="form-group"><label htmlFor="pn">Ghi chú</label><input id="pn" className="form-control" value={note} onChange={e => setNote(e.target.value)} /></div>
        {errors.length > 0 && <div className="alert alert-danger" style={{ marginBottom: 12 }}><i className="fa-solid fa-circle-exclamation" /><ul>{errors.map(e => <li key={e}>{e}</li>)}</ul></div>}
        {warnings.length > 0 && <div className="alert alert-warning" style={{ marginBottom: 12 }}><i className="fa-solid fa-triangle-exclamation" /><ul>{warnings.map(e => <li key={e}>{e}</li>)}</ul></div>}
        <div className={s.actionBar}>
          <div className={s.hint}>{errors.length ? 'Sửa các lỗi trên để xác nhận.' : 'Xác nhận để chuyển quản lý chọn tài xế, hộ tống và duyệt báo giá (khi Kiểm dịch viên cũng đã duyệt hồ sơ ngựa).'}</div>
          <span style={{ display: 'flex', gap: 8 }}>
            <button className="btn btn-ghost" disabled={working} onClick={() => setReason(reason === null ? '' : null)}><i className="fa-solid fa-ban" /> Không duyệt, từ chối đơn</button>
            <button className="btn btn-primary" disabled={!!errors.length || working} onClick={confirm}><i className="fa-solid fa-circle-check" /> Xác nhận xe và lộ trình</button>
          </span>
        </div>
        {reason !== null && (
          <div className="form-group" style={{ marginTop: 12 }}>
            <label htmlFor="rj" className="required">Lý do từ chối (khách sẽ thấy)</label>
            <textarea id="rj" className="form-control" rows={2} value={reason} onChange={e => setReason(e.target.value)} />
            <button className="btn btn-danger" style={{ marginTop: 8 }} disabled={working || !reason.trim()} onClick={reject}>Xác nhận từ chối đơn</button>
          </div>
        )}
      </div>
      {mapOpen && origin && dest && (
        <RouteMapPicker
          origin={{ ...origin, name: b.origin.name }} dest={{ ...dest, name: b.dest.name }} gate={gateGeo ? { ...gateGeo, name: gateGeo.name } : undefined}
          departAt={Number.isNaN(etdT) ? undefined : etdT} value={rests} onApply={picked => { setRests(picked); setBorderTouched(false) }} onClose={() => setMapOpen(false)}
        />
      )}
      {picking !== null && trips[picking] && (
        <ResourcePicker
          kind="vehicle" vehicles={vehicles} people={[]} departAt={b.departAt} international={international}
          schedules={sched.vehicles} usedHere={usedVehicles} selected={trips[picking].vehicleId} horses={trips[picking].horseIds.length}
          onPick={id => setTrip(picking, { vehicleId: id })} onClose={() => setPicking(null)}
        />
      )}
    </>
  )
}

export default function FleetPlanPage() {
  const { id = '' } = useParams()
  const { session } = useAuth()
  const { data: b, reload } = useLoad(() => bookingsApi.get(id), [id])
  const { data: all } = useLoad(bookingsApi.list)
  const { data: vehicles } = useLoad(vehiclesApi.list)

  if (!b || !all || !vehicles) return <div className="page"><div className="wrap"><p className="text-muted">Đang tải…</p></div></div>
  if (b.intake?.coordinator.name !== session!.name) return <div className="page"><div className="wrap"><div className="alert alert-danger"><i className="fa-solid fa-lock" /><div>Đơn {b.id} không được giao cho bạn. <Link to="/coordinator/fleet-plan" className="text-orange font-semibold">Về danh sách</Link></div></div></div></div>

  return (
    <div className="page">
      <div className="wrap">
        <div className="breadcrumb"><Link to="/coordinator/fleet-plan">Xe và lộ trình</Link> / <span className="text-orange font-semibold">{b.id}</span></div>
        <div className="page-header" style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}><h1>Đơn {b.id}</h1><BookingStatusBadge status={b.status} audience="staff" /><ReviewChips b={b} /></div>
        <div className={s.layout}>
          <div className={s.main}>
            <div className="card"><div className="card-header"><h3><i className="fa-solid fa-route" /> Chuyến đi</h3></div><TripSummary b={b} /></div>
            <div className="card"><div className="card-header"><h3><i className="fa-solid fa-horse-head" /> Ngựa cần chở</h3></div><HorseConfigList b={b} /></div>
            {b.status === 'under_review' ? (
              <Plan b={b} vehicles={vehicles} all={all} onDone={reload} />
            ) : b.plan ? (
              <div className="alert alert-success"><i className="fa-solid fa-circle-check" /><div><b>Đã chốt</b> lúc {formatDateTime(b.plan.at)}: {(b.trips ?? []).map((t, i) => `xe ${vehicles.find(v => v.id === t.vehicleId)?.plate} (${t.horseIds.length} ngựa)${i < (b.trips?.length ?? 0) - 1 ? '; ' : ''}`)}. {b.route && `Khởi hành ${formatDateTime(b.route.legs[0].departAt)}.`}</div></div>
            ) : <div className="alert alert-info"><i className="fa-solid fa-circle-info" /><div>Đơn chưa ở bước thẩm định.</div></div>}
          </div>
          <aside className={s.side}>
            <div className="card"><div className="card-header"><h3><i className="fa-solid fa-clock-rotate-left" /> Nhật ký đơn</h3></div><History b={b} /></div>
          </aside>
        </div>
      </div>
    </div>
  )
}
