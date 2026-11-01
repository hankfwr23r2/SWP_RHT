import type { CaptureEvidence } from '../types/capture'

// Metadata comes from the backend, never inferred from a filename or EXIF.
const evidence = new Map<string, CaptureEvidence>()
const listeners = new Set<() => void>()
export function registerCaptureEvidence(items: Record<string, CaptureEvidence> = {}) {
  let changed = false
  for (const [url, item] of Object.entries(items)) {
    if (JSON.stringify(evidence.get(url)) !== JSON.stringify(item)) { evidence.set(url, item); changed = true }
  }
  if (changed) listeners.forEach(fn => fn())
}
export const evidenceOf = (url?: string) => url ? evidence.get(url) : undefined
export const subscribeEvidence = (fn: () => void) => { listeners.add(fn); return () => { listeners.delete(fn) } }
export const captureTime = (url?: string) => evidenceOf(url)?.capturedAt
export const captureTimestamp = (at: number) => new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
}).format(new Date(at)) + ' GMT+7'

export function cameraError(error: unknown): string {
  const name = error !== null && typeof error === 'object' && 'name' in error ? String(error.name) : ''
  if (name === 'NotAllowedError' || name === 'SecurityError') return 'Chưa được cấp quyền camera. Cho phép camera trong trình duyệt và cài đặt thiết bị.'
  if (name === 'NotFoundError' || name === 'OverconstrainedError') return 'Không tìm thấy camera phù hợp. Kiểm tra webcam hoặc đổi camera.'
  if (name === 'NotReadableError' || name === 'AbortError') return 'Không mở được camera. Hãy đóng ứng dụng khác đang dùng camera và thử lại.'
  return 'Không mở được camera. Kiểm tra thiết bị và thử lại.'
}
export async function photograph(video: HTMLVideoElement, capturedAt: number): Promise<File> {
  if (video.readyState < 2 || !video.videoWidth || !video.videoHeight) throw new Error('Camera chưa sẵn sàng. Vui lòng đợi hình ảnh xuất hiện.')
  const ratio = Math.min(1, 1600 / Math.max(video.videoWidth, video.videoHeight))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(video.videoWidth * ratio); canvas.height = Math.round(video.videoHeight * ratio)
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Trình duyệt không hỗ trợ tạo ảnh.')
  ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
  const fontSize = Math.max(12, Math.round(canvas.width / 55))
  ctx.font = `600 ${fontSize}px sans-serif`
  const text = `Chụp: ${captureTimestamp(capturedAt)}`
  const height = fontSize * 2.4
  ctx.fillStyle = 'rgba(0,0,0,0.72)'; ctx.fillRect(0, canvas.height - height, canvas.width, height)
  ctx.fillStyle = '#fff'; ctx.textBaseline = 'middle'
  ctx.fillText(text, 12, canvas.height - height / 2, canvas.width - 24)
  const blob = await new Promise<Blob>((resolve, reject) => canvas.toBlob(b => b ? resolve(b) : reject(new Error('Không tạo được ảnh.')), 'image/jpeg', 0.85))
  if (blob.size > 5 * 1024 * 1024) throw new Error('Ảnh lớn hơn 5 MB. Vui lòng chụp lại.')
  return new File([blob], `camera_${capturedAt}.jpg`, { type: 'image/jpeg' })
}
