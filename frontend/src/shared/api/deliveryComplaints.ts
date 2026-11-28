import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type DeliveryComplaint = Schemas['DeliveryComplaintResponseDto']
export type Complaint = Schemas['ComplaintResponseDto']
export type ComplaintDecision = NonNullable<Schemas['ComplaintResolveRequestDto']['decision']>

const path = (bookingId: number) => `/bookings/${bookingId}/delivery-complaints`

export class DeliveryComplaintValidationError extends Error {
  readonly fields: Record<string, string>

  constructor(message: string, fields: Record<string, string>) {
    super(message)
    this.fields = fields
  }
}

async function send(url: string, body: unknown) {
  const { data, status } = await apiClient.post<ApiResponse<DeliveryComplaint>>(url, body, {
    headers: body instanceof FormData ? { 'Content-Type': 'multipart/form-data' } : undefined,
    validateStatus: code => code === 400 || (code >= 200 && code < 300),
  })
  if (status === 400) {
    const fields: Record<string, string> = {}
    for (const [field, value] of Object.entries(data.data ?? {})) {
      if (typeof value === 'string') fields[field] = value
    }
    throw new DeliveryComplaintValidationError(data.message, fields)
  }
  return data.data
}

export const deliveryComplaintsApi = {
  async view(bookingId: number) {
    return (await apiClient.get<ApiResponse<DeliveryComplaint>>(path(bookingId))).data.data
  },
  confirm: (bookingId: number) => send(`${path(bookingId)}/confirm`, { confirmed: true } satisfies Schemas['DeliveryConfirmationRequestDto']),
  submit: (bookingId: number, body: FormData) => send(path(bookingId), body),
  submitOnBehalf: (bookingId: number, body: FormData) => send(`${path(bookingId)}/submit-on-behalf`, body),
  reply: (bookingId: number, body: Schemas['ComplaintReplyRequestDto']) => send(`${path(bookingId)}/reply`, body),
  resolve: (bookingId: number, body: Schemas['ComplaintResolveRequestDto']) => send(`${path(bookingId)}/resolve`, body),
}
