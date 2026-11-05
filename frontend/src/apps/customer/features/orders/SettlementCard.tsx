// Diễn biến sự cố và bảng quyết toán cuối chuyến của khách (Flow 5–6).
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { INCIDENT_KIND } from '@shared/config/booking-rules'
import { formatDateTime, formatVND } from '@shared/lib/format'
import { customerBookingsApi, type CustomerBookingView } from '@shared/services/bookings'
import { ImageThumb } from '@shared/ui/ImageThumb'
import { useToast } from '@shared/ui/toast'
import s from './OrderDetail.module.css'
import { FormSelect } from '@shared/ui/FormSelect'
import { PolicyLink } from '@shared/ui/PolicyLink'

// Diễn biến sự cố của xe (PRD mục 6.7): khách thấy nhóm, bước xử lý và ETA mới
export function IncidentsCard({ b }: { b: CustomerBookingView }) {
  const { t, i18n } = useTranslation()
  if (!b.incidents?.length) return null
  const step = (i: NonNullable<CustomerBookingView['incidents']>[number]) => i.status === 'resolved'
    ? (i18n.language === 'en' ? `Resolved at ${formatDateTime(i.resolvedAt!)}, transporter resumes route${i.newEta ? `, updated ETA ${formatDateTime(i.newEta)}` : ''}` : `Đã xử lý xong lúc ${formatDateTime(i.resolvedAt!)}, xe tiếp tục hành trình${i.newEta ? `, dự kiến đến ${formatDateTime(i.newEta)}` : ''}`)
    : i.status === 'active'
      ? (i18n.language === 'en' ? `Active emergency plan in progress${i.newEta ? `, updated ETA ${formatDateTime(i.newEta)}` : ''}` : `Đang xử lý theo phương án đã duyệt${i.newEta ? `, thời gian dự kiến mới ${formatDateTime(i.newEta)}` : ''}`)
      : (i18n.language === 'en' ? 'Management is formulating a plan. The manager will call you.' : 'Nhà xe đang lập và duyệt phương án, Quản lý sẽ gọi điện cho bạn')
  return (
    <div className="card">
      <div className="card-header"><h3><i className="fa-solid fa-triangle-exclamation" /> {t('settlement.incidentsTitle')}</h3></div>
      {b.incidents.map(i => <p key={i.id} style={{ margin: '8px 0' }}><b>{INCIDENT_KIND[i.kind].label}</b> · {i18n.language === 'en' ? 'reported at ' : 'báo lúc '}{formatDateTime(i.reportedAt)}<br /><span className="text-muted">{step(i)}. {t('settlement.escortCareNote')}</span></p>)}
    </div>
  )
}

