import { createRoot } from 'react-dom/client'
import { TripTrackMap } from '../../src/shared/ui/TripTrackMap'
import type { Booking } from '../../src/shared/types/booking'
import '../../src/shared/styles/base.css'
// Lộ trình đã chốt có một trạm trong danh mục và hai chỗ nghỉ dọc đường (chỉ có tọa độ, không có trong danh mục)
const b = {
  origin: { id: 'A', country: 'VN', name: 'Thủ Đức' }, dest: { id: 'B', country: 'KH', name: 'Battambang' }, gate: undefined,
  route: {
    legs: [], path: [[10.85, 106.77], [11.26, 105.28], [12.22, 104.63], [12.53, 104.11], [13.1, 103.2]],
    rests: [
      { afterLeg: 1, name: 'Trạm nghỉ Neak Loeung', minutes: 30, lat: 11.26, lng: 105.28 },
      { afterLeg: 2, name: 'Nghỉ dọc đường (km 378)', minutes: 30, lat: 12.219, lng: 104.632 },
      { afterLeg: 3, name: 'Nghỉ dọc đường (km 451)', minutes: 600, lat: 12.528, lng: 104.106, overnight: true },
    ],
  },
} as unknown as Booking
createRoot(document.getElementById('root')!).render(<TripTrackMap b={b} trips={[]} plain height={300} />)
