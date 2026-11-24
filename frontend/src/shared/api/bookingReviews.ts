import apiClient, { type ApiResponse } from './client'
import { formResponseOptions, readFormResponse } from './customerAddresses'
import type { BookingDetail, BookingOrder, BookingStatus } from './bookingOrders'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type BookingSummary = Schemas['BookingSummaryResponseDto']
export type InsuranceEvidence = Schemas['InsuranceEvidenceRequestDto']

const bookingPath = (id: number) => `/bookings/${id}`
const insurancePath = (id: number, insuranceId: number) => `${bookingPath(id)}/insurances/${insuranceId}`

async function post<T>(path: string, body: unknown) {
  return readFormResponse<T>(await apiClient.post(path, body, formResponseOptions))
}

export const bookingReviewsApi = {
  summaries: async (status: BookingStatus, page: number) =>
    (await apiClient.get<ApiResponse<BookingSummary[]>>('/bookings/summaries', { params: { statuses: status, page } })).data.data,
  detail: async (id: number) =>
    (await apiClient.get<ApiResponse<BookingDetail>>(bookingPath(id) + '/review-detail')).data.data,
  missingDocuments: async () =>
    (await apiClient.get<ApiResponse<BookingOrder[]>>('/bookings/missing-documents')).data.data,
  intake: (id: number) => post<BookingOrder>(bookingPath(id) + '/intake', {}),
  returnForResubmission: (id: number, body: Schemas['BookingReturnRequestDto']) =>
    post<BookingOrder>(bookingPath(id) + '/return-for-resubmission', body),
  reject: (id: number, body: Schemas['BookingRejectionRequestDto']) => post<BookingOrder>(bookingPath(id) + '/reject', body),
  changeDeparture: (id: number, departureId: number) => post<BookingOrder>(bookingPath(id) + '/change-departure', { departureId }),
  startQuoting: (id: number) => post<BookingOrder>(bookingPath(id) + '/start-quoting', {}),
  recordEvidence: (id: number, insuranceId: number, body: InsuranceEvidence) =>
    post<null>(insurancePath(id, insuranceId) + '/record-evidence', body),
  confirmInsurance: (id: number, insuranceId: number) => post<null>(insurancePath(id, insuranceId) + '/confirm', {}),
  requestInsuranceRevision: (id: number, insuranceId: number, body: Schemas['InsuranceRevisionReasonRequestDto']) =>
    post<null>(insurancePath(id, insuranceId) + '/request-revision', body),
  reopenInsurance: (id: number, insuranceId: number, body: Schemas['InsuranceReopenRequestDto']) =>
    post<null>(insurancePath(id, insuranceId) + '/reopen', body),
}
