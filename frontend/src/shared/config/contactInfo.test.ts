import { describe, expect, it } from 'vitest'
import { HOTLINE, OFFICE_ADDRESS } from './business-rules'
import { applyRuntimeBusinessConfig } from './runtimeBusinessRules'

// Hotline và địa chỉ văn phòng lấy từ tham số hệ thống do admin cấu hình;
// chưa có thì giữ thông tin mẫu ban đầu để trang không trống.
const required = {
  DEPOSIT_RATE: '0.30', QUOTE_VALID_HOURS: '24', MAX_CONTINUOUS_HOURS: '4',
  MIN_REST_MINUTES: '30', VITAL_CHECK_INTERVAL_HOURS: '2', MIN_LEAD_DAYS: '20',
}

describe('contact information from system configs', () => {
  it('keeps the sample values until the admin sets real ones', () => {
    applyRuntimeBusinessConfig(required)

    expect(HOTLINE).toBe('1900 6868')
    expect(OFFICE_ADDRESS).toContain('Xô Viết Nghệ Tĩnh')
  })

  it('uses the hotline and the address the admin configured', () => {
    applyRuntimeBusinessConfig({ ...required, HOTLINE: '1900 7777', OFFICE_ADDRESS: '25 Nguyễn Huệ, Quận 1, TP.HCM' })

    expect(HOTLINE).toBe('1900 7777')
    expect(OFFICE_ADDRESS).toBe('25 Nguyễn Huệ, Quận 1, TP.HCM')
  })

  it('ignores blank values instead of showing an empty hotline', () => {
    applyRuntimeBusinessConfig({ ...required, HOTLINE: '1900 7777' })
    applyRuntimeBusinessConfig({ ...required, HOTLINE: '  ', OFFICE_ADDRESS: '' })

    expect(HOTLINE).toBe('1900 7777')
    expect(OFFICE_ADDRESS).toBe('25 Nguyễn Huệ, Quận 1, TP.HCM')
  })
})
