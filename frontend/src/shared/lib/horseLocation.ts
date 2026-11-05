import { PROVINCES_DATA } from '../config/administrative'
import { COUNTRY_LOCATIONS } from '../config/network'
import { normalizeCountry } from '../config/runtimeDocumentRequirements'
import type { HorseProfile } from '../types/booking'

export type PickupLocationStatus = 'match' | 'different' | 'unverified'

// Compare complete address components, never substrings (e.g. farm 1 vs farm 10).
const normalize = (value: string) => value.normalize('NFD')
  .replace(/\p{M}/gu, '').toLowerCase().replace(/đ/g, 'd')
  .replace(/[^\p{L}\p{N}\s]/gu, ' ').replace(/\s+/g, ' ').trim()
  .replace(/^(?:thanh pho|tp|tinh)\s+/, '')

const components = (address: string) => address.split(/[,;|\n]|\s+[—–]\s+/u)
  .map(normalize).filter(Boolean).sort()

export function horsePickupLocationStatus(
  horse: Pick<HorseProfile, 'currentLocationName' | 'currentCountry'>,
  pickupAddress: string,
  originCountry?: string,
): PickupLocationStatus {
  const country = normalizeCountry(originCountry)
  const currentCountry = normalizeCountry(horse.currentCountry)
  if (country && currentCountry && country !== currentCountry) return 'different'
  const current = horse.currentLocationName?.trim()
  if (!current || !pickupAddress.trim()) return 'unverified'

  const catalog = Object.entries(COUNTRY_LOCATIONS)
    .filter(([code]) => !country || code === country).flatMap(([, locations]) => locations)
  const resolve = (value: string) => catalog.find(location => location.id === value)?.name ?? value
  const left = components(resolve(current))
  const right = components(resolve(pickupAddress))
  if (!left.length || !right.length) return 'unverified'

  const provinces = PROVINCES_DATA.filter(province => !country || province.country === country)
  const administrativeNames = new Set(provinces.flatMap(province =>
    [normalize(province.name), ...province.wards.map(ward => normalize(ward.name))]))
  const leftSpecific = left.filter(part => !administrativeNames.has(part))
  const rightSpecific = right.filter(part => !administrativeNames.has(part))
  const leftProvince = provinces.find(province => left.includes(normalize(province.name)))
  const rightProvince = provinces.find(province => right.includes(normalize(province.name)))
  if (leftProvince && rightProvince && leftProvince.code !== rightProvince.code) return 'different'
  if (!leftSpecific.length || !rightSpecific.length) return 'unverified'

  if (left.length === right.length && left.every((part, index) => part === right[index])) return 'match'
  // A partial address can refer to the same place, but requires the owner's confirmation.
  if (left.every(part => right.includes(part)) || right.every(part => left.includes(part))) return 'unverified'
  return 'different'
}
