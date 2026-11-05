import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { cameraError, captureTimestamp, photograph } from '../lib/capture'
import { Modal } from './Modal'
import s from './CaptureField.module.css'

interface Props { label: string; onClose: () => void; onUse: (file: File, capturedAt: number) => Promise<void> }
export function CameraCapture({ label, onClose, onUse }: Props) {
  const video = useRef<HTMLVideoElement>(null)
  const stream = useRef<MediaStream | null>(null)
  const [facing, setFacing] = useState<'environment' | 'user'>('environment')
  const [attempt, setAttempt] = useState(0)
  const [ready, setReady] = useState(false)
  const [error, setError] = useState('')
  const [shot, setShot] = useState<{ file: File; at: number; url: string }>()
  const [saving, setSaving] = useState(false)
  const [shooting, setShooting] = useState(false)
  const stop = () => { stream.current?.getTracks().forEach(track => track.stop()); stream.current = null }

  useEffect(() => {
    if (shot) return
    let cancelled = false
    let opened: MediaStream | undefined
    const start = async () => {
      if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
        setError('Camera cần HTTPS hoặc localhost. Truy cập HTTP qua IP mạng LAN không mở được camera trực tiếp.'); return
      }
      try {
        opened = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: facing }, width: { ideal: 1600 }, height: { ideal: 1200 } }, audio: false })
        if (cancelled) { opened.getTracks().forEach(track => track.stop()); return }
        stream.current = opened
        opened.getVideoTracks().forEach(track => { track.onended = () => {
          if (!cancelled) { setReady(false); setError('Camera đã ngắt kết nối. Vui lòng kiểm tra thiết bị và mở lại.') }
        } })
        if (video.current) { video.current.srcObject = opened; await video.current.play() }
      } catch (err) { opened?.getTracks().forEach(track => track.stop()); if (!cancelled) setError(cameraError(err)) }
    }
    void start()
    return () => { cancelled = true; opened?.getTracks().forEach(track => track.stop()); if (stream.current === opened) stream.current = null }
  }, [facing, attempt, shot])
  useEffect(() => () => { if (shot) URL.revokeObjectURL(shot.url) }, [shot])
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape' && !saving && !shooting) { e.stopImmediatePropagation(); onClose() } }
    document.addEventListener('keydown', onKey, true)
    return () => document.removeEventListener('keydown', onKey, true)
  }, [onClose, saving, shooting])
  const retake = () => { setReady(false); setError(''); setShot(undefined); setAttempt(n => n + 1) }
  const take = async () => {
    if (!video.current || shooting) return
    setShooting(true); setError('')
    try {
      const at = Date.now()
      const file = await photograph(video.current, at)
      setShot({ file, at, url: URL.createObjectURL(file) }); stop(); setReady(false)
    } catch (err) { setError(err instanceof Error ? err.message : 'Không chụp được ảnh.') }
    finally { setShooting(false) }
  }
  return createPortal(<Modal title={label} subtitle="Chụp trực tiếp từ camera" onClose={() => { if (!saving && !shooting) onClose() }} wide
    footer={<div className={s.cameraActions}>
      {shot ? <><button type="button" className="btn btn-outline" disabled={saving} onClick={retake}>Chụp lại</button><button type="button" className="btn btn-primary" disabled={saving} onClick={async () => {
        setSaving(true); setError('')
        try { await onUse(shot.file, shot.at); onClose() }
        catch (err) { setError(err instanceof Error ? err.message : 'Upload thất bại. Thử lại bằng ảnh vừa chụp.') }
        finally { setSaving(false) }
      }}>{saving ? 'Đang lưu ảnh…' : 'Dùng ảnh này'}</button></> : <>
        <button type="button" className="btn btn-outline" disabled={shooting} onClick={() => { stop(); setReady(false); setError(''); setFacing(f => f === 'environment' ? 'user' : 'environment') }}>Đổi camera</button>
        <button type="button" className="btn btn-primary" disabled={!ready || shooting} onClick={take}>{shooting ? 'Đang chụp…' : 'Chụp'}</button>
        {error && <button type="button" className="btn btn-outline" onClick={() => { setError(''); setReady(false); setAttempt(n => n + 1) }}>Thử mở lại camera</button>}
      </>}
    </div>}>
    {shot ? <><img className={s.cameraPreview} src={shot.url} alt="Ảnh vừa chụp" /><p>Chụp theo thiết bị: {captureTimestamp(shot.at)}</p></> : <video className={s.cameraPreview} ref={video} autoPlay muted playsInline onLoadedData={() => setReady(true)} onError={() => { setReady(false); setError('Không phát được hình ảnh camera. Vui lòng thử lại.') }} />}
    {!shot && !ready && !error && <p role="status">Đang mở camera, vui lòng cho phép truy cập…</p>}
    {error && <p className="form-error" role="alert">{error}</p>}
    <p className="form-hint">Thời gian chụp lấy từ đồng hồ thiết bị. Thời gian hệ thống ghi nhận được lưu độc lập sau khi upload thành công.</p>
  </Modal>, document.body)
}
