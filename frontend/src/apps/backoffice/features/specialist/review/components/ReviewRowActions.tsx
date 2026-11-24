import { useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import type { BookingSummary } from '@shared/api/bookingReviews'
import { Button } from '@shared/ui/Button'
import { useReviewActions } from '../useReviewActions'
import s from '../Review.module.css'

// Nút của một đơn trong danh sách: tiếp nhận ngay, hoặc mở đơn để thẩm định
export function ReviewRowActions({ row }: { row: BookingSummary & { id: number } }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { run, busy, intake } = useReviewActions(row.id)
  const path = `/specialist/review/${row.id}`
  const accept = async () => { if (await run(() => intake().run(''))) navigate(path) }
  return <div className={s.actions}>
    {row.status === 'PENDING_INTAKE' && <Button size="sm" variant="primary" loading={busy} onClick={accept}>{t('staffReview.action.intake')}</Button>}
    <Button size="sm" variant={row.status === 'UNDER_REVIEW' ? 'primary' : 'secondary'} disabled={busy} onClick={() => navigate(path)}>
      {t(row.status === 'UNDER_REVIEW' ? 'staffReview.reviewNow' : 'staffReview.open')}</Button>
  </div>
}
