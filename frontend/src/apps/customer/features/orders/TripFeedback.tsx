// Diễn biến hành trình và đánh giá của khách; chi phí sự cố thuộc nội bộ công ty.
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { INCIDENT_KIND } from '@shared/config/booking-rules'
import { formatDateTime } from '@shared/lib/format'
import { customerBookingsApi, type CustomerBookingView } from '@shared/services/bookings'
import { useToast } from '@shared/ui/toast'
import { FormSelect } from '@shared/ui/FormSelect'

// Diễn biến sự cố của xe (PRD mục 6.7): khách thấy nhóm, bước xử lý và ETA mới
export function IncidentsCard({ incidents }: { incidents: NonNullable<CustomerBookingView['incidents']> }) {
  const { t, i18n } = useTranslation()
  if (!incidents.length) return null
  const step = (i: NonNullable<CustomerBookingView['incidents']>[number]) => i.status === 'resolved'
    ? (i18n.language === 'en' ? `Resolved at ${formatDateTime(i.resolvedAt!)}, transporter resumes route${i.newEta ? `, updated ETA ${formatDateTime(i.newEta)}` : ''}` : `Đã xử lý xong lúc ${formatDateTime(i.resolvedAt!)}, xe tiếp tục hành trình${i.newEta ? `, dự kiến đến ${formatDateTime(i.newEta)}` : ''}`)
    : i.status === 'active'
      ? (i18n.language === 'en' ? `Active emergency plan in progress${i.newEta ? `, updated ETA ${formatDateTime(i.newEta)}` : ''}` : `Đang xử lý theo phương án đã duyệt${i.newEta ? `, thời gian dự kiến mới ${formatDateTime(i.newEta)}` : ''}`)
      : (i18n.language === 'en' ? 'Management is formulating a plan. The manager will call you.' : 'Nhà xe đang lập và duyệt phương án, Quản lý sẽ gọi điện cho bạn')
  return (
    <div className="card">
      <div className="card-header"><h3><i className="fa-solid fa-triangle-exclamation" /> {t('settlement.incidentsTitle')}</h3></div>
      {incidents.map(i => <p key={i.id} style={{ margin: '8px 0' }}><b>{INCIDENT_KIND[i.kind].label}</b> · {i18n.language === 'en' ? 'reported at ' : 'báo lúc '}{formatDateTime(i.reportedAt)}<br /><span className="text-muted">{step(i)}. {t('settlement.escortCareNote')}</span></p>)}
    </div>
  )
}

// Feedback is independent of all company expenses and payments.
export function RatingCard({ b, owner, onDone }: { b: CustomerBookingView; owner: string; onDone: () => void }) {
  const { i18n } = useTranslation()
  const toast = useToast()
  const [busy, setBusy] = useState(false)
  const [stars, setStars] = useState(5)
  const [comment, setComment] = useState('')
  const en = i18n.language === 'en'
  if (b.status !== 'completed') return null
  const submit = async () => {
    setBusy(true)
    try {
      await customerBookingsApi.rate(owner, b.id, { stars, comment })
      toast(en ? 'Rating submitted' : 'Đã gửi đánh giá chuyến đi')
      onDone()
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Không gửi được đánh giá', 'error')
    } finally { setBusy(false) }
  }
  return <div className="card">
    <div className="card-header"><h3><i className="fa-solid fa-star" /> {en ? 'Trip feedback' : 'Đánh giá chuyến đi'}</h3></div>
    {b.rating ? <p>{en ? 'Your rating: ' : 'Bạn đã chấm: '}{b.rating.stars ?? b.rating.trip}/5 ★{b.rating.comment ? ` · ${b.rating.comment}` : ''}</p> : <>
      <div className="form-group"><label htmlFor="rt-stars">{en ? 'Satisfaction level' : 'Mức độ hài lòng'}</label>
        <FormSelect id="rt-stars" className="form-control" value={stars} onChange={e => setStars(Number(e.target.value))}>
          {[5, 4, 3, 2, 1].map(n => <option key={n} value={n}>{n} {en ? 'stars' : 'sao'}</option>)}
        </FormSelect>
      </div>
      <div className="form-group"><label htmlFor="rt-note">{en ? 'Feedback (optional)' : 'Nhận xét (không bắt buộc)'}</label>
        <input id="rt-note" className="form-control" maxLength={2000} value={comment} onChange={e => setComment(e.target.value)} />
      </div>
      <button className="btn btn-primary btn-lg btn-full" disabled={busy} onClick={submit}>{busy ? (en ? 'Sending…' : 'Đang gửi…') : (en ? 'Submit rating' : 'Gửi đánh giá')}</button>
    </>}
  </div>
}
