// Giấy khách phải nộp cho tuyến đã chọn.
import { useTranslation } from 'react-i18next'
import type { RegulationPreview } from '@shared/api/bookingOrders'
import { Card } from '@shared/ui/Card'
import { ErrorState } from '@shared/ui/ErrorState'
import { Skeleton } from '@shared/ui/Skeleton'
import s from '../Booking.module.css'

interface Props {
  preview?: RegulationPreview
  loaded: boolean
  error?: Error
  onRetry: () => void
}

export function RegulationNotice({ preview, loaded, error, onRetry }: Props) {
  const { t } = useTranslation()
  const mine = preview?.requirements?.filter(item => item.submitterType === 'CUSTOMER') ?? []
  return (
    <Card title={t('bookingForm.route.documents')}>
      {error ? <ErrorState message={error.message} onRetry={onRetry} /> : !loaded ? <Skeleton lines={3} /> : (
        <div className={s.stack}>
          {mine.length ? (
            <ul className={s.docs}>
              {mine.map(item => (
                <li key={item.id}>
                  {item.documentTypeName} {!item.mandatory && <span className={s.hint}>({t('bookingForm.route.optional')})</span>}
                </li>
              ))}
            </ul>
          ) : <p className={s.hint}>{t('bookingForm.route.noDocuments')}</p>}
          <p className={s.hint}>{t('bookingForm.route.documentsHint')}</p>
        </div>
      )}
    </Card>
  )
}
