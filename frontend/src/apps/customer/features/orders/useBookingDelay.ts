import { bookingOrdersApi, type BookingReschedule } from '@shared/api/bookingOrders'
import { useLoad } from '@shared/hooks/useLoad'

export function useBookingDelay(bookingId: number) {
  const state = useLoad(() => bookingOrdersApi.delay(bookingId), [bookingId], { toastOnError: false })

  return {
    ...state,
    data: state.data?.[0],
    reschedule: (body: BookingReschedule) => bookingOrdersApi.delayReschedule(bookingId, body),
    refundPreview: () => bookingOrdersApi.delayRefundPreview(bookingId),
    refund: (reason: string) => bookingOrdersApi.delayRefund(bookingId, reason),
  }
}
