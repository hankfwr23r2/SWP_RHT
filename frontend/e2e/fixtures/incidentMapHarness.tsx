import { createRoot } from 'react-dom/client'
import { IncidentMap, NEW_ROUTE, oldRouteLines } from '../../src/apps/backoffice/shared/IncidentMap'
import '../../src/shared/styles/base.css'
// Lộ trình đã lập đi thẳng theo kinh tuyến; xe báo tắc đường ở giữa, đường tránh vòng sang phía đông rồi về điểm kế tiếp
const planned: [number, number][] = [[10.0, 106.0], [10.5, 106.0], [11.0, 106.0], [11.5, 106.0], [12.0, 106.0]]
const at = { lat: 10.6, lng: 106.0 }, next = { lat: 11.5, lng: 106.0 }
const old = oldRouteLines(planned, at, next)
const detour: [number, number][] = [[10.6, 106.0], [10.9, 106.3], [11.3, 106.3], [11.5, 106.0]]
;(window as unknown as { oldKm: number }).oldKm = old.km
createRoot(document.getElementById('root')!).render(
  <IncidentMap height={420} location={at} origin={{ lat: 10, lng: 106, name: 'Đón' }} dest={{ lat: 12, lng: 106, name: 'Trả' }}
    lines={[...old.lines, { path: detour, color: NEW_ROUTE, weight: 6, label: 'Đường tránh', legend: 'Đường mới' }]} />,
)
