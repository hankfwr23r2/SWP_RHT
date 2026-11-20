import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { IncidentMap } from '../../src/apps/backoffice/shared/IncidentMap'
import { insertWaypoint } from '../../src/shared/lib/waypoints'
import type { GeoPoint } from '../../src/shared/config/network'
import '../../src/shared/styles/base.css'
// Xe báo tắc đường ở phía nam, điểm kế tiếp ở phía bắc: điều phối bấm điểm đi qua như màn lập phương án
const at = { lat: 10.6, lng: 106.0 }, next = { lat: 11.5, lng: 106.0 }
function Harness() {
  const [points, setPoints] = useState<GeoPoint[]>([])
  ;(window as unknown as { points: GeoPoint[] }).points = points
  return <IncidentMap height={600} location={at} origin={{ lat: 10, lng: 106, name: 'Đón' }} dest={{ lat: 12, lng: 106, name: 'Trả' }}
    lines={[{ path: [[10.6, 106.0], [11.5, 106.0]], color: '#ea580c' }]} waypoints={points}
    onPoint={p => setPoints(list => insertWaypoint(at, list, next, p))}
    onMoveWaypoint={(index, p) => setPoints(list => list.map((x, i) => (i === index ? p : x)))}
    onRemoveWaypoint={index => setPoints(list => list.filter((_, i) => i !== index))} />
}
createRoot(document.getElementById('root')!).render(<Harness />)
