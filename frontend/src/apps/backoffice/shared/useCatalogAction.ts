
import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useToast } from '@shared/ui/toast'

export function useCatalogAction(reload: () => unknown, fallbackField?: string) {
  const { t } = useTranslation()
  const toast = useToast()
  const running = useRef(false)
  const [busy, setBusy] = useState(false)
  const [fields, setFields] = useState<Record<string, string>>({})
  const [error, setError] = useState('')

  async function run(action: () => Promise<unknown>) {
    if (running.current) return false
    running.current = true
    setBusy(true)
    setFields({})
    setError('')
    try {
      await action()
      toast(t('staffCatalog.saved'))
      await reload()
      return true
    } catch (cause) {
      const structured = cause instanceof Error && 'fields' in cause
      const message = structured && cause.message ? cause.message : t('staffCatalog.saveFailed')
      const details = structured && cause.fields && typeof cause.fields === 'object'
        ? Object.fromEntries(Object.entries(cause.fields).filter((entry): entry is [string, string] => typeof entry[1] === 'string'))
        : {}
      setFields({ ...(fallbackField && !Object.keys(details).length ? { [fallbackField]: message } : {}), ...details })
      if (!fallbackField || (Object.keys(details).length && !details[fallbackField])) setError(message)
      toast(t('staffCatalog.saveFailed'), 'error')
      return false
    } finally {
      running.current = false
      setBusy(false)
    }
  }

  return { run, busy, fields, error }
}
