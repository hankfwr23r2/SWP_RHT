import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Skeleton } from '@shared/ui/Skeleton'
import { useToast } from '@shared/ui/toast'
import { useCollectionCode } from '../useCollectionCode'

export function CollectionCodeCard({ bookingId }: { bookingId: number }) {
  const { t } = useTranslation()
  const toast = useToast()
  const [confirming, setConfirming] = useState(false)
  const { data, loaded, error, reload, confirm } = useCollectionCode(bookingId)

  return (
    <Card title={t('handoverEvidence.code')}>
      {!loaded ? <Skeleton variant="block" lines={2} /> : error ? <ErrorState message={error.message} onRetry={reload} /> : (
        <>
          {data?.collectionCode ? <p><strong>{data.collectionCode}</strong></p>
            : <EmptyState title={t('handoverEvidence.noCode')} description={t('handoverEvidence.noCodeNext')} />}
          {data?.customerConfirmed
            ? <p>{t('handoverEvidence.confirmed')}</p>
            : <Button onClick={() => setConfirming(true)}>{t('handoverEvidence.confirm')}</Button>}
        </>
      )}
      {confirming && <ConfirmDialog title={t('handoverEvidence.confirm')} message={t('handoverEvidence.confirmMessage')}
        confirmLabel={t('handoverEvidence.confirm')} onCancel={() => setConfirming(false)}
        onConfirm={async () => {
          try {
            await confirm()
            setConfirming(false)
            toast(t('handoverEvidence.confirmed'), 'success')
          } catch (e) {
            toast(e instanceof Error ? e.message : String(e), 'error')
            throw e
          }
        }} />}
    </Card>
  )
}
