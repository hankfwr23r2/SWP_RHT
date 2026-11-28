import apiClient, { type ApiResponse } from './client'
import { formResponseOptions, readFormResponse } from './customerAddresses'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type PriceList = Schemas['PriceListResponseDto']
export type PriceListCreate = Schemas['PriceListCreateRequestDto']
export type PriceListUpdate = Schemas['PriceListUpdateRequestDto']
export type PriceItem = Schemas['PriceItemResponseDto']
export type PriceItemCreate = Schemas['PriceItemCreateRequestDto']
export type PriceItemUpdate = Schemas['PriceItemUpdateRequestDto']
export type VehicleClass = Schemas['VehicleClassResponseDto']

const path = '/price-lists'
const get = async <T>(url: string) => (await apiClient.get<ApiResponse<T>>(url)).data.data
const post = async <T>(url: string, body: unknown) => readFormResponse<T>(await apiClient.post(url, body, formResponseOptions))
const put = async <T>(url: string, body: unknown) => readFormResponse<T>(await apiClient.put(url, body, formResponseOptions))
const remove = async (url: string) => readFormResponse(await apiClient.delete(url, formResponseOptions))

export const priceListsApi = {
  list: () => get<PriceList[]>(path),
  detail: (id: number) => get<PriceList>(`${path}/${id}`),
  create: (body: PriceListCreate) => post<PriceList>(path, body),
  update: (id: number, body: PriceListUpdate) => put<PriceList>(`${path}/${id}`, body),
  remove: (id: number) => remove(`${path}/${id}`),
  activate: (id: number) => post<PriceList>(`${path}/${id}/activate`, {} satisfies Schemas['PriceListActivateRequestDto']),
  archive: (id: number) => post<PriceList>(`${path}/${id}/archive`, {} satisfies Schemas['PriceListArchiveRequestDto']),
  items: (id: number) => get<PriceItem[]>(`${path}/${id}/items`),
  createItem: (id: number, body: PriceItemCreate) => post<PriceItem>(`${path}/${id}/items`, body),
  updateItem: (id: number, itemId: number, body: PriceItemUpdate) => put<PriceItem>(`${path}/${id}/items/${itemId}`, body),
  removeItem: (id: number, itemId: number) => remove(`${path}/${id}/items/${itemId}`),
  vehicleClasses: () => get<VehicleClass[]>('/vehicle-classes'),
}
