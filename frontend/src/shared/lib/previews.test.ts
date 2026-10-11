// Kho xem trước ảnh trong phiên: hệ thống chưa có kho tệp nên chỉ nhớ ảnh vừa chọn theo tên tệp.
import { describe, expect, it } from 'vitest'
import { previewOf, rememberPreview } from './previews'

describe('xem trước ảnh', () => {
  it('nhớ ảnh đã chọn theo tên tệp, lấy lại được', () => {
    rememberPreview('giay_kiem_dich.jpg', new Blob(['x'], { type: 'image/jpeg' }))
    expect(previewOf('giay_kiem_dich.jpg')).toMatch(/^blob:/)
  })
  it('tệp không phải ảnh (PDF) hoặc tên chưa từng chọn thì không có xem trước', () => {
    rememberPreview('ho_so.pdf', new Blob(['x'], { type: 'application/pdf' }))
    expect(previewOf('ho_so.pdf')).toBeUndefined()
    expect(previewOf('khong_co.jpg')).toBeUndefined()
    expect(previewOf(undefined)).toBeUndefined()
  })
  it('chọn lại cùng tên thì lấy ảnh mới nhất', () => {
    rememberPreview('a.png', new Blob(['1'], { type: 'image/png' }))
    const first = previewOf('a.png')
    rememberPreview('a.png', new Blob(['2'], { type: 'image/png' }))
    expect(previewOf('a.png')).not.toBe(first)
  })
})
