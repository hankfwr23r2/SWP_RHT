// Tra cứu đơn công khai (trang chủ): mã đơn + 4 số cuối SĐT người gửi. Dữ liệu lấy từ bộ đơn chuẩn (services/bookings.ts).
import { publicBookingsApi, type PublicTracking } from './bookings'

export type { PublicTracking }
export const trackOrders = (codes: string[], phoneLast4: string, captchaToken: string) =>
  publicBookingsApi.trackMany(codes, phoneLast4, captchaToken)
