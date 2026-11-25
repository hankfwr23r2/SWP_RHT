import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { custodyApi, type BalanceContactTarget } from '@shared/api/custody'
import { useToast } from '@shared/ui/toast'

export type BalanceAction = 'contact' | 'extend' | 'transfer'

export function usePickupBalanceAction(bookingId: number, onDone: () => void) {
  const { t } = useTranslation()
  const toast = useToast()
  const [busy, setBusy] = useState(false)

  const submit = async (action: BalanceAction, target: BalanceContactTarget, note: string) => {
    setBusy(true)
    try {
      if (action === 'contact') await custodyApi.recordBalanceContact(bookingId, { target, result: note })
      if (action === 'extend') await custodyApi.extendPickupWait(bookingId, { reason: note })
      if (action === 'transfer') await custodyApi.sendPickupToHub(bookingId, { reason: note })
      toast(t('tracking.balance.saved'), 'success')
      onDone()
    } catch (error) {
      toast(error instanceof Error ? error.message : t('tracking.balance.failed'), 'error')
    } finally {
      setBusy(false)
    }
  }

  return { busy, submit }
}
