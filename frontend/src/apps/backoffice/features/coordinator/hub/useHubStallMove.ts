import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { hubStallsApi } from '@shared/api/hubStalls'
import { useToast } from '@shared/ui/toast'

export function useHubStallMove(stayId: number, onMoved: () => Promise<void>, onClose: () => void) {
  const { t } = useTranslation()
  const toast = useToast()
  const [selected, select] = useState('')
  const [busy, setBusy] = useState(false)

  async function move() {
    setBusy(true)
    try {
      await hubStallsApi.move(stayId, { hubStallId: Number(selected) })
      toast(t('hubStalls.moved'), 'success')
      onClose()
    } catch (error) {
      toast(error instanceof Error ? error.message : t('hubStalls.error'), 'error')
      select('')
    } finally {
      await onMoved()
      setBusy(false)
    }
  }

  return { selected, select, busy, move }
}
