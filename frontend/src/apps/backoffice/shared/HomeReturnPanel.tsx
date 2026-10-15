// Driver và Escort đã giao ngựa ở nước ngoài: đang chạy về trụ sở (PRD mục 10.2).
// Driver xác nhận đã về, chỉ bấm được sau mốc tối thiểu và phải chụp ảnh trực tiếp tại trụ sở.
import { useState } from 'react'
import { useAuth } from '@shared/auth/AuthContext'
import { returnLate } from '@shared/lib/booking'
import { formatDateTime } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import type { Booking, VehicleTrip } from '@shared/types/booking'
import { CaptureField } from '@shared/ui/CaptureField'
import { useNow } from '@shared/ui/useNow'
import { Panel } from './field'
import s from './field.module.css'

type Run = (fn: () => Promise<unknown>, msg: string) => Promise<void>

export function HomeReturnPanel({ b, t, role, run }: { b: Booking; t: VehicleTrip; role: 'driver' | 'escort'; run: Run }) {
  const { session } = useAuth()
  const now = useNow(15_000)
  const [photo, setPhoto] = useState<string>()
  const h = t.homeReturn
  if (!h) return null
  if (h.backAt) return <Panel title="Đã về trụ sở" icon="fa-house-circle-check" tone="ok"><p>Về tới trụ sở lúc {formatDateTime(h.backAt)}. Bạn đã sẵn sàng nhận chuyến mới.</p></Panel>
  const late = returnLate(t, now)
  const wait = Math.max(0, h.earliestAt - now)
  const mins = Math.ceil(wait / 60_000)
  const left = mins >= 60 ? `${Math.floor(mins / 60)} giờ ${mins % 60} phút` : `${mins} phút`
  return (
    <Panel title="Đang về trụ sở" icon="fa-road" tone={late ? 'warn' : undefined}>
      <p>Đã giao ngựa ở <b>{h.fromPlace}</b>. Tài xế và hộ tống chạy về trụ sở, dự kiến tới lúc <b>{formatDateTime(h.eta)}</b>. Trong lúc về bạn chưa nhận được chuyến mới.</p>
      {late && <div className="alert alert-warning" style={{ marginBottom: 12 }}><i className="fa-solid fa-triangle-exclamation" /><div><b>Về trễ so với giờ dự kiến.</b> Điều phối viên và Quản lý đang thấy cờ này.</div></div>}
      {role === 'driver' ? (
        <>
          <CaptureField label="Ảnh chụp tại trụ sở" required value={photo} onChange={setPhoto} />
          <button className={`btn btn-primary ${s.big}`} disabled={!photo || wait > 0} onClick={() => run(() => bookingsApi.confirmHome(b.id, t.tripId, session!.name, photo!), 'Đã xác nhận về trụ sở, bạn sẵn sàng nhận chuyến mới')}><i className="fa-solid fa-house-circle-check" /> Đã về trụ sở</button>
          {wait > 0 && <p className={s.hint ?? ''} style={{ marginTop: 8, fontSize: '0.85rem' }}>Nút mở sau {formatDateTime(h.earliestAt)} (còn {left}).</p>}
        </>
      ) : <p>Tài xế sẽ xác nhận khi cả xe về tới trụ sở.</p>}
    </Panel>
  )
}
