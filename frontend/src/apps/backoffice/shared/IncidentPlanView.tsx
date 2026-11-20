// Xem lại phương án sự cố trên bản đồ (chỉ đọc): Quản lý duyệt, Tài xế và Hộ tống làm theo
import { findGate, findTransitStation } from '@shared/config/network'
import { INCIDENT_ACTION } from '@shared/config/booking-rules'
import { findLocation, projectOnPath } from '@shared/lib/booking'
import { routeBeforeIncident } from '@shared/lib/incidentRoute'
import type { Booking, Incident } from '@shared/types/booking'
import { IncidentMap, NEW_ROUTE, ONWARD_LEGEND, ONWARD_ROUTE, RESCUE_ROUTE, oldRouteLines, type MapLine } from './IncidentMap'

export function IncidentPlanView({ b, i, height = 340 }: { b: Booking; i: Incident; height?: number }) {
  const plan = i.plan
  const origin = findLocation(b.origin.id), dest = findLocation(b.dest.id)
  if (!plan || !origin || !dest) return null
  const gate = findGate(b.gate)
  const station = plan.station ? [findTransitStation(plan.station)].filter(s => s !== undefined) : []
  // Đường cũ để so sánh: tắc đường thì tới điểm cuối của đường tránh, các loại khác tới hết lộ trình đã lập
  const detourEnd = plan.detour?.path.at(-1)
  const current = routeBeforeIncident(b, i)
  const old = oldRouteLines(current, i.location, detourEnd ? { lat: detourEnd[0], lng: detourEnd[1] } : undefined)
  // Tắc đường: đường tránh dừng ở điểm kế tiếp, phần lộ trình còn lại từ đó tới điểm trả
  const afterDetour: [number, number][] = detourEnd && current.length > 1
    ? [detourEnd, ...current.slice(projectOnPath(current.map(([lat, lng]) => ({ lat, lng })), { lat: detourEnd[0], lng: detourEnd[1] }).segment + 1)]
    : []
  const lines: MapLine[] = [
    ...old.lines,
    ...(plan.rescueLine ? [{ path: plan.rescueLine.path, color: RESCUE_ROUTE, flow: true, legend: 'Đường cứu hộ', label: `Cứu hộ tới chỗ xe: ${plan.rescueLine.km} km` }] : []),
    ...(plan.toStation ? [{ path: plan.toStation.path, color: NEW_ROUTE, flow: true, weight: 6, label: `Đưa ngựa tới ${plan.station}: ${plan.toStation.km} km`, legend: 'Đường mới' }] : []),
    ...(plan.detour ? [{ path: plan.detour.path, color: NEW_ROUTE, flow: true, weight: 6, label: `Lộ trình mới: ${plan.detour.km} km`, legend: 'Đường mới' }] : []),
    ...(plan.onward ? [{ path: plan.onward.path, color: ONWARD_ROUTE, flow: true, weight: 5, label: 'Đường đi tiếp từ trạm SOS tới điểm trả', legend: ONWARD_LEGEND, fit: false }] : []),
    ...(afterDetour.length > 1 ? [{ path: afterDetour, color: ONWARD_ROUTE, weight: 4, label: 'Đi tiếp theo lộ trình tới điểm trả', legend: ONWARD_LEGEND, fit: false }] : []),
  ]
  return (
    <IncidentMap height={height} location={i.location} origin={{ ...origin, name: b.origin.name }} dest={{ ...dest, name: b.dest.name }} gate={gate ? { ...gate } : undefined}
      stations={station} selectedStation={plan.station}
      rescues={plan.rescue ? [{ ...plan.rescue, area: '' }] : []} selectedRescue={plan.rescue?.name} lines={lines}>
      <div className="im-head"><span className="im-icon"><i className="fa-solid fa-route" /></span><div><b>Phương án đã lập</b><small>{INCIDENT_ACTION[plan.action]}</small></div></div>
      {plan.rescue && <div className="im-road"><span>Cứu hộ: <b>{plan.rescue.name}</b></span><small>{plan.rescue.phone}{plan.rescueLine ? ` · ${plan.rescueLine.km} km` : ''}</small></div>}
      {plan.station && <div className="im-road"><span>Trạm nghỉ: <b>{plan.station}</b>{plan.restMinutes ? ` · nghỉ ${plan.restMinutes} phút` : ''}</span>{plan.toStation && <small>{plan.toStation.km} km từ chỗ xe</small>}</div>}
      {plan.detour && <div className="im-road"><span>Lộ trình mới: <b>{plan.detour.km} km · {plan.detour.hours < 1 ? `${Math.round(plan.detour.hours * 60)} phút` : `${plan.detour.hours.toFixed(1)} giờ`}</b></span><small>{old.km > 0 ? `Đường cũ ${old.km} km · ` : ''}Giữ nguyên cửa khẩu đã chốt</small></div>}
    </IncidentMap>
  )
}
