import type { BookingSummary } from '@shared/api/bookingReviews'
import type { Departure } from '@shared/api/dispatch'

const day = (date: Date) => date.toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' })
const WEEKS_AHEAD = 8

// Khoảng ngày tìm chuyến: từ hôm nay, đủ số tuần hệ thống mở chuyến trước
export const searchRange = (now: Date) => ({ from: day(now), to: day(new Date(now.getTime() + WEEKS_AHEAD * 7 * 24 * 3600 * 1000)) })

// Chuyến khác cùng tuyến còn nhận; backend kiểm lại khi đổi
export const departureOptions = (departures: Departure[], booking: BookingSummary) =>
  departures.filter(departure => departure.id !== booking.departureId && departure.routeName === booking.routeName
    && departure.status !== 'CANCELLED' && departure.status !== 'COMPLETED' && departure.status !== 'IN_PROGRESS')
