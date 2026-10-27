// Coordinator: chốt xe và lộ trình một đơn trong một trang (PRD mục 2.4, nhánh B).
// Coordinator tự chọn xe (đơn đông ngựa thì nhiều xe), chia ngựa lên xe, lập lộ trình rồi xác nhận.
import { Fragment, lazy, Suspense, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { MAX_CONTINUOUS_HOURS, VEHICLE_CLASS } from '@shared/config/booking-rules'
import { findGate, findTransitStation } from '@shared/config/network'
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
import { matchFixedRoute, MAX_FIXED_ROUTE_ACCESS_KM, MAX_FIXED_ROUTE_DETOUR_RATIO, type FixedRouteMatch } from '@shared/lib/fixedRouteMatch'
import { bookingsApi } from '@shared/services/bookings'
import { roadRoute } from '@shared/services/routing'
import { type Vehicle } from '@shared/services/fleet'
import { useAutoRefresh, useLoad } from '@shared/services/useLoad'
import { isCoordinatorTask } from '../../../shared/staffWork'
import type { Booking, RestStop, VehicleTrip } from '@shared/types/booking'
import { BookingStatusBadge } from '@shared/ui/BookingStatusBadge'
import { useToast } from '@shared/ui/toast'
import { History, ReviewChips } from '../../../shared/BookingParts'
import s from '../../../shared/booking.module.css'
import layoutStyles from '../Coordinator.module.css'
import { FormSelect } from '@shared/ui/FormSelect'
import { ResourcePicker } from '../../../shared/ResourcePicker'
const RouteMapPicker = lazy(() => import('./RouteMapPicker').then(m => ({ default: m.RouteMapPicker })))
import { InlineRouteMap } from './InlineRouteMap'
import { roadNote } from '@shared/services/routing'
import { useRoadRouteWithAlternatives } from '@shared/services/useRoadRoute'
import { routesApi, type BackendRouteResponse } from '@shared/api/routes'
import { usePageData } from '@shared/routing/pageData'
import type { fleetPlanLoader } from '../../../shared/loaders'

const pad = (n: number) => String(n).padStart(2, '0')
const toLocal = (t: number) => {
  const d = new Date(t)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}
const fromLocal = (v: string) => (v ? new Date(v).getTime() : NaN)

type Draft = Pick<VehicleTrip, 'vehicleId' | 'horseIds'>
type CorridorSuggestion = {
  corridor: BackendRouteResponse
  match: FixedRouteMatch
  totalKm: number
  detourKm: number
}

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
  const moveHorse = (horseId: string, to: number) => {
    const target = trips[to]
    if (!target || target.horseIds.includes(horseId)) return
    const vehicle = vehicles.find(v => v.id === target.vehicleId)
    if (vehicle && target.horseIds.length >= vehicle.capacity) {
      toast(`Xe ${to + 1} đã đủ ${vehicle.capacity} ngăn, không thể chuyển thêm ngựa vào.`, 'error')
      return
    }
    setTrips(ts =>
      ts.map((t, j) => ({
        ...t,
        horseIds: j === to ? [...t.horseIds, horseId] : t.horseIds.filter(x => x !== horseId),
      }))
    )
  }
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
  const gateGeo = findGate(gate)
  const originPoint = useMemo(() => origin && ({ lat: origin.lat, lng: origin.lng }), [origin?.lat, origin?.lng]) // eslint-disable-line react-hooks/exhaustive-deps
  const destPoint = useMemo(() => dest && ({ lat: dest.lat, lng: dest.lng }), [dest?.lat, dest?.lng]) // eslint-disable-line react-hooks/exhaustive-deps
  const gatePoint = useMemo(() => gateGeo && ({ lat: gateGeo.lat, lng: gateGeo.lng }), [gateGeo?.lat, gateGeo?.lng]) // eslint-disable-line react-hooks/exhaustive-deps
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
  const [selectedCorridorId, setSelectedCorridorId] = useState<number | ''>('')
  const [corridorSuggestions, setCorridorSuggestions] = useState<CorridorSuggestion[]>([])
  const [suggestingCorridors, setSuggestingCorridors] = useState(false)
  const [applyingCorridor, setApplyingCorridor] = useState(false)
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

  const stationsOnRoute = rests.map(r => {
    const st = findTransitStation(r.name)
    if (st) return st
    if (r.lat != null && r.lng != null) {
      return { name: r.name, lat: r.lat, lng: r.lng, area: 'Trạm cố định' }
    }
    return undefined
  }).filter((x): x is NonNullable<typeof x> => !!x)

  useEffect(() => {
    if (!originPoint || !destPoint || corridors.length === 0) {
      setCorridorSuggestions([])
      setSuggestingCorridors(false)
      return
    }
    let cancelled = false
    const controller = new AbortController()
    setSuggestingCorridors(true)

    void (async () => {
      try {
        const direct = await roadRoute(
          international && gatePoint ? [originPoint, gatePoint, destPoint] : [originPoint, destPoint],
          etdT,
          { signal: controller.signal }
        )
        if (cancelled) return
        if (!direct) {
          setCorridorSuggestions([])
          setSelectedCorridorId('')
          return
        }

        const candidates = await Promise.all(corridors.map(async corridor => {
          try {
            const stops = await routesApi.getRouteStops(corridor.id)
            const match = matchFixedRoute(stops, originPoint, destPoint, international ? gatePoint : undefined)
            if (!match || match.pickupAccessKm > MAX_FIXED_ROUTE_ACCESS_KM || match.deliveryAccessKm > MAX_FIXED_ROUTE_ACCESS_KM) return null
            const route = await roadRoute(match.points, etdT, { signal: controller.signal })
            if (!route || route.km > direct.km * MAX_FIXED_ROUTE_DETOUR_RATIO) return null
            return {
              corridor,
              match,
              totalKm: route.km,
              detourKm: Math.max(0, route.km - direct.km),
            } satisfies CorridorSuggestion
          } catch {
            return null
          }
        }))
        if (cancelled) return
        const ranked = candidates.filter((candidate): candidate is CorridorSuggestion => candidate !== null)
          .sort((a, b) => a.totalKm - b.totalKm || a.match.pickupAccessKm + a.match.deliveryAccessKm - b.match.pickupAccessKm - b.match.deliveryAccessKm)
        setCorridorSuggestions(ranked)
        setSelectedCorridorId(current => ranked.some(item => item.corridor.id === current) ? current : ranked[0]?.corridor.id || '')
      } catch {
        if (!cancelled) {
          setCorridorSuggestions([])
          setSelectedCorridorId('')
        }
      } finally {
        if (!cancelled) setSuggestingCorridors(false)
      }
    })()

    return () => {
      cancelled = true
      controller.abort()
    }
  }, [corridors, originPoint, destPoint, gatePoint, international, etdT])

  const selectedSuggestion = corridorSuggestions.find(item => item.corridor.id === selectedCorridorId)
  useEffect(() => {
    if (selectedSuggestion) setRests(selectedSuggestion.match.restStops)
  }, [selectedSuggestion])

  const roadPath = selectedSuggestion?.match.points
    ?? (origin && dest ? routeOutline(origin, gateGeo, dest, stationsOnRoute).path : undefined)
  const {
    routes: roadAlternativesList,
    activeRoute: road,
    selectedIndex: routeIdx,
    setSelectedIndex: setRouteIdx,
    loading: roadLoading,
  } = useRoadRouteWithAlternatives(roadPath, Number.isNaN(etdT) ? undefined : etdT)
  // OSRM fournit une durée adaptée à un véhicule léger. Pour planifier un
  // transport de chevaux, convertir le km routier avec la vitesse métier.
  const driveHours = road ? road.km / AVG_SPEED_KMH : estHours
  const plannedLegMetrics = useMemo(
    () => road?.legs?.map(leg => ({ km: leg.km, hours: leg.km / AVG_SPEED_KMH })),
    [road]
  )

  const selectedCorridor = useMemo(() => {
    return corridors.find(c => c.id === selectedCorridorId)
  }, [corridors, selectedCorridorId])

  const applyCorridorStops = async (corridorId: number) => {
    setApplyingCorridor(true)
    try {
      const suggestion = corridorSuggestions.find(item => item.corridor.id === corridorId)
      let stops = suggestion?.match.restStops
      if (!stops || stops.length === 0) {
        const rawStops = await routesApi.getRouteStops(corridorId)
        stops = rawStops.map((s, idx) => ({
          afterLeg: idx + 1,
          name: s.locationName || s.address || `Trạm ${s.stopOrder}`,
          minutes: 45,
          lat: s.latitude ?? undefined,
          lng: s.longitude ?? undefined,
        }))
      }
      if (!stops || stops.length === 0) {
        toast('Tuyến này chưa có trạm dừng được thiết lập.', 'info')
        return
      }
      setManualStops(stops)
      toast(`Đã áp dụng ${stops.length} trạm nghỉ của hành lang.`)
    } catch {
      toast('Không thể tải trạm dừng của hành lang này', 'error')
    } finally {
      setApplyingCorridor(false)
    }
  }

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
        : layoutLegs(b.origin.name, b.dest.name, etdT, rests, driveHours, gate, true, plannedLegMetrics),
    [etdT, rests, b, driveHours, gate, initial.legs, plannedLegMetrics]
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
  const { errors: routeErrors, warnings } = validateRoutePlan(route, international, gate)
  if (Number.isNaN(etdT)) routeErrors.unshift('Nhập giờ khởi hành hợp lệ.')
  if (international && !gates.some(g => g.name === gate)) routeErrors.unshift('Chọn cửa khẩu cho tuyến này.')
  const errors = [...tripErrors, ...routeErrors]

  const confirm = async () => {
    setWorking(true)
    try {
      await bookingsApi.confirmPlan(b.id, session!.name, {
        routeId: selectedCorridorId ? Number(selectedCorridorId) : undefined,
        trips,
        route,
        gate: international ? gate : undefined,
        note: note.trim(),
      })
      toast(`Đã xác nhận ${trips.length} xe và lộ trình ${b.id}`)
      onDone()
      navigate('/coordinator/fleet-plan')
      setTimeout(() => {
        if (window.location.pathname.includes('/fleet-plan/')) {
          window.location.href = '/backoffice/coordinator/fleet-plan'
        }
      }, 350)
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
      setTimeout(() => {
        if (window.location.pathname.includes('/fleet-plan/')) {
          window.location.href = '/backoffice/coordinator/fleet-plan'
        }
      }, 350)
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
                          {trips.map((trip, j) => {
                            const targetVehicle = vehicles.find(vehicle => vehicle.id === trip.vehicleId)
                            const isFull = j !== i && !!targetVehicle && trip.horseIds.length >= targetVehicle.capacity
                            return <option key={j} value={j} disabled={isFull}>
                              Chuyển sang Xe {j + 1}
                              {isFull ? ' (đã đầy)' : ''}
                            </option>
                          })}
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
            </div>

            <div className="form-group" style={{ margin: 0 }}>
              <label htmlFor="border">Giờ dự kiến tới cửa khẩu</label>
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
                style={borderEta && borderOutsideWindow(borderEta, gate) ? { color: 'var(--amber)' } : undefined}
              >
                {borderEta && borderOutsideWindow(borderEta, gate) ? (
                  <span>⚠️ {warnings.find(warning => warning.includes('khung')) ?? 'Giờ tới cửa khẩu ngoài giờ hoạt động.'} Hãy bấm giờ xuất phát tối ưu bên dưới!</span>
                ) : (
                  <span>✓ ETA nằm trong giờ hoạt động của cửa khẩu đã chọn.</span>
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
          <div style={{ marginBottom: 14, padding: '12px 14px', background: 'var(--surface-soft, #f8fafc)', border: '1px solid var(--line, #e2e8f0)', borderRadius: 8, display: 'grid', gap: 10 }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 8 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <i className="fa-solid fa-road" style={{ color: 'var(--orange, #ea580c)', fontSize: '1rem' }} />
                <span style={{ fontSize: '0.88rem', fontWeight: 600 }}>
                  Hành lang an toàn cố định (Admin phê duyệt)
                </span>
              </div>
              <span style={{ fontSize: '0.75rem', background: '#dcfce7', color: '#15803d', padding: '2px 8px', borderRadius: 6, fontWeight: 600 }}>
                <i className="fa-solid fa-shield-check" /> Tuyến chuẩn an sinh
              </span>
            </div>
            
            <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
              <div style={{ flex: '1 1 260px' }}>
                <FormSelect
                  className="form-control"
                  style={{ fontSize: '0.85rem', height: 38 }}
                  value={selectedCorridorId}
                  onChange={e => setSelectedCorridorId(e.target.value ? Number(e.target.value) : '')}
                >
                  <option value="">-- Lộ trình linh hoạt (Tự động đề xuất OSRM) --</option>
                  {suggestingCorridors && <option disabled>Đang đối chiếu tọa độ điểm đón/trả…</option>}
                  {corridorSuggestions.map(({ corridor: c, totalKm, detourKm, match }, idx) => (
                    <option key={c.id} value={c.id}>
                      {idx === 0 ? '⭐ Gợi ý tốt nhất · ' : ''}{c.routeName} · {Math.round(totalKm)} km · {match.direction === 'reverse' ? 'chiều ngược' : 'chiều xuôi'}{detourKm > 0 ? ` · vòng thêm ${Math.round(detourKm)} km` : ''}
                    </option>
                  ))}
                  {corridors.filter(c => !corridorSuggestions.some(s => s.corridor.id === c.id)).map(c => (
                    <option key={c.id} value={c.id}>
                      {c.routeName} (Tuyến chuẩn an sinh)
                    </option>
                  ))}
                </FormSelect>
              </div>

              {selectedCorridor && (
                <button
                  type="button"
                  className="btn btn-outline btn-sm font-semibold"
                  style={{ height: 38, whiteSpace: 'nowrap' }}
                  disabled={applyingCorridor}
                  onClick={() => applyCorridorStops(selectedCorridor.id)}
                  title="Tự động nạp các trạm dừng an toàn đã thiết lập sẵn của hành lang này"
                >
                  {applyingCorridor ? (
                    <><i className="fa-solid fa-spinner fa-spin" /> Đang áp dụng…</>
                  ) : (
                    <><i className="fa-solid fa-route" /> Áp dụng trạm dừng của hành lang</>
                  )}
                </button>
              )}
            </div>

            {!suggestingCorridors && corridorSuggestions.length === 0 && (
              <div style={{ fontSize: '0.8rem', color: 'var(--muted)' }}>
                Không có tuyến cố định phù hợp với tọa độ và cửa khẩu của đơn; đang dùng tuyến đường trực tiếp OSRM.
              </div>
            )}

            {selectedCorridor && (
              <div style={{ fontSize: '0.8rem', color: 'var(--muted)', background: '#fff', padding: '8px 12px', borderRadius: 6, border: '1px dashed var(--line)' }}>
                <b>{selectedCorridor.routeName}</b>: {selectedCorridor.description || 'Tuyến tham khảo được Admin cấu hình.'}
                {selectedSuggestion && <span style={{ marginLeft: 8 }}>· {selectedSuggestion.match.direction === 'reverse' ? 'Chiều ngược' : 'Chiều xuôi'} · {Math.round(selectedSuggestion.totalKm)} km dự kiến{selectedSuggestion.detourKm > 0 ? ` · vòng thêm ${Math.round(selectedSuggestion.detourKm)} km` : ''}</span>}
              </div>
            )}
          </div>
        )}

        {/* THANH CHỌN TUYẾN ĐƯỜNG THAY THẾ (OSRM ALTERNATIVE ROUTES) */}
        {roadAlternativesList.length > 1 && (
          <div style={{ marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            <span style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--muted)', display: 'inline-flex', alignItems: 'center', gap: 5 }}>
              <i className="fa-solid fa-signs-post" style={{ color: 'var(--orange)' }} /> Chọn hướng tuyến:
            </span>
            <div style={{ display: 'inline-flex', gap: 6, flexWrap: 'wrap' }}>
              {roadAlternativesList.map((alt, idx) => {
                const isActive = idx === routeIdx
                return (
                  <button
                    key={idx}
                    type="button"
                    onClick={() => setRouteIdx(idx)}
                    className={`btn btn-sm ${isActive ? 'btn-primary' : 'btn-outline'}`}
                    style={{
                      fontSize: '0.8rem',
                      padding: '4px 10px',
                      borderRadius: 20,
                      fontWeight: isActive ? 700 : 500,
                    }}
                  >
                    {isActive && <i className="fa-solid fa-check" style={{ marginRight: 4 }} />}
                    {alt.name || `Tuyến ${idx + 1}`} ({alt.km} km · {(alt.km / AVG_SPEED_KMH).toFixed(1)}h)
                  </button>
                )
              })}
            </div>
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
              activeRoad={road}
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
                    <small>{findTransitStation(r.name)?.area ?? 'Trạm ngoài danh mục'}</small>
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
            Tổng lái ≈ {driveHours.toFixed(1)}h (ước tính {AVG_SPEED_KMH} km/h · {roadNote(road, roadLoading)}) · {legs.length} chặng
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
                <th className="text-right">Cự ly</th>
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
                      <td className="text-right nowrap">
                        {l.km !== undefined ? (
                          <span style={{ fontWeight: 600, color: 'var(--navy)' }}>{l.km} km</span>
                        ) : (
                          <span style={{ color: 'var(--muted)' }}>—</span>
                        )}
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
                        <td colSpan={4} style={{ padding: '7px 14px', color: 'var(--muted)' }}>
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
        <Suspense fallback={null}>
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
        </Suspense>
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
  const { session } = useAuth()
  const { b, all, vehicles, reload } = usePageData<typeof fleetPlanLoader>()
  useAutoRefresh(reload)
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

        <div className={layoutStyles.routeLayout}>
          <div className={layoutStyles.routeMain}>
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
          <aside className={layoutStyles.routeSide}>
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
