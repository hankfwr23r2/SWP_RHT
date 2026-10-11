// Tra cứu đơn công khai (trang chủ): mã đơn + 4 số cuối SĐT người gửi. Dữ liệu lấy từ bộ đơn chuẩn (services/bookings.ts).
import { publicBookingsApi, type PublicTracking } from './bookings'

export type { PublicTracking }
export const trackOrder = (code: string, phoneLast4: string): Promise<PublicTracking | null> => publicBookingsApi.track(code, phoneLast4)
