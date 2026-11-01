import { useSyncExternalStore } from 'react'
import { captureTimestamp, evidenceOf, subscribeEvidence } from '../lib/capture'
export function CaptureEvidenceInfo({ url }: { url: string }) {
  const data = useSyncExternalStore(subscribeEvidence, () => evidenceOf(url), () => undefined)
  if (!data) return <small>Chưa có thông tin thời điểm chụp.</small>
  return <div style={{ fontSize: '0.82rem', lineHeight: 1.6 }}>
    <div>Chụp theo thiết bị: <time dateTime={new Date(data.capturedAt).toISOString()}>{captureTimestamp(data.capturedAt)}</time></div>
    <div>Hệ thống ghi nhận: <time dateTime={new Date(data.recordedAt).toISOString()}>{captureTimestamp(data.recordedAt)}</time></div>
    <div>Người chụp: {data.capturedBy}</div>
  </div>
}
