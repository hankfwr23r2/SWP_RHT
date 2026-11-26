import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { usePickupAddOns } from './usePickupAddOns'
import type { PickupAddOn } from '@shared/api/pickupMisses'
import { Button } from '@shared/ui/Button'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Skeleton } from '@shared/ui/Skeleton'
import { useToast } from '@shared/ui/toast'

export function PickupAddOnPanel({ horseId }: { horseId: number }) {
  const { t } = useTranslation()
  const toast = useToast()
  const { data, loaded, error, reload, complete } = usePickupAddOns(horseId)
  const [selected, setSelected] = useState<PickupAddOn>()

  const confirm = async () => {
    if (selected?.serviceId == null) return
    try {
      await complete(selected.serviceId)
      setSelected(undefined)
      toast(t('pickupMiss.saved'), 'success')
      await reload()
    } catch (failure) {
      toast(t('pickupMiss.failed'), 'error')
      throw failure
    }
  }

  if (!loaded) return <Skeleton variant="block" lines={2} />
  if (error) return <ErrorState onRetry={reload} />
  if (!data?.length) return <EmptyState title={t('pickupMiss.noServices')} description={t('pickupMiss.noServicesHint')} />
  return (
    <>
      <ul>
        {data.map(addOn => (
          <li key={addOn.serviceId}>
            {addOn.name}{' '}
            {addOn.completedAt ? t('pickupMiss.serviceCompleted') : (
              <Button size="sm" onClick={() => setSelected(addOn)}>{t('pickupMiss.completeService')}</Button>
            )}
          </li>
        ))}
      </ul>
      {selected && (
        <ConfirmDialog title={t('pickupMiss.completeService')} confirmLabel={t('pickupMiss.completeService')}
          message={t('pickupMiss.completeServiceHint', { service: selected.name })}
          onCancel={() => setSelected(undefined)} onConfirm={confirm} />
      )}
    </>
  )
}
