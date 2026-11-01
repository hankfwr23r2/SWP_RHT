export type CapturePurpose = 'ARRIVE_PICKUP' | 'ARRIVE_CHECKPOINT' | 'UPLOAD_HANDOVER' | 'CUSTOMS_CLEARED' | 'CONFIRM_HOME' | 'EXCLUDE_UNFIT_HORSE' | 'SUBMIT_WELFARE' | 'SOS' | 'EXPENSE'
export interface CaptureEvidence {
  id: number
  url: string
  bookingId: number
  tripId: string
  purpose: CapturePurpose
  target: string
  capturedBy: string
  capturedAt: number
  recordedAt: number
}
export interface CaptureContext { bookingId: string; tripId: string }
