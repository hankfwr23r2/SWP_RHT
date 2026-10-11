// Xem lại phương án sự cố trên bản đồ (chỉ đọc): Quản lý duyệt, Tài xế và Hộ tống làm theo
import { GATES, TRANSIT_STATIONS } from '@shared/config/network'
import { INCIDENT_ACTION } from '@shared/config/booking-rules'
import { findLocation } from '@shared/lib/booking'
import type { Booking, Incident } from '@shared/types/booking'
import { IncidentMap, type MapLine } from './IncidentMap'

export function IncidentPlanView({ b, i, height = 340 }: { b: Booking; i: Incident; height?: number }) {
  const plan = i.plan
  const origin = findLocation(b.origin.id), dest = findLocation(b.dest.id)
  if (!plan || !origin || !dest) return null
  const gate = b.gate ? GATES.find(g => g.name === b.gate) : undefined
  const station = plan.station ? TRANSIT_STATIONS.filter(x => x.name === plan.station) : []
  const lines: MapLine[] = [
    ...(plan.rescueLine ? [{ path: plan.rescueLine.path, color: '#059669', flow: true, label: `Cứu hộ tới chỗ xe: ${plan.rescueLine.km} km` }] : []),
    ...(plan.toStation ? [{ path: plan.toStation.path, color: '#ea580c', flow: true, weight: 6, label: `Đưa ngựa tới ${plan.station}: ${plan.toStation.km} km` }] : []),
    ...(plan.detour ? [{ path: plan.detour.path, color: '#2563eb', flow: true, weight: 6, label: `Lộ trình mới: ${plan.detour.km} km` }] : []),
  ]
  return (
    <IncidentMap height={height} location={i.location} origin={{ ...origin, name: b.origin.name }} dest={{ ...dest, name: b.dest.name }} gate={gate ? { ...gate } : undefined}
      stations={station} selectedStation={plan.station}
      rescues={plan.rescue ? [{ ...plan.rescue, area: '' }] : []} selectedRescue={plan.rescue?.name} lines={lines}>
      <div className="im-head"><span className="im-icon"><i className="fa-solid fa-route" /></span><div><b>Phương án đã lập</b><small>{INCIDENT_ACTION[plan.action]}</small></div></div>
      {plan.rescue && <div className="im-road"><span>Cứu hộ: <b>{plan.rescue.name}</b></span><small>{plan.rescue.phone}{plan.rescueLine ? ` · ${plan.rescueLine.km} km` : ''}</small></div>}
      {plan.station && <div className="im-road"><span>Trạm nghỉ: <b>{plan.station}</b>{plan.restMinutes ? ` · nghỉ ${plan.restMinutes} phút` : ''}</span>{plan.toStation && <small>{plan.toStation.km} km từ chỗ xe</small>}</div>}
      {plan.detour && <div className="im-road"><span>Lộ trình mới: <b>{plan.detour.km} km · {plan.detour.hours < 1 ? `${Math.round(plan.detour.hours * 60)} phút` : `${plan.detour.hours.toFixed(1)} giờ`}</b></span><small>Giữ nguyên cửa khẩu đã chốt</small></div>}
    </IncidentMap>
  )
}
