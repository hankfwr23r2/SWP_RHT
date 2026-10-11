// Bước 4: xác nhận và gửi đơn. Đơn gửi đi ở trạng thái "Chờ Manager tiếp nhận".
import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { VEHICLE_CLASS } from '@shared/config/booking-rules'
import { COUNTRIES } from '@shared/config/network'
import { classForHorses, findLocation, fromIsoDay, insuranceFee } from '@shared/lib/booking'
import { formatDate, formatVND } from '@shared/lib/format'
import { customerBookingsApi } from '@shared/services/bookings'
import { horsesApi } from '@shared/services/horses'
import { useLoad } from '@shared/services/useLoad'
import { SEX_LABEL, type PlaceRef } from '@shared/types/booking'
import { Modal } from '@shared/ui/Modal'
import { ReadMore } from '@shared/ui/ReadMore'
import { useToast } from '@shared/ui/toast'
import { BookingShell } from './BookingShell'
import { countriesOf, useBookingDraft } from './draft'
import s from './Booking.module.css'


export default function Step4ReviewPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const { session } = useAuth()
  const owner = session!.name
  const { draft, clear } = useBookingDraft()
  const { data: horses } = useLoad(() => horsesApi.list(owner), [owner])
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState(false) // đã gửi: bản nháp bị xóa, không chuyển ngược về bước 1

  const [placed, setPlaced] = useState<{ id: string; route: string; date: string; horses: number } | null>(null)

  // Đã gửi: hiện popup xác nhận, khách chọn xem đơn vừa đặt hoặc chi tiết đơn
  if (sent) {
    if (!placed) return null
    const toList = () => navigate(`/orders?group=confirm&new=${placed.id}`)
    return (
      <Modal onClose={toList} title={<><i className="fa-solid fa-circle-check" style={{ color: 'var(--green)' }} /> Đã gửi đơn {placed.id}</>} subtitle="Đơn của bạn đã được ghi nhận"
        footer={<><button className="btn btn-ghost" onClick={() => navigate(`/orders/${placed.id}`)}>Xem chi tiết đơn</button><button className="btn btn-primary" onClick={toList}>Xem đơn vừa đặt</button></>}>
        <dl className={s.reviewGrid}>
          <div><dt>Tuyến</dt><dd>{placed.route}</dd></div>
          <div><dt>Ngày khởi hành</dt><dd>{placed.date}</dd></div>
          <div><dt>Số ngựa</dt><dd>{placed.horses} con</dd></div>
        </dl>
        <p className="form-hint" style={{ marginTop: 14 }}>Đơn nằm ở <b>Đơn hàng của tôi › Chờ xác nhận</b>, đang ở <b>bước 1/8: Gửi đơn</b>, chờ quản lý tiếp nhận. Quản lý sẽ tiếp nhận, Kiểm dịch viên và Điều phối viên thẩm định, rồi gửi báo giá cho bạn. Bạn theo dõi từng bước ở đó.</p>
      </Modal>
    )
  }
  const countries = countriesOf(draft)
  if (!countries || !draft.departDate) return <Navigate to="/booking/route" replace />
  if (!draft.horseIds.length) return <Navigate to="/booking/horses" replace />
  if (draft.horseIds.some(id => !draft.config[id]?.insurance)) return <Navigate to="/booking/services" replace />

  const international = draft.type === 'international'
  const rows = draft.horseIds.map(id => horses?.find(h => h.id === id)).filter(Boolean) as NonNullable<typeof horses>
  const place = (id: string, country: PlaceRef['country']): PlaceRef => ({ id, name: findLocation(id)?.name ?? id, country })
  const origin = place(draft.originId, countries.origin)
  const dest = place(draft.destId, countries.dest)
  const cls = VEHICLE_CLASS[classForHorses(rows.length)]

  const submit = async () => {
    if (busy) return
    setBusy(true)
    try {
      const order = await customerBookingsApi.create(owner, {
        type: draft.type as 'domestic' | 'international', origin, dest,
        departAt: fromIsoDay(draft.departDate), consignor: draft.consignor, consignee: draft.consignee,
        horses: rows.map(h => {
          const c = draft.config[h.id]
          return { horseId: h.id, name: h.name, microchip: h.microchip, breed: h.breed, sex: h.sex, stall: c.stall, feedPackage: c.feedPackage, waterPlan: c.waterPlan, insurance: { opted: c.insurance === 'buy' } }
        }),
      })
      setPlaced({ id: order.id, route: `${origin.name.split(' — ')[0]} → ${dest.name.split(' — ')[0]}`, date: formatDate(fromIsoDay(draft.departDate)), horses: rows.length })
      setSent(true)
      clear()
      toast(`Đã gửi đơn ${order.id}`)
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Không gửi được đơn.', 'error')
      setBusy(false)
    }
  }

  return (
    <BookingShell step={4} title="Xác nhận và gửi đơn" subtitle="Kiểm tra lại thông tin. Sau khi gửi, Quản lý sẽ tiếp nhận và bắt đầu thẩm định.">
      <div className={`card ${s.review}`} data-card>
        <div className="card-header"><h3><i className="fa-solid fa-route" /> Chuyến đi</h3><Link to="/booking/route" className={s.editLink}>Sửa</Link></div>
        <dl className={s.reviewGrid}>
          <div><dt>Loại chuyến</dt><dd>{international ? `Quốc tế (${COUNTRIES[countries.origin].name} → ${COUNTRIES[countries.dest].name})` : 'Trong nước'}</dd></div>
          <div><dt>Ngày khởi hành</dt><dd>{formatDate(fromIsoDay(draft.departDate))}</dd></div>
          <div><dt>Điểm đón</dt><dd>{origin.name}</dd></div>
          <div><dt>Điểm giao</dt><dd>{dest.name}</dd></div>
          <div><dt>Người gửi</dt><dd>{draft.consignor.name}<br /><small className="text-muted">{draft.consignor.phone}</small></dd></div>
          <div><dt>Người nhận</dt><dd>{draft.consignee.name}<br /><small className="text-muted">{draft.consignee.phone}</small></dd></div>
        </dl>
      </div>

      <div className="card" data-card>
        <div className="card-header"><h3><i className="fa-solid fa-horse-head" /> {rows.length} ngựa · xe {cls.label} ({cls.stalls})</h3><Link to="/booking/services" className={s.editLink}>Sửa</Link></div>
        {rows.map(h => {
          const c = draft.config[h.id]
          return (
            <div key={h.id} className={s.reviewHorse}>
              <div><b>{h.name}</b> <small>Chip {h.microchip} · {h.breed} · {SEX_LABEL[h.sex]}</small></div>
              <div className="text-right">{c.stall === 'single' ? 'Khoang đơn' : 'Khoang tiêu chuẩn'}</div>
              <small>{c.insurance === 'buy' ? 'Mua bảo hiểm chuyến đi' : 'Từ chối bảo hiểm (trách nhiệm hạn chế)'}</small>
              <small className="text-right">{c.insurance === 'buy' ? `Phí ${formatVND(insuranceFee(h.breed))}` : ''}</small>
            </div>
          )
        })}
      </div>

      <div className="alert alert-info" data-card>
        <i className="fa-solid fa-circle-info" />
        <ReadMore text={'Bạn không cần làm giấy kiểm dịch hay thủ tục hải quan. Nhà xe sẽ làm trọn gói sau khi bạn đặt cọc và báo tiến độ cho bạn. Bạn chỉ cần giao bản gốc hồ sơ ngựa cho tài xế khi nhận ngựa.'} />
      </div>

      <div className={s.actions}>
        <Link to="/booking/services" className="btn btn-ghost"><i className="fa-solid fa-arrow-left" /> Dịch vụ và bảo hiểm</Link>
        <button type="button" className="btn btn-primary btn-lg" onClick={submit} disabled={busy}><i className="fa-solid fa-paper-plane" /> {busy ? 'Đang gửi…' : 'Gửi yêu cầu đặt đơn'}</button>
      </div>
    </BookingShell>
  )
}
