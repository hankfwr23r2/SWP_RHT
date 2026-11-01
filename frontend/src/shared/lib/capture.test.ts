import { describe, it, expect } from 'vitest'
import { cameraError, captureTimestamp, captureTime, evidenceOf, registerCaptureEvidence } from './capture'
import type { CaptureEvidence } from '../types/capture'

describe('thời gian chứng từ camera', () => {
  it('không suy diễn thời gian thật từ tên ảnh cũ hoặc URL', () => {
    expect(captureTime('capture_1790000000000.jpg')).toBeUndefined()
    expect(captureTime('https://example.com/capture_1790000000000.jpg')).toBeUndefined()
  })
  it('giữ riêng giờ thiết bị và giờ backend, khôi phục từ metadata sau khi tải đơn', () => {
    const item: CaptureEvidence = { id: 1, url: 'https://example.com/camera-1.jpg', bookingId: 1, tripId: 'T1', purpose: 'ARRIVE_PICKUP', target: 'pickup', capturedBy: 'driver', capturedAt: 1793520000000, recordedAt: 1793520060000 }
    registerCaptureEvidence({ [item.url]: item })
    expect(captureTime(item.url)).toBe(item.capturedAt)
    expect(evidenceOf(item.url)?.recordedAt).toBe(item.recordedAt)
  })
  it('định dạng đúng múi giờ Việt Nam dù máy demo dùng múi giờ khác', () => {
    const text = captureTimestamp(Date.parse('2026-11-01T00:05:09Z'))
    expect(text).toContain('07:05:09')
    expect(text).toContain('01/11/2026')
    expect(text).toContain('GMT+7')
  })
  it('hướng dẫn xử lý lỗi quyền, thiếu camera và camera đang bận', () => {
    const error = (name: string) => { const e = new Error(); e.name = name; return e }
    expect(cameraError(error('NotAllowedError'))).toContain('cấp quyền')
    expect(cameraError(error('NotFoundError'))).toContain('Không tìm thấy')
    expect(cameraError(error('NotReadableError'))).toContain('ứng dụng khác')
  })
})
