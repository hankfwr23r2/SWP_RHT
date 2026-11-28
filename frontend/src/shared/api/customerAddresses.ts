import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type CustomerAddress = Schemas['CustomerAddressResponseDto']
export type CustomerAddressCreate = Schemas['CustomerAddressCreateRequestDto']
export type CustomerAddressUpdate = Schemas['CustomerAddressUpdateRequestDto']

const path = '/customers/me/addresses'

// The shared interceptor flattens validation details into a message. Keep the
// structured 400/409 envelope for forms while preserving normal 401 handling.
export const formResponseOptions = {
  validateStatus: (status: number) => (status >= 200 && status < 300) || status === 400 || status === 409,
}

export function readFormResponse<T>(response: { status: number; data: ApiResponse<T | Record<string, string> | null> }): T {
  const { status, data } = response
  if (status >= 400) {
    throw Object.assign(new Error(data?.message), { status, fields: (data?.data ?? {}) as Record<string, string> })
  }
  return data.data as T
}

export const customerAddressesApi = {
  list: async () => (await apiClient.get<ApiResponse<CustomerAddress[]>>(path)).data.data,
  create: async (body: CustomerAddressCreate) =>
    readFormResponse<CustomerAddress>(await apiClient.post(path, body, formResponseOptions)),
  update: async (id: number, body: CustomerAddressUpdate) =>
    readFormResponse<CustomerAddress>(await apiClient.put(`${path}/${id}`, body, formResponseOptions)),
  remove: async (id: number) => { await apiClient.delete(`${path}/${id}`) },
}
