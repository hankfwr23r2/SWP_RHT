// Chuyến đi đường bộ: ngày giao dự kiến, số giờ đi đường, chia chặng. Gốc: thu_tuc.js (tripDays, deliveryDate, expiresEarly).
import { DAY } from '../config/business-rules'
import { dayKey } from './dates'

// Số ngày đi đường theo thời gian dự kiến của đơn: "2 ngày" → 2; đi trong ngày ("~6 giờ") → 1
export const tripDays = (duration: string) => Number(/(\d+)\s*ngày/.exec(duration)?.[1] ?? 1)
export const deliveryDate = (departAt: number, duration: string) => departAt + tripDays(duration) * DAY

// Giấy có thời hạn phải còn hiệu lực đến hết ngày giao dự kiến
export const expiresEarly = (validUntil: number, departAt: number, duration: string) =>
  dayKey(new Date(validUntil)) < dayKey(new Date(deliveryDate(departAt, duration)))

// Số giờ đi đường (tính giờ giao dự kiến khi khởi hành)
export function tripHours(duration: string) {
  const hours = /([\d.]+)\s*giờ/.exec(duration)
  return hours ? Number(hours[1]) : tripDays(duration) * 24
}

// Điểm dừng dạng "Nơi — việc làm"
export const splitStop = (stop: string) => {
  const [place, act = ''] = stop.split(' — ')
  return { place, act }
}

// Chặng xe chạy [từ, đến] theo danh sách điểm dừng; đoạn qua cửa khẩu (thông quan → kiểm tra thú y) không tính là chặng.
// Đơn chưa có điểm dừng: 1 chặng đi thẳng từ điểm đi tới điểm đến.
export function legsFromStops(stops: string[] | undefined, from: string, to: string): [string, string][] {
  if (!stops?.length) return [[from, to]]
  const points = stops.map(splitStop)
  const crossing = (i: number) => points[i].act === 'thông quan' && points[i + 1].act === 'kiểm tra thú y'
  return points.slice(0, -1).flatMap((p, i) => (crossing(i) ? [] : [[p.place, points[i + 1].place] as [string, string]]))
}
