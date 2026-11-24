import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { bookingReviewsApi, type BookingSummary } from '@shared/api/bookingReviews'
import { dispatchApi } from '@shared/api/dispatch'
import { useLoad } from '@shared/hooks/useLoad'
import { formatDateTime } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Modal } from '@shared/ui/Modal'
import { Skeleton } from '@shared/ui/Skeleton'
import { useToast } from '@shared/ui/toast'
import { departureOptions, searchRange } from './plans'
import s from './Plans.module.css'

// Đổi đơn sang chuyến khác cùng tuyến; backend kiểm chỗ, hạn chốt và giấy rồi báo khách
export function ChangeDepartureModal({ booking, onClose, onDone }: { booking: BookingSummary; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation()
  const toast = useToast()
  const [chosen, setChosen] = useState<number>()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const range = searchRange(new Date())
  const { data, loaded, error: loadError, reload } = useLoad(() => dispatchApi.departures(range.from, range.to), [range.from], { toastOnError: false })
  const options = departureOptions(data ?? [], booking)

  const save = async () => {
    if (chosen == null || booking.id == null) return
    setBusy(true)
    setError('')
    try {
      await bookingReviewsApi.changeDeparture(booking.id, chosen)
      toast(t('plans.changed'), 'success')
      onDone()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '')
    } finally {
      setBusy(false)
    }
  }
  const content = () => {
    if (loadError) return <ErrorState message={loadError.message} onRetry={reload} />
    if (!loaded) return <Skeleton lines={4} />
    if (!options.length) return <EmptyState title={t('plans.noOther')} description={t('plans.noOtherHint')} />
    return (
      <ul className={s.list}>
        {options.map(option => (
          <li key={option.id}>
            <label className={s.option}>
              <input type="radio" name="departure" checked={chosen === option.id} disabled={busy} onChange={() => { setChosen(option.id); setError('') }} />
              <span><strong>{option.plannedDepartureAt ? formatDateTime(new Date(option.plannedDepartureAt)) : ''}</strong><br />
                {t('plans.seats', { count: option.availableStalls ?? 0 })}</span>
            </label>
          </li>
        ))}
      </ul>
    )
  }
  return (
    <Modal title={t('plans.changeTitle', { code: booking.code })} subtitle={booking.routeName} onClose={() => { if (!busy) onClose() }} footer={<>
      <Button onClick={onClose} disabled={busy}>{t('ui.cancel')}</Button>
      <Button variant="primary" disabled={chosen == null} loading={busy} onClick={save}>{t('plans.change')}</Button>
    </>}>
      <p className={s.hint}>{t('plans.changeHint')}</p>
      {error && <p className={s.error} role="alert">{error}</p>}
      {content()}
    </Modal>
  )
}
