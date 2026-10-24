import { describe, expect, it } from 'vitest'
import type { ServiceCatalogItem } from '@shared/api/serviceCatalog'
import { defaultConfig, isDeclinedInsurance, serviceOptionFor } from './draft'

const service = (id: number, serviceName: string, categoryId: number, price = 0): ServiceCatalogItem => ({
  id,
  serviceName,
  categoryId,
  categoryName: `Category ${categoryId}`,
  price,
  status: 'ACTIVE',
})

describe('booking service catalog selection', () => {
  const catalog = [
    service(1, 'Khoang tiêu chuẩn', 1),
    service(2, 'Khoang đơn mở rộng', 1, 2_000_000),
    service(4, 'Gói nâng cao', 2, 300_000),
    service(10, 'Từ chối', 4),
  ]

  it('cho phép dịch vụ đã chọn hiện tại và dùng lựa chọn tương thích với bản nháp cũ', () => {
    const config = { ...defaultConfig(), selectedServices: { stall: 2 } }
    expect(serviceOptionFor(catalog, config, 'stall')?.id).toBe(2)
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
})
