import { describe, expect, it } from 'vitest'
import { vi } from './locales/vi'
import { en } from './locales/en'

// Phí thủ tục kiểm dịch và hải quan đơn quốc tế do khách chịu và nằm trong báo giá: màn giấy tờ thông quan không được hứa "trọn gói" như miễn phí
describe('mô tả giấy tờ kiểm dịch và hải quan cho khách', () => {
  it('tiếng Việt nói nhà xe làm giấy tờ và phí nằm trong báo giá, không hứa trọn gói', () => {
    expect(vi.clearance.desc).toMatch(/báo giá/)
    expect(vi.clearance.desc).not.toMatch(/trọn gói/i)
  })
  it('tiếng Anh nói cùng ý, không dùng turnkey hay comprehensive', () => {
    expect(en.clearance.desc).toMatch(/quot/i)
    expect(en.clearance.desc).not.toMatch(/turnkey|comprehensive/i)
  })
  // Công ty không ký điện tử và không xuất hóa đơn qua phiếu báo giá
  it('nhãn phiếu báo giá không tự nhận là hóa đơn điện tử, cả hai ngôn ngữ', () => {
    expect(vi.orders.quote.docBadge).not.toMatch(/hóa đơn/i)
    expect(en.orders.quote.docBadge).not.toMatch(/invoice/i)
  })
})
