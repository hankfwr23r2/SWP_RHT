import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi } from '@shared/api/dispatch'
import { useToast } from '@shared/ui/toast'
import { localToIso } from '@backoffice/shared/localTime'

export function useHubAppointment(bookingHorseId: number, onDone: () => void) {
  const { t } = useTranslation()
  const toast = useToast()
  const [at, setAt] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  function changeTime(value: string) {
    setAt(value)
    setError(undefined)
  }

  async function submit() {
    setBusy(true)
    setError(undefined)
    try {
      await dispatchApi.appointAtHub(bookingHorseId, localToIso(at))
      toast(t('dispatch.hub.done'), 'success')
      onDone()
    } catch (cause) {
      if (cause instanceof Error && 'status' in cause && cause.status === 400) setError(cause.message)
      else toast(cause instanceof Error ? cause.message : String(cause), 'error')
    } finally {
      setBusy(false)
    }
  }

  return { at, changeTime, busy, error, submit }
}
