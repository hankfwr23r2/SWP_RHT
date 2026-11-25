import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { deliveryComplaintsApi, DeliveryComplaintValidationError, type ComplaintDecision, type DeliveryComplaint } from '@shared/api/deliveryComplaints'
import { useToast } from '@shared/ui/toast'
import { useLoad } from './useLoad'

export function useDeliveryComplaint(bookingId: number, onChange?: () => void) {
  const load = useLoad(() => deliveryComplaintsApi.view(bookingId), [bookingId], { toastOnError: false })
  const toast = useToast()
  const { t } = useTranslation()
  const active = useRef(false)
  const [busy, setBusy] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  async function run(action: () => Promise<DeliveryComplaint>) {
    if (active.current) return false
    active.current = true
    setBusy(true)
    setFieldErrors({})
    try {
      const result = await action()
      load.updateData(() => result)
      toast(t('deliveryComplaint.saved'), 'success')
      onChange?.()
      return true
    } catch (error) {
      const message = error instanceof Error ? error.message : t('deliveryComplaint.failed')
      if (error instanceof DeliveryComplaintValidationError) setFieldErrors(error.fields)
      toast(message, 'error')
      return false
    } finally {
      active.current = false
      setBusy(false)
    }
  }

  return {
    ...load,
    busy,
    fieldErrors,
    confirm: () => run(() => deliveryComplaintsApi.confirm(bookingId)),
    submit: (body: FormData) => run(() => deliveryComplaintsApi.submit(bookingId, body)),
    submitOnBehalf: (body: FormData) => run(() => deliveryComplaintsApi.submitOnBehalf(bookingId, body)),
    reply: (reply: string) => run(() => deliveryComplaintsApi.reply(bookingId, { reply })),
    resolve: (decision: ComplaintDecision, reason: string) => run(() => deliveryComplaintsApi.resolve(bookingId, { decision, reason })),
  }
}
