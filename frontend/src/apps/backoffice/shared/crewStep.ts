// Bước của tài xế/hộ tống trên app trước khi xe lăn bánh.
// Đơn chỉ sẵn sàng đón ngựa khi giấy tờ đã xong và cả kíp xe đã nhận lệnh.
import type { Booking, Clearance, VehicleTrip } from '@shared/types/booking'

export type CrewStep = 'acknowledge' | 'waiting_documents' | 'waiting_crew' | 'ready' | 'departed'

export function crewStep(
  booking: Pick<Booking, 'status'> & { clearance?: Pick<Clearance, 'doneAt'> },
  trip: Pick<VehicleTrip, 'acks' | 'departedAt'>,
  role: 'driver' | 'escort',
): CrewStep {
  if (trip.departedAt) return 'departed'
  if (!trip.acks?.[role]) return 'acknowledge'
  if (booking.status === 'ready_for_pickup') return 'ready'
  if (booking.clearance?.doneAt) return 'waiting_crew'
  return 'waiting_documents'
}
