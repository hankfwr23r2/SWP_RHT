import apiClient, { type ApiResponse } from './client'
import type { components } from './generated/schema'

type Schemas = components['schemas']
export type Country = Schemas['CountryResponse']
export type Province = Schemas['ProvinceResponse']
export type StallType = Schemas['StallTypeResponseDto']
export type Insurer = Schemas['PartyResponseDto']
export type BillableService = Schemas['BillableServiceResponseDto']

function catalogList<T>(path: string) {
  const requests = new Map<string, Promise<T[]>>()
  return (country = '') => {
    const key = country.trim().toUpperCase()
    let request = requests.get(key)
    if (!request) {
      request = apiClient.get<ApiResponse<T[]>>(path, key ? { params: { country: key } } : undefined)
        .then(response => response.data.data)
        .catch(error => {
          requests.delete(key)
          throw error
        })
      requests.set(key, request)
    }
    return request
  }
}

const countries = catalogList<Country>('/countries')
const provinces = catalogList<Province>('/provinces')
const stallTypes = catalogList<StallType>('/stall-types')
const insurers = catalogList<Insurer>('/parties/insurers')
const billableServices = catalogList<BillableService>('/billable-services')

export const catalogApi = {
  countries: () => countries(),
  provinces: (country: string) => provinces(country),
  stallTypes: () => stallTypes(),
  insurers: () => insurers(),
  billableServices: () => billableServices(),
}
