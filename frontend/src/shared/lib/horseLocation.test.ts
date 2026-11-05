import { describe, expect, it } from 'vitest'
import { horsePickupLocationStatus } from './horseLocation'
import { setNetworkLocations } from '../config/network'

const compare = (currentLocationName: string | undefined, pickup: string, currentCountry = 'VN') =>
  horsePickupLocationStatus({ currentLocationName, currentCountry }, pickup, 'VN')

describe('horse pickup location comparison', () => {
  it('matches accents, case, punctuation and reordered address components', () => {
    expect(compare('Trang trại A, Phường Tây Hồ, Thành phố Hà Nội',
      'HA NOI, phuong tay ho,  Trang trai A.')).toBe('match')
  })

  it('does not treat Hanoi alone as proof of a matching pickup', () => {
    expect(compare('Hà Nội', 'Trang trại A, Hà Nội')).toBe('unverified')
    expect(compare('Hà Nội', 'Hà Nội')).toBe('unverified')
    expect(compare('Trang trại A, Hà Nội', 'Hà Nội')).toBe('unverified')
  })

  it('keeps different farms and house numbers distinct in the same city', () => {
    expect(compare('Trang trại A, Hà Nội', 'Trang trại B, Hà Nội')).toBe('different')
    expect(compare('Trang trại 1, Hà Nội', 'Trang trại 10, Hà Nội')).toBe('different')
    expect(compare('Số 12 Đường A, Hà Nội', 'Số 21 Đường A, Hà Nội')).toBe('different')
  })

  it('requires confirmation for missing or partial location information', () => {
    expect(compare(undefined, 'Trang trại A, Hà Nội')).toBe('unverified')
    expect(compare('Trang trại A', 'Trang trại A, Hà Nội')).toBe('unverified')
    expect(compare('Trang trại A', '')).toBe('unverified')
  })

  it('detects a different province or country', () => {
    expect(compare('Hà Nội', 'Trang trại A, Đà Nẵng')).toBe('different')
    expect(compare('Trang trại A', 'Trang trại A', 'KH')).toBe('different')
  })

  it('resolves exact catalog identifiers without substring matching', () => {
    setNetworkLocations([{
      id: 900, locationCode: 'TEST-FARM', locationType: 'RidingClub',
      locationName: 'Trang trại A', city: 'Hà Nội', province: 'Hà Nội', country: 'VN',
      lat: 21, lng: 105,
    }])
    try {
      expect(compare('Trang trại A', 'TEST-FARM')).toBe('match')
      expect(compare('Trang trại A — Hà Nội', 'TEST-FARM')).toBe('unverified')
    } finally {
      setNetworkLocations([])
    }
  })
})
