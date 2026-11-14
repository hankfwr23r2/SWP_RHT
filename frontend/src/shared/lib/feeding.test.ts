import { describe, expect, it } from 'vitest'
import { cleanFeeding, feedingLines } from './feeding'

describe('thông tin cho ăn của ngựa', () => {
  it('bỏ khoảng trắng thừa và giữ phần khách đã khai', () => {
    expect(cleanFeeding({ usualTimes: ' 07:00, 18:00 ', ration: ' 8 kg cỏ/ngày ', ownFeed: true }))
      .toEqual({ usualTimes: '07:00, 18:00', ration: '8 kg cỏ/ngày', ownFeed: true })
  })

  it('không khai gì thì không gửi lên', () => {
    expect(cleanFeeding(undefined)).toBeUndefined()
    expect(cleanFeeding({ usualTimes: '  ', ration: '', ownFeed: false })).toBeUndefined()
  })

  it('chỉ tick gửi kèm thức ăn vẫn là một khai báo', () => {
    expect(cleanFeeding({ ownFeed: true })).toEqual({ ownFeed: true })
  })

  it('tạo các dòng hiển thị cho nhân viên, bỏ qua phần trống', () => {
    expect(feedingLines({ usualTimes: '07:00, 18:00', ownFeed: true })).toEqual([
      ['Giờ ăn quen', '07:00, 18:00'],
      ['Thức ăn', 'Khách gửi kèm thức ăn riêng'],
    ])
    expect(feedingLines({ ration: 'Kiêng đậu' })).toEqual([['Khẩu phần, ghi chú', 'Kiêng đậu']])
    expect(feedingLines(undefined)).toEqual([])
  })
})
