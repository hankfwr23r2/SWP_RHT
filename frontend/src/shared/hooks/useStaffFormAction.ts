import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useRevalidator } from 'react-router'
import { useToast } from '@shared/ui/toast'

export function useStaffFormAction() {
  const { t } = useTranslation()
  const toast = useToast()
  const { revalidate } = useRevalidator()
  const running = useRef(false)
  const [busy, setBusy] = useState(false)
  const [fields, setFields] = useState<Record<string, string>>({})

  function reset() { setFields({}) }

  async function run(action: () => Promise<unknown>, refresh = true) {
    if (running.current) return false
    running.current = true
    setBusy(true)
    reset()
    try {
      await action()
      toast(t('staffNetwork.saved'))
      if (refresh) await revalidate()
      return true
    } catch (cause) {
      const fieldErrors = typeof cause === 'object' && cause !== null && 'fields' in cause ? cause.fields : undefined
      if (fieldErrors && typeof fieldErrors === 'object') {
        setFields(Object.fromEntries(Object.entries(fieldErrors).filter(([, value]) => typeof value === 'string')))
      }
      toast(t('staffNetwork.actionError'), 'error')
      return false
    } finally {
      running.current = false
      setBusy(false)
    }
  }

  return { busy, fields, reset, run }
}
