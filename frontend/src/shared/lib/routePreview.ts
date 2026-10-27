import type { RoutePreviewResponse } from '../api/routes'
import type { TransitStation } from '../config/network'
import type { RoadRoute } from '../services/routing'
import type { RestStop, RouteLeg, RoutePlan } from '../types/booking'

const STOPS = new Set(['REST', 'ROADSIDE_REST', 'OVERNIGHT'])
const ROADSIDE = 'Nghỉ dọc đường'

// Backend đặt tên chỗ nghỉ dọc đường theo trạm gần nhất trong 20 km, nhưng xe không dừng ở trạm đó
const restName = (type: string, place: string) =>
  type === 'ROADSIDE_REST' && !place.startsWith(ROADSIDE) ? `${ROADSIDE} gần ${place}` : place

// Đơn nội địa chỉ chọn trạm ở Việt Nam; đơn quốc tế chọn được mọi trạm
export function stationsForBooking(stations: TransitStation[], international: boolean): TransitStation[] {
  return international ? stations : stations.filter(s => s.country === 'VN')
}

// Lịch trình backend → dạng RoutePlan cũ: mỗi chỗ nghỉ (trạm, nghỉ dọc đường, nghỉ đêm) chia chặng, rests[i] ở cuối legs[i];
// chờ và làm thủ tục cửa khẩu nằm trong chặng (giờ tới cửa khẩu ở borderEta) và tính là nghỉ:
// hours của chặng = đoạn lái liên tục dài nhất trong chặng. manualNames: tên các trạm do điều phối viên chọn tay (đánh dấu manual).
export function routePlanFromPreview(preview: RoutePreviewResponse, originName: string, destName: string, manualNames: string[] = []): Pick<RoutePlan, 'legs' | 'rests' | 'borderEta'> {
  const legs: RouteLeg[] = []
  const rests: RestStop[] = []
  let from = originName
  let departAt: number | undefined
  let arriveAt = preview.schedule[0]?.startAt ?? preview.arrivalAt
  let km = 0
  let stretch = 0
  let longest = 0
  const close = (to: string) => {
    legs.push({ no: legs.length + 1, from, to, departAt: departAt ?? arriveAt, arriveAt, km: Math.round(km * 10) / 10, hours: Math.round(longest / 36) / 100 })
    from = to
    departAt = undefined
    km = 0
    stretch = 0
    longest = 0
  }
  for (const e of preview.schedule) {
    if (e.type === 'DRIVE') {
      departAt ??= e.startAt
      arriveAt = e.endAt
      km += e.km
      stretch += (e.endAt - e.startAt) / 1000
      longest = Math.max(longest, stretch)
    } else if (e.type === 'BORDER_WAIT' || e.type === 'BORDER_PROCESSING') {
      stretch = 0
    } else if (STOPS.has(e.type)) {
      const name = restName(e.type, e.place)
      close(name)
      rests.push({
        afterLeg: legs.length,
        name,
        minutes: Math.round((e.endAt - e.startAt) / 60000),
        ...(e.lat != null && e.lng != null ? { lat: e.lat, lng: e.lng } : {}),
        ...(manualNames.includes(name) ? { manual: true } : {}),
      })
      arriveAt = e.endAt
    }
  }
  close(destName)
  return { legs, rests, borderEta: preview.borderArrivalAt ?? undefined }
}

// Đường vẽ và số liệu tổng của tuyến xe tải backend, theo dạng RoadRoute mà bản đồ đang dùng
export function roadFromPreview(preview: RoutePreviewResponse): RoadRoute {
  return {
    path: preview.path,
    km: preview.km,
    hours: preview.driveHours,
    legs: preview.legs.map(l => ({ km: l.km, hours: l.driveHours })),
    source: 'valhalla',
    traffic: false,
  }
}
