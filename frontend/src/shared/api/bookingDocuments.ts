import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type DocumentChecklist = Schemas['BookingDocumentChecklistResponseDto']
export type ChecklistItem = Schemas['Item']
export type BookingDocument = Schemas['BookingDocumentResponseDto']

export const bookingDocumentsApi = {
  myChecklist: async (bookingId: number) =>
    (await apiClient.get<ApiResponse<DocumentChecklist>>(`/bookings/${bookingId}/documents/my-checklist`)).data.data,
  upload: async (bookingId: number, item: ChecklistItem, file: File, expiryDate: string) => {
    const body = new FormData()
    body.append('requirementId', String(item.requirement?.id))
    if (item.bookingHorseId != null) body.append('bookingHorseId', String(item.bookingHorseId))
    if (expiryDate) body.append('expiryDate', expiryDate)
    body.append('file', file)
    return (await apiClient.post<ApiResponse<BookingDocument>>(`/bookings/${bookingId}/documents/upload`, body,
      { headers: { 'Content-Type': 'multipart/form-data' } })).data.data
  },
}
