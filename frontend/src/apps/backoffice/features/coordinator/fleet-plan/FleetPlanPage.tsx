// Coordinator: chốt xe và lộ trình một đơn trong một trang (PRD mục 2.4, nhánh B).
// Coordinator tự chọn xe (đơn đông ngựa thì nhiều xe), chia ngựa lên xe, lập lộ trình rồi xác nhận.
import { Fragment, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { MAX_CONTINUOUS_HOURS, VEHICLE_CLASS } from '@shared/config/booking-rules'
import { GATES, TRANSIT_STATIONS } from '@shared/config/network'
import { AVG_SPEED_KMH } from '@shared/config/public-pricing'
import {
  borderOutsideWindow,
  buildRoutePlan,
  clashOf,
  estimateBorderEta,
  findLocation,
  gatesFor,
  layoutLegs,
  routeKm,
  suggestGate,
  suggestGateWithSavings,
  suggestOptimalDepartureForBorder,
  routeOutline,
  schedulesOf,
  validateRoutePlan,
  vehicleClassOf,
  autoPackFleet,
} from '@shared/lib/booking'
import { atHour } from '@shared/lib/dates'
import { formatClock, formatDate, formatDateTime } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import { vehiclesApi, type Vehicle } from '@shared/services/fleet'
import { useAutoRefresh, useLoad } from '@shared/services/useLoad'
import { isCoordinatorTask } from '../../../shared/staffWork'
import type { Booking, RestStop, VehicleTrip } from '@shared/types/booking'
import { BookingStatusBadge } from '@shared/ui/BookingStatusBadge'
import { useToast } from '@shared/ui/toast'
import { History, ReviewChips } from '../../../shared/BookingParts'
import s from '../../../shared/booking.module.css'
import { FormSelect } from '@shared/ui/FormSelect'
import { ResourcePicker } from '../../../shared/ResourcePicker'
import { RouteMapPicker } from './RouteMapPicker'
import { InlineRouteMap } from './InlineRouteMap'
import { roadNote } from '@shared/services/routing'
import { useRoadRoute } from '@shared/services/useRoadRoute'
import { routesApi, type BackendRouteResponse } from '@shared/api/routes'

const pad = (n: number) => String(n).padStart(2, '0')
const toLocal = (t: number) => {
  const d = new Date(t)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}
const fromLocal = (v: string) => (v ? new Date(v).getTime() : NaN)

type Draft = Pick<VehicleTrip, 'vehicleId' | 'horseIds'>

function Plan({ b, vehicles, all, onDone }: { b: Booking; vehicles: Vehicle[]; all: Booking[]; onDone: () => void }) {
  const toast = useToast()
  const navigate = useNavigate()
  const { session } = useAuth()
  const international = b.type === 'international'
  const sched = useMemo(() => schedulesOf(all, b.id), [all, b.id])

  // ----- xe, ngựa -----
  const initialTrips = useMemo<Draft[]>(() => {
    if (b.trips?.length) {
      return b.trips.map(t => ({ vehicleId: t.vehicleId, horseIds: t.horseIds }))
    }
    if (vehicles.length) {
      return autoPackFleet(
        b.horses.map(h => h.horseId),
        vehicles,
        sched.vehicles,
        b.departAt
      )
    }
    return [{ vehicleId: '', horseIds: b.horses.map(h => h.horseId) }]
  }, [b.trips, b.horses, b.departAt, vehicles, sched.vehicles])

  const [trips, setTrips] = useState<Draft[]>(initialTrips)

  // Tự động phân bổ thông minh khi danh sách xe khả dụng được tải về nếu chưa gán
  useEffect(() => {
    if (!vehicles.length) return
    setTrips(prev => {
      if (prev.length === 1 && !prev[0].vehicleId) {
        return autoPackFleet(
          b.horses.map(h => h.horseId),
          vehicles,
          sched.vehicles,
          b.departAt
        )
      }
      return prev
    })
  }, [vehicles, sched.vehicles, b.departAt, b.horses])

  const runAutoPack = () => {
    const packed = autoPackFleet(
      b.horses.map(h => h.horseId),
      vehicles,
      sched.vehicles,
      b.departAt
    )
    setTrips(packed)
    toast('Đã tự động tính toán và phân bổ xe tối ưu (Best-fit).')
  }

  const [note, setNote] = useState(b.plan?.note ?? '')
  const [working, setWorking] = useState(false)
  const [reason, setReason] = useState<string | null>(null)
  const [mapOpen, setMapOpen] = useState(false)
  const [picking, setPicking] = useState<number | null>(null)

  const setTrip = (i: number, patch: Partial<Draft>) =>
    setTrips(ts => ts.map((t, j) => (j === i ? { ...t, ...patch } : t)))
  const moveHorse = (horseId: string, to: number) =>
    setTrips(ts =>
      ts.map((t, j) => ({
        ...t,
        horseIds: j === to ? [...t.horseIds.filter(x => x !== horseId), horseId] : t.horseIds.filter(x => x !== horseId),
      }))
    )
  const usedVehicles = new Set(trips.map(t => t.vehicleId))

  const tripErrors = trips.flatMap((t, i) => {
    const out: string[] = []
    const v = vehicles.find(x => x.id === t.vehicleId)
    if (!t.horseIds.length) out.push(`Xe ${i + 1} chưa có ngựa nào.`)
    if (!v) out.push(`Xe ${i + 1} chưa chọn xe.`)
    else {
      if (t.horseIds.length > v.capacity)
        out.push(`Xe ${i + 1} chỉ có ${v.capacity} ngăn, đang xếp ${t.horseIds.length} ngựa.`)
      const clash = clashOf(sched.vehicles.get(t.vehicleId), b.departAt)
      if (clash) out.push(`Xe ${v.plate} trùng lịch với ${clash.order} (khởi hành ${formatDate(clash.departAt)}).`)
    }
    return out
  })

  const addTrip = () => setTrips(ts => [...ts, { vehicleId: '', horseIds: [] }])
  const removeTrip = (i: number) =>
    setTrips(ts => {
      if (ts.length < 2) return ts
      const rest = ts.filter((_, j) => j !== i)
      rest[0] = { ...rest[0], horseIds: [...rest[0].horseIds, ...ts[i].horseIds] }
      return rest
    })
  const splitEvenly = () =>
    setTrips(ts => {
      const ids = ts.flatMap(t => t.horseIds)
      return ts.map((t, j) => ({ ...t, horseIds: ids.filter((_, k) => k % ts.length === j) }))
    })

  // ----- lộ trình -----
  const gates = international ? gatesFor(b.origin, b.dest) : []
  const suggestedInfo = international ? suggestGateWithSavings(b.origin, b.dest) : undefined
  const suggested = suggestedInfo?.gate ?? (international ? suggestGate(b.origin, b.dest) : undefined)
  const [gate, setGate] = useState(b.gate ?? suggested ?? (gates[0]?.name ?? ''))
  const origin = findLocation(b.origin.id),
    dest = findLocation(b.dest.id)
  const gateGeo = gate ? GATES.find(g => g.name === gate) : undefined
  const estHours = routeKm(b.origin, b.dest, gate || undefined) / AVG_SPEED_KMH

  const defaultEtdTime = useMemo(() => {
    if (international) {
      return suggestOptimalDepartureForBorder(b.departAt, estHours * 0.55, 45)
    }
    return atHour(new Date(b.departAt), 5)
  }, [international, b.departAt, estHours])

  const initial = useMemo(
    () => b.route ?? buildRoutePlan({ ...b, gate: gate || undefined }, defaultEtdTime),
    [b, gate, defaultEtdTime]
  )
  const [etd] = useState(toLocal(initial.legs[0]?.departAt ?? defaultEtdTime))
  const [rests, setRests] = useState<RestStop[]>(initial.rests)
  const [corridors, setCorridors] = useState<BackendRouteResponse[]>([])
  const [borderText, setBorderText] = useState(initial.borderEta ? toLocal(initial.borderEta) : '')
  const [borderTouched, setBorderTouched] = useState(!!b.route?.borderEta)
  const etdT = fromLocal(etd)

  useLoad(async () => {
    try {
      const list = await routesApi.listRoutes()
      setCorridors(list.filter(r => r.status === 'ACTIVE'))
    } catch {
      // Bỏ qua lỗi nạp corridors
    }
  }, [])

  const stationsOnRoute = rests.flatMap(r => TRANSIT_STATIONS.filter(x => x.name === r.name))

  const roadPath = origin && dest ? routeOutline(origin, gateGeo, dest, stationsOnRoute).path : undefined
  const { route: road, loading: roadLoading } = useRoadRoute(roadPath, Number.isNaN(etdT) ? undefined : etdT)
  const driveHours = road?.hours ?? estHours

  const resuggest = (g?: string) => {
    const chosenGate = g ?? gate
    const plan = buildRoutePlan(
      { ...b, gate: chosenGate || undefined },
      Number.isNaN(etdT) ? defaultEtdTime : etdT
    )
    setRests(plan.rests)
    setBorderTouched(false)
    toast('Đã tự động tính toán các trạm dừng nghỉ theo chuẩn an sinh (≤ 4 giờ / trạm).')
  }

  const pickGate = (g: string) => {
    setGate(g)
    resuggest(g)
  }

  const legs = useMemo(
    () =>
      Number.isNaN(etdT)
        ? initial.legs
        : layoutLegs(b.origin.name, b.dest.name, etdT, rests, driveHours, gate, true),
    [etdT, rests, b, driveHours, gate, initial.legs]
  )
  const borderEta = international
    ? borderTouched
      ? fromLocal(borderText)
      : estimateBorderEta(legs, rests, gate)
    : undefined
  const route = {
    legs,
    rests: rests.map((r, i) => ({ ...r, afterLeg: i + 1 })),
    borderEta: borderEta && !Number.isNaN(borderEta) ? borderEta : undefined,
  }
  const { errors: routeErrors, warnings } = validateRoutePlan(route, international)
  if (Number.isNaN(etdT)) routeErrors.unshift('Nhập giờ khởi hành hợp lệ.')
  if (international && !gates.some(g => g.name === gate)) routeErrors.unshift('Chọn cửa khẩu cho tuyến này.')
  const errors = [...tripErrors, ...routeErrors]

  const confirm = async () => {
    setWorking(true)
    try {
      await bookingsApi.confirmPlan(b.id, session!.name, {
        trips,
        route,
        gate: international ? gate : undefined,
        note: note.trim(),
      })
      toast(`Đã xác nhận ${trips.length} xe và lộ trình ${b.id}`)
      onDone()
      navigate('/coordinator/fleet-plan')
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Không xác nhận được', 'error')
      setWorking(false)
    }
  }

  const reject = async () => {
    setWorking(true)
    try {
      await bookingsApi.rejectOrder(b.id, session!.name, 'coordinator', reason ?? '')
      toast(`Đã từ chối ${b.id}, khách nhận được lý do`)
      onDone()
      navigate('/coordinator/fleet-plan')
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Không từ chối được', 'error')
      setWorking(false)
    }
  }

  return (
    <>
      {b.sentBack?.to === 'coordinator' && (
        <div className="alert alert-warning">
          <i className="fa-solid fa-rotate-left" />
          <div>
            <b>Quản lý trả lại đơn này:</b> {b.sentBack.reason}{' '}
            <span className="sub-text">({b.sentBack.by}, {formatDateTime(b.sentBack.at)})</span>
          </div>
        </div>
      )}
      {b.plan && (
        <div className="alert alert-info">
          <i className="fa-solid fa-circle-check" />
          <div>
            <b>Bạn đã xác nhận xe và lộ trình</b> lúc {formatDateTime(b.plan.at)}. Đang chờ Kiểm dịch viên duyệt hồ sơ
            ngựa để chuyển Quản lý duyệt báo giá.
          </div>
        </div>
      )}

      {/* KHỐI 1: XE CỦA ĐƠN & PHÂN NGỰA */}
      <div className="card">
        <div className="card-header">
          <h3>
            <i className="fa-solid fa-truck" /> {trips.length > 1 ? `${trips.length} xe chuyên dụng` : 'Xe chuyên dụng của đơn'}
          </h3>
          <span style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <button
              type="button"
              className="btn btn-outline btn-sm text-orange font-semibold"
              onClick={runAutoPack}
              title="Tự động tính số lượng xe cần thiết và chọn xe tối ưu theo sức chứa (Best-fit)"
            >
              <i className="fa-solid fa-wand-magic-sparkles" /> Tự động xếp xe tối ưu
            </button>
            {trips.length > 1 && (
              <button className="btn btn-outline btn-sm" onClick={splitEvenly}>
                <i className="fa-solid fa-scale-balanced" /> Chia đều ngựa
              </button>
            )}
            <button className="btn btn-outline btn-sm" onClick={addTrip}>
              <i className="fa-solid fa-plus" /> Thêm xe
            </button>
          </span>
        </div>
        <div className="form-hint" style={{ marginBottom: 12 }}>
          Ưu tiên xe đủ số ngăn cho ngựa. Phương tiện trùng lịch trong vòng 3 ngày sẽ tự động bị khóa và cảnh báo.
        </div>

        {trips.map((t, i) => {
          const v = vehicles.find(x => x.id === t.vehicleId)
          const isOverloaded = v && t.horseIds.length > v.capacity
          const booked = v ? sched.vehicles.get(t.vehicleId) : undefined
          const hasClash = v && clashOf(booked, b.departAt)
          return (
            <div
              key={i}
              className={s.pick}
              style={{
                display: 'block',
                marginBottom: 12,
                border: isOverloaded || hasClash ? '1px solid var(--red)' : undefined,
              }}
            >
              <div className={s.pickName} style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                <span>
                  <b>Xe {i + 1}</b>
                  {v ? ` · Biển số ${v.plate} (${VEHICLE_CLASS[vehicleClassOf(v.capacity)].label}, ${v.capacity} ngăn)` : ''}
                </span>
                {trips.length > 1 && (
                  <button className="btn btn-ghost btn-sm text-red" onClick={() => removeTrip(i)}>
                    <i className="fa-solid fa-trash" /> Bỏ xe này
                  </button>
                )}
              </div>

              {/* THANH TẢI TRỌNG SỨC CHỨA XE TRỰC QUAN */}
              {v && (
                <div style={{ margin: '8px 0 10px', maxWidth: 420 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', marginBottom: 4 }}>
                    <span style={{ color: 'var(--muted)' }}>Tải trọng khoang:</span>
                    <span style={{ fontWeight: 700, color: isOverloaded ? 'var(--red)' : t.horseIds.length === v.capacity ? 'var(--green)' : 'var(--text)' }}>
                      {t.horseIds.length}/{v.capacity} ngăn ({v.capacity ? Math.round((t.horseIds.length / v.capacity) * 100) : 0}%)
                    </span>
                  </div>
                  <div style={{ height: 7, background: '#e2e8f0', borderRadius: 999, overflow: 'hidden' }}>
                    <div
                      style={{
                        width: `${Math.min(100, v.capacity ? Math.round((t.horseIds.length / v.capacity) * 100) : 0)}%`,
                        height: '100%',
                        background: isOverloaded ? 'var(--red, #ef4444)' : t.horseIds.length === v.capacity ? 'var(--green, #16a34a)' : 'var(--orange, #f97316)',
                        borderRadius: 999,
                        transition: 'width 0.3s ease',
                      }}
                    />
                  </div>
                </div>
              )}

              {isOverloaded && (
                <div style={{ color: 'var(--red)', fontSize: '0.82rem', marginTop: 4, fontWeight: 600 }}>
                  <i className="fa-solid fa-circle-exclamation" /> Xe chỉ có {v.capacity} ngăn nhưng đang xếp {t.horseIds.length} ngựa. Vui lòng thêm xe hoặc đổi xe lớn hơn!
                </div>
              )}
              {hasClash && (
                <div style={{ color: 'var(--red)', fontSize: '0.82rem', marginTop: 4, fontWeight: 600 }}>
                  <i className="fa-solid fa-triangle-exclamation" /> Xe {v.plate} bị trùng lịch với đơn {hasClash.order}!
                </div>
              )}
              <div className="form-group" style={{ marginTop: 10, maxWidth: 420 }}>
                <label htmlFor={`vehicle${i}`}>Chọn phương tiện</label>
                <button
                  id={`vehicle${i}`}
                  type="button"
                  className={`form-control ${s.vehBtn}`}
                  onClick={() => setPicking(i)}
                >
                  <i className="fa-solid fa-truck" aria-hidden="true" />
                  <span key={t.vehicleId} className={s.vehPop}>
                    {v ? <b>{v.plate} ({v.capacity} ngăn)</b> : 'Bấm để chọn xe'}
                  </span>
                  <em>{v ? 'Đổi xe' : 'Chọn xe'}</em>
                </button>
                {t.vehicleId && (
                  <span className={`${s.busyNote} ${booked?.length ? s.busyOn : s.busyFree}`}>
                    <i className={`fa-solid ${booked?.length ? 'fa-calendar-check' : 'fa-circle-check'}`} />
                    {booked?.length
                      ? `Đang nhận ${booked.length} đơn khác: ${booked.slice(0, 2).map(x => `${x.order.slice(-4)} (${formatDate(x.departAt)})`).join(', ')}`
                      : 'Xe rảnh, sẵn sàng nhận chuyến'}
                  </span>
                )}
              </div>


              <ul style={{ display: 'grid', gap: 6, marginTop: 8 }}>
                {t.horseIds.map(hid => {
                  const h = b.horses.find(x => x.horseId === hid)
                  return (
                    <li
                      key={hid}
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        gap: 10,
                        alignItems: 'center',
                        background: '#f8fafc',
                        padding: '6px 12px',
                        borderRadius: 8,
                      }}
                    >
                      <span>
                        <b>{h?.name}</b> <span className={s.sub}>· Chip: {h?.microchip}</span>
                      </span>
                      {trips.length > 1 && (
                        <FormSelect
                          className="form-control"
                          style={{ width: 140, padding: '3px 8px', fontSize: '0.8rem' }}
                          aria-label={`Chuyển ${h?.name} sang xe`}
                          value={i}
                          onChange={e => moveHorse(hid, Number(e.target.value))}
                        >
                          {trips.map((_, j) => (
                            <option key={j} value={j}>
                              Chuyển sang Xe {j + 1}
                            </option>
                          ))}
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

      {/* KHỐI 2: CỬA KHẨU THÔNG QUAN (NẾU QUỐC TẾ) */}
      {international && (
        <div className="card">
          <div
            className="card-header"
            style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}
          >
            <h3>
              <i className="fa-solid fa-flag" /> Cửa khẩu thông quan quốc tế
            </h3>
            {suggestedInfo?.savingsKm && (
              <span className="badge badge-success">
                <i className="fa-solid fa-wand-magic-sparkles" /> Tiết kiệm {suggestedInfo.savingsKm} km
              </span>
            )}
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 16 }}>
            <div className="form-group" style={{ margin: 0 }}>
              <label htmlFor="gate" className="required">
                Cửa khẩu xuất/nhập cảnh
              </label>
              <FormSelect id="gate" className="form-control" value={gate} onChange={e => pickGate(e.target.value)}>
                {gates.map(g => (
                  <option key={g.name} value={g.name}>
                    {g.name}
                    {g.name === suggested
                      ? ` (Đề xuất tối ưu: ${routeKm(b.origin, b.dest, g.name)} km)`
                      : ` · ${routeKm(b.origin, b.dest, g.name)} km`}
                  </option>
                ))}
              </FormSelect>
              <div className="form-hint">Hệ thống tự động tính khoảng cách và đề xuất cửa khẩu ngắn nhất.</div>
            </div>

            <div className="form-group" style={{ margin: 0 }}>
              <label htmlFor="border">
                Giờ dự kiến tới cửa khẩu ({gate})
              </label>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                <input
                  id="border"
                  type="datetime-local"
                  className="form-control"
                  value={borderTouched ? borderText : borderEta ? toLocal(borderEta) : ''}
                  onChange={e => {
                    setBorderTouched(true)
                    setBorderText(e.target.value)
                  }}
                />
                {borderTouched && (
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm"
                    onClick={() => setBorderTouched(false)}
                    title="Tính lại tự động theo lộ trình"
                  >
                    <i className="fa-solid fa-rotate" /> Tự động
                  </button>
                )}
              </div>
              <div
                className="form-hint"
                style={borderEta && borderOutsideWindow(borderEta) ? { color: 'var(--amber)' } : undefined}
              >
                {borderEta && borderOutsideWindow(borderEta) ? (
                  <span>⚠️ Giờ tới cửa khẩu ngoài khung 07:30–16:30. Hãy bấm giờ xuất phát tối ưu bên dưới!</span>
                ) : (
                  <span>✓ Khung giờ thông quan lý tưởng (07:30–16:30).</span>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* KHỐI 3: BẢN ĐỒ LỘ TRÌNH TRỰC QUAN & TRẠM DỪNG NGHỈ */}
      <div className="card">
        <div className="card-header">
          <h3>
            <i className="fa-solid fa-map-location-dot" /> Bản đồ lộ trình & Trạm dừng nghỉ
          </h3>
          <span style={{ display: 'flex', gap: 8 }}>
            <button className="btn btn-primary btn-sm" onClick={() => setMapOpen(true)}>
              <i className="fa-solid fa-up-right-and-down-left-from-center" /> Chọn trạm trên bản đồ
            </button>
            <button className="btn btn-outline btn-sm" onClick={() => resuggest(gate)}>
              <i className="fa-solid fa-wand-magic-sparkles" /> Gợi ý chuẩn ≤ 4h
            </button>
          </span>
        </div>
        <div className="form-hint" style={{ marginBottom: 12 }}>
          Quy chuẩn an sinh: Xe chạy tối đa <b>4.0 giờ</b> phải dừng nghỉ tối thiểu 30–45 phút để kiểm tra sức khỏe và cho ngựa uống nước.
        </div>

        {corridors.length > 0 && (
          <div style={{ marginBottom: 14, padding: '10px 14px', background: 'var(--surface-soft, #f8fafc)', border: '1px solid var(--line, #e2e8f0)', borderRadius: 8, display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 8 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <i className="fa-solid fa-road" style={{ color: 'var(--orange, #ea580c)' }} />
              <span style={{ fontSize: '0.88rem' }}>
                Hành lang an toàn: <b>{corridors[0].routeName}</b>
                {corridors[0].distance && <span style={{ color: 'var(--muted)', marginLeft: 6 }}>({corridors[0].distance} km)</span>}
              </span>
            </div>
            <span style={{ fontSize: '0.78rem', background: '#dcfce7', color: '#15803d', padding: '2px 8px', borderRadius: 6, fontWeight: 600 }}>
              <i className="fa-solid fa-shield-check" /> Trục cố định an toàn
            </span>
          </div>
        )}

        {/* BẢN ĐỒ INLINE TRỰC TIẾP TRÊN TRANG */}

        {origin && dest && (
          <div style={{ marginBottom: 16 }}>
            <InlineRouteMap
              origin={{ ...origin, name: b.origin.name }}
              dest={{ ...dest, name: b.dest.name }}
              gate={gateGeo ? { ...gateGeo, name: gateGeo.name } : undefined}
              departAt={Number.isNaN(etdT) ? undefined : etdT}
              rests={rests}
              onOpenPicker={() => setMapOpen(true)}
            />
          </div>
        )}

        {/* DANH SÁCH CÁC TRẠM NGHỈ */}
        {rests.length ? (
          <ol className={s.stopList}>
            {rests.map((r, i) => (
              <li key={r.name + i}>
                <button
                  type="button"
                  className={s.stopItem}
                  onClick={() => setMapOpen(true)}
                  title="Mở bản đồ để đổi trạm hoặc thời gian nghỉ"
                >
                  <span className={s.stopNum}>{i + 1}</span>
                  <span className={s.stopName}>
                    <b>{r.name}</b>
                    <small>{TRANSIT_STATIONS.find(x => x.name === r.name)?.area ?? 'Trạm ngoài danh mục'}</small>
                  </span>
                  <span className={s.stopMin}>
                    <i className="fa-regular fa-clock" /> Nghỉ {r.minutes} phút
                  </span>
                </button>
                <button
                  className={s.iconBtn}
                  aria-label={`Bỏ trạm ${r.name}`}
                  onClick={() =>
                    setRests(x => x.filter((_, j) => j !== i).map((y, j) => ({ ...y, afterLeg: j + 1 })))
                  }
                >
                  <i className="fa-solid fa-xmark" />
                </button>
              </li>
            ))}
          </ol>
        ) : (
          <div className={s.stopEmpty}>
            <i className="fa-solid fa-mug-hot" /> Chưa có trạm dừng nghỉ nào.{' '}
            <button type="button" className="btn btn-outline btn-sm" onClick={() => resuggest(gate)} style={{ marginLeft: 8 }}>
              Tự động phân bổ trạm chuẩn ≤ 4h
            </button>
          </div>
        )}
      </div>

      {/* KHỐI 4: LỊCH TRÌNH CHI TIẾT & CHỌN GIỜ KHỞI HÀNH THÔNG MINH */}
      <div className="card">
        <div className="card-header">
          <h3>
            <i className="fa-solid fa-clock" /> Lịch trình từng chặng
          </h3>
          <span className="sub-text">
            Tổng lái ≈ {driveHours.toFixed(1)}h ({roadNote(road, roadLoading)}) · {legs.length} chặng
          </span>
        </div>

        {/* THÔNG TIN THỜI GIAN HÀNH TRÌNH TỰ ĐỊNH */}
        <div
          style={{
            background: '#f8fafc',
            border: '1px solid var(--line)',
            borderRadius: 10,
            padding: '14px 18px',
            marginBottom: 16,
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
            gap: 16,
          }}
        >
          <div>
            <div style={{ fontSize: '0.78rem', color: 'var(--muted)', textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 4 }}>
              <i className="fa-regular fa-calendar-check" style={{ color: 'var(--orange)', marginRight: 6 }} />
              Ngày xuất phát
            </div>
            <div style={{ fontSize: '1.05rem', fontWeight: 700, color: 'var(--navy)' }}>
              {legs[0] ? formatDateTime(legs[0].departAt) : formatDate(b.departAt)}
            </div>
            <div style={{ fontSize: '0.78rem', color: '#16a34a', marginTop: 2 }}>
              <i className="fa-solid fa-circle-check" style={{ marginRight: 4 }} />
              Hệ thống tự định giờ xuất phát tối ưu
            </div>
          </div>

          <div>
            <div style={{ fontSize: '0.78rem', color: 'var(--muted)', textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 4 }}>
              <i className="fa-solid fa-flag-checkered" style={{ color: '#0284c7', marginRight: 6 }} />
              Ngày đến dự kiến
            </div>
            <div style={{ fontSize: '1.05rem', fontWeight: 700, color: 'var(--navy)' }}>
              {legs.length ? formatDateTime(legs[legs.length - 1].arriveAt) : 'Đang tính toán…'}
            </div>
            {legs.length > 0 && (
              <div style={{ fontSize: '0.78rem', color: 'var(--muted)', marginTop: 2 }}>
                Tổng hành trình: {((legs[legs.length - 1].arriveAt - legs[0].departAt) / 3_600_000).toFixed(1)} giờ
              </div>
            )}
          </div>

          <div>
            <div style={{ fontSize: '0.78rem', color: 'var(--muted)', textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 4 }}>
              <i className="fa-solid fa-heart-pulse" style={{ color: '#ea580c', marginRight: 6 }} />
              Quy chuẩn an sinh
            </div>
            <div style={{ fontSize: '1.05rem', fontWeight: 700, color: 'var(--navy)' }}>
              {rests.length} trạm dừng nghỉ
            </div>
            <div style={{ fontSize: '0.78rem', color: 'var(--muted)', marginTop: 2 }}>
              Nghỉ 30–45 phút / trạm (tối đa 4.0h lái liên tục)
            </div>
          </div>
        </div>

        {/* BẢNG CÁC CHẶNG LÁI & ĐIỂM DỪNG NGHỈ */}
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Chặng</th>
                <th>Hành trình</th>
                <th>Khởi hành</th>
                <th>Đến</th>
                <th className="text-right">Lái liên tục</th>
              </tr>
            </thead>
            <tbody>
              {legs.map((l, idx) => {
                const h = (l.arriveAt - l.departAt) / 3_600_000
                const isOverWelfare = h > MAX_CONTINUOUS_HOURS
                const restStop = rests[idx]
                const departDay = new Date(l.departAt).toDateString()
                const arriveDay = new Date(l.arriveAt).toDateString()
                const isNextDay = departDay !== arriveDay

                return (
                  <Fragment key={l.no}>
                    <tr style={isOverWelfare ? { background: 'rgba(239, 68, 68, 0.08)' } : undefined}>
                      <td className="font-semibold">{l.no}</td>
                      <td>
                        {l.from} → {l.to}
                      </td>
                      <td className="nowrap">{formatDateTime(l.departAt)}</td>
                      <td className="nowrap">
                        {isNextDay ? formatDateTime(l.arriveAt) : formatClock(l.arriveAt)}
                        {isNextDay && (
                          <span className="badge badge-info" style={{ marginLeft: 6, fontSize: '0.7rem' }}>
                            +1 ngày
                          </span>
                        )}
                      </td>
                      <td className="text-right nowrap">
                        {isOverWelfare ? (
                          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                            <span className="badge badge-danger">
                              ⚠️ {h.toFixed(1)}h (Vượt 4h)
                            </span>
                            <button
                              type="button"
                              className="btn btn-ghost btn-sm text-red"
                              onClick={() => setMapOpen(true)}
                              title="Bấm để chèn trạm nghỉ chia nhỏ chặng này"
                              style={{ padding: '2px 6px', fontSize: '0.75rem' }}
                            >
                              + Thêm trạm
                            </button>
                          </div>
                        ) : (
                          <span style={{ fontWeight: 600, color: 'var(--text)' }}>
                            {h.toFixed(1)} giờ
                          </span>
                        )}
                      </td>
                    </tr>
                    {restStop && idx < legs.length - 1 && (
                      <tr style={{ background: '#f8fafc', fontSize: '0.82rem' }}>
                        <td colSpan={2} style={{ padding: '7px 14px', color: '#047857' }}>
                          <i className="fa-solid fa-mug-hot" style={{ marginRight: 6 }} />
                          <b>Dừng nghỉ: {restStop.name}</b>
                        </td>
                        <td colSpan={3} style={{ padding: '7px 14px', color: 'var(--muted)' }}>
                          Nghỉ <b>{restStop.minutes} phút</b> ({formatClock(l.arriveAt)} → {formatClock(legs[idx + 1].departAt)}) · Kiểm tra an sinh & cho uống nước
                        </td>
                      </tr>
                    )}
                  </Fragment>
                )
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* KHỐI 5: GHI CHÚ & XÁC NHẬN */}
      <div className="card">
        <div className="form-group">
          <label htmlFor="pn">Ghi chú phương án</label>
          <input
            id="pn"
            className="form-control"
            placeholder="Ghi chú thêm cho Quản lý hoặc kíp xe nếu cần..."
            value={note}
            onChange={e => setNote(e.target.value)}
          />
        </div>

        {errors.length > 0 && (
          <div className="alert alert-danger" style={{ marginBottom: 12 }}>
            <i className="fa-solid fa-circle-exclamation" />
            <div>
              <b>Cần xử lý các điểm sau trước khi xác nhận:</b>
              <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>
                {errors.map(e => (
                  <li key={e}>{e}</li>
                ))}
              </ul>
            </div>
          </div>
        )}
        {warnings.length > 0 && (
          <div className="alert alert-warning" style={{ marginBottom: 12 }}>
            <i className="fa-solid fa-triangle-exclamation" />
            <div>
              <b>Lưu ý điều phối:</b>
              <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>
                {warnings.map(e => (
                  <li key={e}>{e}</li>
                ))}
              </ul>
            </div>
          </div>
        )}

        <div className={s.actionBar}>
          <div className={s.hint}>
            {errors.length
              ? 'Khắc phục các điểm lỗi màu đỏ ở trên để mở khóa xác nhận.'
              : 'Xác nhận để chuyển Quản lý duyệt giá và chọn kíp xe (khi Kiểm dịch viên cũng đã duyệt y tế).'}
          </div>
          <span style={{ display: 'flex', gap: 8 }}>
            <button
              className="btn btn-ghost"
              disabled={working}
              onClick={() => setReason(reason === null ? '' : null)}
            >
              <i className="fa-solid fa-ban" /> Từ chối đơn
            </button>
            <button
              className="btn btn-primary"
              disabled={!!errors.length || working}
              onClick={confirm}
            >
              <i className="fa-solid fa-circle-check" /> Xác nhận xe và lộ trình
            </button>
          </span>
        </div>

        {reason !== null && (
          <div className="form-group" style={{ marginTop: 12 }}>
            <label htmlFor="rj" className="required">
              Lý do từ chối (khách hàng sẽ nhận được thông báo này)
            </label>
            <textarea
              id="rj"
              className="form-control"
              rows={2}
              placeholder="Nêu rõ lý do không thể tiếp nhận chuyến..."
              value={reason}
              onChange={e => setReason(e.target.value)}
            />
            <button
              className="btn btn-danger"
              style={{ marginTop: 8 }}
              disabled={working || !reason.trim()}
              onClick={reject}
            >
              Xác nhận từ chối đơn
            </button>
          </div>
        )}
      </div>

      {mapOpen && origin && dest && (
        <RouteMapPicker
          origin={{ ...origin, name: b.origin.name }}
          dest={{ ...dest, name: b.dest.name }}
          gate={gateGeo ? { ...gateGeo, name: gateGeo.name } : undefined}
          departAt={Number.isNaN(etdT) ? undefined : etdT}
          value={rests}
          onApply={picked => {
            setRests(picked)
            setBorderTouched(false)
          }}
          onClose={() => setMapOpen(false)}
        />
      )}
      {picking !== null && trips[picking] && (
        <ResourcePicker
          kind="vehicle"
          vehicles={vehicles}
          people={[]}
          departAt={b.departAt}
          schedules={sched.vehicles}
          usedHere={usedVehicles}
          selected={trips[picking].vehicleId}
          horses={trips[picking].horseIds.length}
          onPick={id => setTrip(picking, { vehicleId: id })}
          onClose={() => setPicking(null)}
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
  const { data: vehicles, reload: reloadVehicles } = useLoad(vehiclesApi.list)
  useAutoRefresh(reloadVehicles)
  const [showOrderDetails, setShowOrderDetails] = useState(false)

  if (!b || !all || !vehicles)
    return (
      <div className="page">
        <div className="wrap">
          <p className="text-muted">Đang tải dữ liệu…</p>
        </div>
      </div>
    )

  const isAssigned = isCoordinatorTask(b, session?.name, session?.username, session?.role)

  if (!isAssigned)
    return (
      <div className="page">
        <div className="wrap">
          <div className="alert alert-danger">
            <i className="fa-solid fa-lock" />
            <div>
              Đơn {b.id} hiện được giao cho {b.intake?.coordinator.name || 'điều phối viên khác'}.{' '}
              <Link to="/coordinator/fleet-plan" className="text-orange font-semibold">
                Về danh sách
              </Link>
            </div>
          </div>
        </div>
      </div>
    )

  const international = b.type === 'international'

  return (
    <div className="page">
      <div className="wrap">
        <div className="breadcrumb">
          <Link to="/coordinator/fleet-plan">Điều phối đội xe</Link> /{' '}
          <span className="text-orange font-semibold">{b.id}</span>
        </div>

        <div className="page-header" style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
          <h1>Đơn {b.id}</h1>
          <BookingStatusBadge status={b.status} labels={b} audience="staff" />
          <ReviewChips b={b} />
        </div>

        {!b.intake && (
          <div className="alert alert-info" style={{ marginBottom: 16 }}>
            <i className="fa-solid fa-circle-info" />
            <div>Đơn chưa được Quản lý tiếp nhận chính thức. Đang hiển thị ở chế độ xem trước.</div>
          </div>
        )}

        {/* QUICK HEADER BAR - TÓM TẮT ĐƠN HÀNG GỌN GÀNG */}
        <div className="card" style={{ padding: '12px 16px', marginBottom: 16 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
            <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
              <span style={{ fontWeight: 700, fontSize: '1.05rem', color: 'var(--navy)' }}>
                {b.origin.name.split(' — ')[0]} → {b.dest.name.split(' — ')[0]}
              </span>
              <span className={`badge ${international ? 'badge-primary' : 'badge-neutral'}`}>
                {international ? 'Tuyến quốc tế' : 'Tuyến nội địa'}
              </span>
              <span style={{ fontSize: '0.85rem', color: 'var(--muted)' }}>
                <i className="fa-regular fa-calendar" style={{ marginRight: 4 }} />
                Ngày khởi hành: <b>{formatDate(b.departAt)}</b>
              </span>
              <span style={{ fontSize: '0.85rem', color: 'var(--muted)' }}>
                <i className="fa-solid fa-horse" style={{ marginRight: 4 }} />
                <b>{b.horses.length}</b> con ngựa
              </span>
            </div>
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              onClick={() => setShowOrderDetails(x => !x)}
            >
              <i className={`fa-solid ${showOrderDetails ? 'fa-chevron-up' : 'fa-chevron-down'}`} />{' '}
              {showOrderDetails ? 'Ẩn thông tin khách & ngựa' : 'Xem thông tin khách & ngựa'}
            </button>
          </div>

          {showOrderDetails && (
            <div
              style={{
                marginTop: 14,
                paddingTop: 14,
                borderTop: '1px solid var(--line)',
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
                gap: 16,
              }}
            >
              <div>
                <h4 style={{ margin: '0 0 6px', fontSize: '0.88rem', color: 'var(--navy)' }}>
                  <i className="fa-solid fa-user" /> Người gửi & Người nhận
                </h4>
                <div style={{ fontSize: '0.82rem', lineHeight: 1.6 }}>
                  <div>Khách đặt đơn: <b>{b.customer}</b></div>
                  <div>Người gửi: <b>{b.consignor.name}</b> ({b.consignor.phone})</div>
                  <div>Người nhận: <b>{b.consignee.name}</b> ({b.consignee.phone})</div>
                </div>
              </div>
              <div>
                <h4 style={{ margin: '0 0 6px', fontSize: '0.88rem', color: 'var(--navy)' }}>
                  <i className="fa-solid fa-horse-head" /> Danh sách ngựa ({b.horses.length} con)
                </h4>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                  {b.horses.map(h => (
                    <span key={h.horseId} className="badge badge-neutral" style={{ fontSize: '0.78rem' }}>
                      {h.name} (Chip: {h.microchip}) · {h.breed}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>

        <div className={s.layout}>
          <div className={s.main}>
            {b.status === 'under_review' ? (
              <Plan b={b} vehicles={vehicles} all={all} onDone={reload} />
            ) : b.plan ? (
              <div className="alert alert-success">
                <i className="fa-solid fa-circle-check" />
                <div>
                  <b>Đã chốt phương án</b> lúc {formatDateTime(b.plan.at)}:{' '}
                  {(b.trips ?? []).map(
                    (t, i) =>
                      `xe ${vehicles.find(v => v.id === t.vehicleId)?.plate} (${t.horseIds.length} ngựa)${
                        i < (b.trips?.length ?? 0) - 1 ? '; ' : ''
                      }`
                  )}
                  . {b.route && `Khởi hành ${formatDateTime(b.route.legs[0].departAt)}.`}
                </div>
              </div>
            ) : (
              <div className="alert alert-info">
                <i className="fa-solid fa-circle-info" />
                <div>Đơn chưa ở bước thẩm định.</div>
              </div>
            )}
          </div>
          <aside className={s.side}>
            <div className="card">
              <div className="card-header">
                <h3>
                  <i className="fa-solid fa-clock-rotate-left" /> Nhật ký đơn
                </h3>
              </div>
              <History b={b} />
            </div>
          </aside>
        </div>
      </div>
    </div>
  )
}
