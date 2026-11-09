import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { HOTLINE, MIN_LEAD_DAYS } from '@shared/config/business-rules'
import { earliestRescheduleDay } from '@shared/lib/bookingSchedule'
import { formatDate } from '@shared/lib/format'
import { customerBookingsApi, type CustomerBookingView } from '@shared/services/bookings'
import { useToast } from '@shared/ui/toast'
import { useNow } from '@shared/ui/useNow'

export function ScheduleChangeCard({ b, onDone }: { b: CustomerBookingView; onDone: () => void }) {
  const { i18n } = useTranslation()
  const en = i18n.language.startsWith('en')
  const toast = useToast()
  const [mode, setMode] = useState<'date' | 'cancel'>('date')
  const [day, setDay] = useState('')
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const now = useNow()
  const minimum = earliestRescheduleDay(now, MIN_LEAD_DAYS)
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (mode === 'date' && (!day || day < minimum)) {
      setError(en ? 'Please choose an available future date.' : 'Vui lòng chọn ngày mới đáp ứng thời gian chuẩn bị tối thiểu.')
      return
    }
    setBusy(true)
    setError('')
    try {
      if (mode === 'date') await customerBookingsApi.changeSchedule(b.id, day)
      else await customerBookingsApi.cancelScheduleRequest(b.id, reason.trim())
      toast(mode === 'date'
        ? (en ? 'New date sent for review.' : 'Đã gửi lịch mới. Chờ công ty kiểm tra và xác nhận.')
        : (en ? 'Unpaid request cancelled.' : 'Đã hủy yêu cầu chưa thanh toán.'))
      onDone()
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : (en ? 'Please try again.' : 'Vui lòng thử lại.'))
    } finally { setBusy(false) }
  }
  const support = async () => {
    setBusy(true)
    setError('')
    try {
      await customerBookingsApi.requestScheduleSupport(b.id)
      toast(en ? 'The manager has been asked to contact you.' : 'Đã gửi yêu cầu quản lý liên hệ hỗ trợ.')
      onDone()
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : (en ? 'Please try again.' : 'Vui lòng thử lại.'))
    } finally { setBusy(false) }
  }
  return (
    <section className="card" aria-labelledby="schedule-options-title">
      <div className="card-header"><h3 id="schedule-options-title">{en ? 'Choose what to do next' : 'Chọn phương án cho đơn của bạn'}</h3></div>
      <p>{en ? 'Previous departure date: ' : 'Ngày khởi hành cũ: '}{formatDate(b.departAt)}.</p>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 16 }}>
        <button type="button" className={`btn ${mode === 'date' ? 'btn-primary' : 'btn-ghost'}`} disabled={busy} aria-pressed={mode === 'date'} onClick={() => { setMode('date'); setError('') }}>{en ? 'Choose a new date' : 'Chọn ngày mới'}</button>
        <button type="button" className={`btn ${mode === 'cancel' ? 'btn-danger' : 'btn-ghost'}`} disabled={busy} aria-pressed={mode === 'cancel'} onClick={() => { setMode('cancel'); setError('') }}>{en ? 'Cancel request' : 'Hủy yêu cầu'}</button>
      </div>
      <form onSubmit={submit}>
        {mode === 'date' ? <>
          <div className="form-group"><label htmlFor="new-departure-date">{en ? 'Requested new departure date' : 'Ngày khởi hành mới mong muốn'}</label>
            <input id="new-departure-date" className="form-control" type="date" min={minimum} required value={day} disabled={busy} onChange={event => setDay(event.target.value)} />
          </div>
          <p className="form-hint">{en ? `Earliest date: ${minimum}. The company will recheck documents, insurance, vehicles, crew and pricing before confirming the trip.` : `Ngày sớm nhất: ${minimum.split('-').reverse().join('/')}. Công ty sẽ kiểm tra lại hồ sơ, bảo hiểm, xe, kíp xe và báo giá trước khi xác nhận chuyến.`}</p>
        </> : <>
          <p>{en ? 'This cancels the unpaid request. Please confirm by entering a reason and selecting the button below.' : 'Bạn đang hủy yêu cầu chưa thanh toán. Nhập lý do và bấm nút bên dưới để xác nhận.'}</p>
          <div className="form-group"><label htmlFor="schedule-cancel-reason">{en ? 'Reason for cancellation' : 'Lý do hủy'}</label><textarea id="schedule-cancel-reason" className="form-control" required maxLength={500} rows={3} value={reason} disabled={busy} onChange={event => setReason(event.target.value)} /></div>
        </>}
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className={`btn ${mode === 'cancel' ? 'btn-danger' : 'btn-primary'}`} disabled={busy || (mode === 'cancel' && !reason.trim())}>{busy ? (en ? 'Processing…' : 'Đang xử lý…') : mode === 'date' ? (en ? 'Submit new date' : 'Gửi yêu cầu đổi lịch') : (en ? 'Confirm cancellation' : 'Xác nhận hủy yêu cầu')}</button>
      </form>
      <hr />
      {b.scheduleChange?.supportRequestedAt
        ? <p role="status">{en ? 'Your support request was sent. The manager will contact you.' : 'Đã gửi yêu cầu hỗ trợ. Quản lý sẽ liên hệ với bạn.'}</p>
        : <button className="btn btn-ghost" type="button" disabled={busy} onClick={support}>{en ? 'Ask the manager to contact me' : 'Nhờ quản lý liên hệ hỗ trợ'}</button>}
      <p className="form-hint">{en ? 'You can also call ' : 'Bạn cũng có thể gọi '}<a href={`tel:${HOTLINE.replace(/\s/g, '')}`}>{HOTLINE}</a>.</p>
    </section>
  )
}
