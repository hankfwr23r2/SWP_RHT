import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { bookingOrdersApi, type BookingOrder } from '@shared/api/bookingOrders'
import type { CustomerAddress } from '@shared/api/customerAddresses'
import { clearDraft, type BookingDraft } from './draft'
import { createRequest } from './model'

export function useSubmitBooking() {
  const { t } = useTranslation()
  const sending = useRef(false)
  const [busy, setBusy] = useState(false)
  const [order, setOrder] = useState<BookingOrder>()
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})

  const submit = async (draft: BookingDraft, pickup: CustomerAddress, delivery: CustomerAddress, addressBook: CustomerAddress[]) => {
    if (sending.current) return
    sending.current = true
    setBusy(true)
    setError('')
    setFields({})
    try {
      const insuranceFiles = draft.horseIds.flatMap(id => {
        const horse = draft.horses[id]
        return horse.coverage === 'PERSONAL' && horse.policyFile ? [horse.policyFile] : []
      })
      setOrder(await bookingOrdersApi.create(createRequest(draft, pickup, delivery, addressBook), insuranceFiles))
      clearDraft()
    } catch (cause) {
      const failure = cause as Error & { fields?: Record<string, string> }
      setFields(failure.fields ?? {})
      setError(failure.message || t('bookingForm.submitError'))
    } finally {
      sending.current = false
      setBusy(false)
    }
  }
  return { submit, busy, order, error, fields }
}
