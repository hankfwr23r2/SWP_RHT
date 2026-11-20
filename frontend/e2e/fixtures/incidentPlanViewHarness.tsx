import { createRoot } from 'react-dom/client'
import { setNetworkLocations } from '../../src/shared/config/network'
import { IncidentPlanView } from '../../src/apps/backoffice/shared/IncidentPlanView'
import type { Booking, Incident } from '../../src/shared/types/booking'
import '../../src/shared/styles/base.css'
// Điểm đón và điểm trả có trong danh mục; lộ trình đi thẳng theo kinh tuyến 106
setNetworkLocations([
  { id: 1, locationCode: 'A', locationType: 'City', locationName: 'Đón', city: '', province: '', country: 'Việt Nam', latitude: 10, longitude: 106, status: 'ACTIVE' },
  { id: 2, locationCode: 'B', locationType: 'City', locationName: 'Trả', city: '', province: '', country: 'Việt Nam', latitude: 12, longitude: 106, status: 'ACTIVE' },
])
const incident = {
  id: 'INC-1', tripId: 'T1', kind: 'traffic_jam', reportedBy: 'driver01', reportedAt: 100, location: { lat: 10.5, lng: 106 }, photo: '', note: '', status: 'approved', expenses: [],
  approval: { at: 200, by: 'manager' },
  plan: { action: 'reroute', note: '', newEta: 0, at: 150, by: 'co', detour: { path: [[10.5, 106], [10.7, 106.3], [11.3, 106.3], [11.5, 106]], km: 90, hours: 2 } },
} as unknown as Incident
const b = {
  origin: { id: 'A', country: 'VN', name: 'Đón' }, dest: { id: 'B', country: 'VN', name: 'Trả' },
  route: { legs: [], rests: [], path: [[10, 106], [10.5, 106], [11, 106], [11.5, 106], [12, 106]] }, incidents: [incident],
} as unknown as Booking
createRoot(document.getElementById('root')!).render(<IncidentPlanView b={b} i={incident} height={420} />)
