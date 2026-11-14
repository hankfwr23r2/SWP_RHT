import { describe, expect, it } from 'vitest'
import type { ServiceCatalogItem } from '@shared/api/serviceCatalog'
import { defaultConfig, isDeclinedInsurance, SERVICE_TIERS, serviceOptionFor, tierOf, tierOptions, tierSelection } from './draft'

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

// Hạng dịch vụ: mỗi hạng là một cặp dịch vụ khoang + thức ăn có sẵn trong danh mục
describe('hạng dịch vụ', () => {
  const catalog = [
    service(1, 'STALL_STANDARD', 'Khoang tiêu chuẩn', 1, 1_000_000),
    service(2, 'STALL_SINGLE_EXTENDED', 'Khoang đơn mở rộng', 1, 2_000_000),
    service(3, 'FEED_BASIC', 'Gói cơ bản', 2),
    service(4, 'FEED_ADVANCED', 'Gói nâng cao', 2, 300_000),
    service(5, 'FEED_SPORT', 'Gói thể thao', 2, 500_000),
  ]
  const [standard, premium, first] = SERVICE_TIERS

  it('đủ dịch vụ trong danh mục thì bán cả 3 hạng, giá lấy từ danh mục', () => {
    const options = tierOptions(catalog)
    expect(options.map(option => option.tier.id)).toEqual(['standard', 'premium', 'first'])
    expect(options[2].stall.price).toBe(2_000_000)
    expect(options[2].feed.price).toBe(500_000)
  })

  it('hạng thiếu dịch vụ khoang hoặc thức ăn (đã ngừng áp dụng) thì không bán', () => {
    const withoutSport = catalog.filter(item => item.serviceCode !== 'FEED_SPORT')
    expect(tierOptions(withoutSport).map(option => option.tier.id)).toEqual(['standard', 'premium'])
    expect(tierOptions([])).toEqual([])
  })

  it('chọn hạng thì gán cả mã khoang, mã thức ăn và các trường cũ', () => {
    const base = { ...defaultConfig(), selectedServiceCodes: { insurance: 'INSURANCE_BUY' } }
    const config = { ...base, ...tierSelection(base, first) }
    expect(config.selectedServiceCodes).toEqual({ insurance: 'INSURANCE_BUY', stall: 'STALL_SINGLE_EXTENDED', feed: 'FEED_SPORT' })
    expect(config.stall).toBe('single') // khoang mở rộng chiếm 2 ngăn khi xếp xe
    expect(config.feedPackage).toBe('sport')
    expect(tierOf(config)).toBe('first')

    const downgraded = { ...config, ...tierSelection(config, standard) }
    expect(downgraded.stall).toBe('standard')
    expect(downgraded.feedPackage).toBe('basic')
    expect(tierOf(downgraded)).toBe('standard')
    expect(tierOf({ ...config, ...tierSelection(config, premium) })).toBe('premium')
  })

  it('chưa chọn, hoặc cặp dịch vụ không khớp hạng nào, thì coi như chưa chọn hạng', () => {
    expect(tierOf(defaultConfig())).toBeUndefined()
    expect(tierOf({ selectedServiceCodes: { stall: 'STALL_SINGLE_EXTENDED', feed: 'FEED_BASIC' } })).toBeUndefined()
    expect(tierOf({ selectedServiceCodes: { stall: 'STALL_STANDARD' } })).toBeUndefined()
  })
})
