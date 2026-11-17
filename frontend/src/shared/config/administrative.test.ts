import { describe, expect, it } from 'vitest'
import { findLocation } from '../lib/booking'
import { PROVINCES_DATA, buildFullAddress, splitAddress } from './administrative'

describe('danh mục tỉnh/xã sau sáp nhập 07/2025', () => {
  it('có đủ 34 tỉnh/thành và 3.321 phường/xã của Việt Nam', () => {
    const vn = PROVINCES_DATA.filter(p => p.country === 'VN')
    expect(vn).toHaveLength(34)
    expect(vn.reduce((sum, p) => sum + p.wards.length, 0)).toBe(3321)
  })

  it('Campuchia có 25 tỉnh/197 huyện, Lào có 18 tỉnh/148 huyện, huyện nào cũng có tọa độ riêng', () => {
    const count = (country: 'KH' | 'LA') => {
      const list = PROVINCES_DATA.filter(p => p.country === country)
      return [list.length, list.reduce((sum, p) => sum + p.wards.length, 0)]
    }
    expect(count('KH')).toEqual([25, 197])
    expect(count('LA')).toEqual([18, 148])
    const sameAsProvince = PROVINCES_DATA.filter(p => p.country !== 'VN')
      .flatMap(p => p.wards.filter(w => w.lat === p.lat && w.lng === p.lng))
    expect(sameAsProvince).toEqual([])
  })

  it('tách lại đúng địa chỉ mà ô chọn ghép ra', () => {
    const address = buildFullAddress('Trại Hòa Bình', 'Xã Long Phước', 'Tỉnh Đồng Nai')

    expect(splitAddress(address, 'VN')).toEqual({ provinceCode: 'VN-75', wardName: 'Xã Long Phước', detail: 'Trại Hòa Bình' })
  })

  it('không nhận nhầm xã khi địa chỉ chi tiết chứa tên giống tên xã khác', () => {
    const address = 'Ấp An Phú Đông, Phường Biên Hòa, Tỉnh Đồng Nai'

    expect(splitAddress(address, 'VN').wardName).toBe('Phường Biên Hòa')
  })
})

describe('tọa độ theo địa chỉ', () => {
  it('xã đã biết tọa độ thì lấy tọa độ xã', () => {
    expect(findLocation('Trại Hòa Bình, Xã Long Phước, Tỉnh Đồng Nai')).toMatchObject({ lat: 10.74, lng: 107.01 })
  })

  it('xã chưa có tọa độ thì tạm lấy tọa độ tỉnh', () => {
    expect(findLocation('Trại A, Phường Biên Hòa, Tỉnh Đồng Nai')).toMatchObject({ lat: 10.957, lng: 106.843 })
  })

  it('Campuchia, Lào lấy tọa độ của huyện', () => {
    expect(findLocation('Chuong ngua A, Siem Reap, Siem Reap')).toMatchObject({ lat: 13.351, lng: 103.85 })
    expect(splitAddress('Trai B, Kaysone Phomvihane, Savannakhet', 'LA'))
      .toEqual({ provinceCode: 'LA-SV', wardName: 'Kaysone Phomvihane', detail: 'Trai B' })
  })
})
