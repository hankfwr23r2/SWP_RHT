import { describe, expect, it } from 'vitest'
import type { ServiceCatalogItem } from '@shared/api/serviceCatalog'
import { defaultConfig, isDeclinedInsurance, serviceOptionFor } from './draft'

const service = (
  id: number,
  serviceCode: string,
  serviceName: string,
  categoryId: number,
  price = 0,
): ServiceCatalogItem => ({
  id,
  serviceCode,
  serviceName,
  categoryId,
  categoryName: `Category ${categoryId}`,
  price,
  billingUnit: 'PER_HORSE',
  status: 'ACTIVE',
})

describe('booking service catalog selection', () => {
  const catalog = [
    service(1, 'STALL_STANDARD', 'Khoang tiêu chuẩn', 1),
    service(2, 'STALL_SINGLE_EXTENDED', 'Khoang đơn mở rộng', 1, 2_000_000),
    service(4, 'FEED_ADVANCED', 'Gói nâng cao', 2, 300_000),
    service(10, 'INSURANCE_DECLINED', 'Từ chối', 4),
  ]

  it('dùng mã dịch vụ ổn định và vẫn đọc lựa chọn ID trong bản nháp cũ', () => {
    const config = { ...defaultConfig(), selectedServiceCodes: { stall: 'STALL_SINGLE_EXTENDED' } }
    expect(serviceOptionFor(catalog, config, 'stall')?.id).toBe(2)
    expect(serviceOptionFor(catalog, { ...defaultConfig(), selectedServices: { stall: 2 } }, 'stall')?.id).toBe(2)
    expect(serviceOptionFor(catalog, { ...defaultConfig(), feedPackage: 'advanced' }, 'feed')?.id).toBe(4)
  })

  it('không tự thay dịch vụ đã bị ẩn do ngừng áp dụng', () => {
    const config = { ...defaultConfig(), selectedServices: { stall: 2 } }
    expect(serviceOptionFor([catalog[0]], config, 'stall')).toBeUndefined()
  })

  it('xác định lựa chọn từ chối bảo hiểm để buộc xác nhận điều khoản', () => {
    expect(isDeclinedInsurance(catalog[3])).toBe(true)
    expect(isDeclinedInsurance(catalog[1])).toBe(false)
  })

  it('không trả dịch vụ nước đã ngừng cung cấp', () => {
    expect(serviceOptionFor([
      service(7, 'WATER_EVERY_2H', 'Mỗi 2 giờ', 3, 50_000),
    ], defaultConfig(), 'water')).toBeUndefined()
  })
})