// Bảng quyết toán sau chuyến: chỉ các khoản khách chịu (kèm ảnh chứng từ), thanh toán và chấm điểm (Flow 6)
export function SettlementCard({ b, owner, onDone }: { b: CustomerBookingView; owner: string; onDone: () => void }) {
  const { t, i18n } = useTranslation()
  const toast = useToast()
  const [busy, setBusy] = useState(false)
  const [stars, setStars] = useState(5)
  const [comment, setComment] = useState('')
  const st = b.settlement
  if (!st) return null
  const hasExpenses = st.items.length > 0
  const open = b.status === 'settlement_issued' || b.status === 'payment_overdue'
  const submit = async () => {
    setBusy(true)
    const rating = { stars, comment, trip: stars, driver: stars, escort: stars }
    try {
      // Có khoản phải trả: sang cổng VNPAY, đơn chỉ đóng khi VNPAY xác nhận đã nhận tiền
      if (st.total > 0) {
        window.location.assign(await customerBookingsApi.settlementVnpayUrl(b.id, rating))
        return
      }
      await customerBookingsApi.settle(owner, b.id, rating)
      toast(i18n.language === 'en' ? 'Rating submitted. Order completed' : 'Đã gửi đánh giá. Đơn hoàn tất')
      onDone()
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Không thực hiện được', 'error')
      setBusy(false)
    }
  }
  return (
    <div className="card">
      {hasExpenses && (
        <>
          <div className="card-header"><h3><i className="fa-solid fa-file-invoice-dollar" /> {t('settlement.invoiceTitle')}</h3>{b.status === 'payment_overdue' && <span className="badge badge-danger">{t('settlement.overdue')}</span>}{b.status === 'completed' && <span className="badge badge-success">{t('settlement.completed')}</span>}</div>
          <p className="form-hint">{i18n.language === 'en' ? <>Transport freight was fixed upon quote acceptance. Fuel and toll charges are fully inclusive. Below are horse-specific incidental expenses per the <PolicyLink doc="incident_cost">Incident Cost Policy</PolicyLink>.</> : <>Giá chuyến đã cố định từ lúc báo giá, nhiên liệu và cầu đường không tính thêm. Dưới đây là các khoản phát sinh do ngựa mà bạn chịu theo <PolicyLink doc="incident_cost">chính sách chi phí sự cố</PolicyLink>.</>}</p>
          <div className={s.bank}>{st.items.map((it, i) => <div key={i}><span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>{it.photo && <ImageThumb name={it.photo} size={32} />}{it.label}</span><b>{formatVND(it.amount)}</b></div>)}<div><span>{t('settlement.totalPayable')}</span><b>{formatVND(st.total)}</b></div></div>
        </>
      )}
      {open && st.total > 0 && <p className="form-hint" style={{ marginTop: 8 }}>{t('settlement.dueNotice', { date: formatDateTime(st.dueAt) })}</p>}
      {open && (
        <>
          <h4 style={{ margin: '16px 0 8px' }}>{t('settlement.rateExperience')}</h4>
          <div className="form-group">
            <label htmlFor="rt-stars">{i18n.language === 'en' ? 'Satisfaction level' : 'Mức độ hài lòng'}</label>
            <FormSelect id="rt-stars" className="form-control" value={stars} onChange={e => setStars(Number(e.target.value))}>
              {[5, 4, 3, 2, 1].map(n => <option key={n} value={n}>{n} {i18n.language === 'en' ? 'stars' : 'sao'} {n === 5 ? (i18n.language === 'en' ? '(Excellent)' : '(Rất hài lòng)') : n === 4 ? (i18n.language === 'en' ? '(Good)' : '(Hài lòng)') : n === 3 ? (i18n.language === 'en' ? '(Average)' : '(Bình thường)') : n === 2 ? (i18n.language === 'en' ? '(Below Average)' : '(Chưa hài lòng)') : (i18n.language === 'en' ? '(Poor)' : '(Rất không hài lòng)')}</option>)}
            </FormSelect>
          </div>
          <div className="form-group"><label htmlFor="rt-note">{i18n.language === 'en' ? 'Feedback / Notes (optional)' : 'Nhận xét (không bắt buộc)'}</label><input id="rt-note" className="form-control" placeholder={i18n.language === 'en' ? 'Share your feedback or experience…' : 'Góp ý hoặc chia sẻ trải nghiệm của bạn…'} value={comment} onChange={e => setComment(e.target.value)} /></div>
          <button className="btn btn-primary btn-lg btn-full" disabled={busy} onClick={submit}>{busy ? (st.total ? (i18n.language === 'en' ? 'Redirecting to VNPAY…' : 'Đang chuyển sang VNPAY…') : (i18n.language === 'en' ? 'Processing…' : 'Đang xử lý…')) : st.total ? (i18n.language === 'en' ? `Pay ${formatVND(st.total)} via VNPAY & Submit Rating` : `Thanh toán ${formatVND(st.total)} qua VNPAY và gửi đánh giá`) : t('settlement.sendRatingAndComplete')}</button>
          {st.total > 0 && <p className="form-hint" style={{ textAlign: 'center' }}>{i18n.language === 'en' ? 'You will be redirected to the VNPAY gateway. Your rating will be recorded upon payment confirmation.' : 'Bạn sẽ được chuyển sang cổng VNPAY. Đánh giá được ghi nhận khi thanh toán thành công.'}</p>}
        </>
      )}
      {b.rating && <p style={{ marginTop: 12 }}>{i18n.language === 'en' ? 'Your rating: ' : 'Bạn đã chấm: '}{b.rating.stars ?? b.rating.trip}/5 ★{b.rating.comment ? `. "${b.rating.comment}"` : ''}</p>}
    </div>
  )
}
