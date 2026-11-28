// Dữ liệu dùng chung của 4 bước đặt đơn. Danh mục nhớ sẵn trong phiên nên chuyển bước chỉ tải lại địa chỉ và ngựa.
import { useLoaderData } from 'react-router'
import { bookingOrdersApi } from '@shared/api/bookingOrders'
import { catalogApi, type Province } from '@shared/api/catalog'
import { customerAddressesApi, type CustomerAddress } from '@shared/api/customerAddresses'
import { customerHorseApi } from '@shared/api/horses'
import { loadAll } from '@shared/routing/pageData'

async function provincesOf(addresses: CustomerAddress[]): Promise<Province[]> {
  const countries = [...new Set(addresses.flatMap(address => address.countryCode ?? []))]
  return (await Promise.all(countries.map(country => catalogApi.provinces(country)))).flat()
}

export async function bookingLoader() {
  const addresses = customerAddressesApi.list()
  const loaded = await loadAll({
    addresses,
    provinces: addresses.then(provincesOf),
    countries: catalogApi.countries(),
    horses: customerHorseApi.list(),
    stallTypes: catalogApi.stallTypes(),
    services: catalogApi.billableServices(),
  })
  return {
    errors: [] as string[],
    failed: loaded.errors.length > 0,
    addresses: loaded.addresses ?? [],
    provinces: (loaded.provinces ?? []) as Province[],
    countries: loaded.countries ?? [],
    horses: loaded.horses ?? [],
    stallTypes: loaded.stallTypes ?? [],
    services: loaded.services ?? [],
  }
}

export type BookingData = Awaited<ReturnType<typeof bookingLoader>>

export const useBookingData = () => useLoaderData() as BookingData

export const addressText = (data: BookingData, address: CustomerAddress) => [
  address.addressLine,
  address.ward,
  data.provinces.find(province => province.code === address.provinceCode)?.name,
  data.countries.find(country => country.code === address.countryCode)?.name,
].filter(Boolean).join(', ')

export const findAddress = (data: BookingData, id: number | null) =>
  id == null ? undefined : data.addresses.find(address => address.id === id)

export const loadProfile = () => bookingOrdersApi.myProfile()
