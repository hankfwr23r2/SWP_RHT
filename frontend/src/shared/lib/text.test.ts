import { describe, expect, it } from 'vitest'
import { splitLead } from './text'

describe('splitLead: câu đầu và phần còn lại', () => {
  it('tách ở dấu chấm hoặc chấm phẩy đầu tiên', () => {
    expect(splitLead('Đơn đã đặt cọc. Nhà xe làm giấy tờ; bạn theo dõi.')).toEqual({ lead: 'Đơn đã đặt cọc.', rest: 'Nhà xe làm giấy tờ; bạn theo dõi.' })
    expect(splitLead('Một ý; ý hai')).toEqual({ lead: 'Một ý;', rest: 'ý hai' })
  })
  it('đoạn một câu thì không có phần cất', () => {
    expect(splitLead('Chỉ một câu ngắn.')).toEqual({ lead: 'Chỉ một câu ngắn.', rest: '' })
    expect(splitLead('Không có dấu ngắt')).toEqual({ lead: 'Không có dấu ngắt', rest: '' })
  })
  it('ghép lại lead + rest đủ chữ, không mất ký tự', () => {
    const t = 'Câu một. Câu hai. Câu ba.'
    const { lead, rest } = splitLead(t)
    expect(`${lead} ${rest}`).toBe(t)
  })
})
