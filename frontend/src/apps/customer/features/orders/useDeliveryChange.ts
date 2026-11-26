import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { bookingOrdersApi, type DeliveryChangeSelection, type DeliveryChangePreview } from '@shared/api/bookingOrders'
import { customerAddressesApi } from '@shared/api/customerAddresses'
import { useLoad } from '@shared/hooks/useLoad'
import { useToast } from '@shared/ui/toast'

export function useDeliveryChange(id: number, onChanged: () => void) {
  const { t } = useTranslation()
  const toast = useToast()
  const addresses = useLoad(customerAddressesApi.list, [], { toastOnError: false })
  const [quote, setQuote] = useState<{ selection: DeliveryChangeSelection; price: DeliveryChangePreview }>()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})

  function reset() {
    setQuote(undefined)
    setError('')
    setFields({})
  }

  function showError(cause: unknown) {
    const problem = cause as { status?: number; fields?: Record<string, string>; message?: string }
    setFields(Object.fromEntries(Object.entries(problem.fields ?? {}).map(([field, message]) => [field.replace(/^selection\./, ''), message])))
    setError(problem.status === 409 ? (problem.message || t('myOrders.deliveryChange.conflict')) : t('myOrders.deliveryChange.error'))
  }

  async function preview(selection: DeliveryChangeSelection) {
    reset()
    setBusy(true)
    try {
      const price = await bookingOrdersApi.previewDeliveryChange(id, selection)
      setQuote({ selection, price })
    } catch (cause) {
      showError(cause)
    } finally {
      setBusy(false)
    }
  }

  async function confirm() {
    if (quote?.price.newTotal == null) return false
    try {
      await bookingOrdersApi.confirmDeliveryChange(id, { selection: quote.selection, expectedTotal: quote.price.newTotal })
      toast(t('myOrders.deliveryChange.success'), 'success')
      onChanged()
      return true
    } catch (cause) {
      reset()
      showError(cause)
      return false
    }
  }

  return { addresses, busy, quote, preview, confirm, reset, error, fields }
}
