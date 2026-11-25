import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { bookingOrdersApi, type InsuranceRevision } from '@shared/api/bookingOrders'
import { useOrderAction } from './useOrderAction'

export function useInsuranceRevision(bookingId: number, insuranceId: number) {
  const { t } = useTranslation()
  const { run, busy, error, clearError } = useOrderAction()
  const [fields, setFields] = useState<Record<string, string>>({})

  const save = (request: InsuranceRevision, file: File) => run(async () => {
    setFields({})
    try {
      await bookingOrdersApi.reviseInsurance(bookingId, insuranceId, request, file)
    } catch (cause) {
      const failure = cause as Error & { fields?: Record<string, string> }
      setFields(failure.fields ?? {})
      throw cause
    }
  }, t('myOrders.insurance.saved'))

  const clearErrors = () => {
    setFields({})
    clearError()
  }
  return { save, busy, error, fields, clearErrors }
}
