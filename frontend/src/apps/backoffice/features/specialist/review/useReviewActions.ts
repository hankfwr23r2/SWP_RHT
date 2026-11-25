import { useRef, useState } from 'react'
import { useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import { bookingDocumentsApi, type ChecklistItem } from '@shared/api/bookingDocuments'
import { bookingReviewsApi, type InsuranceEvidence, type InsurancePremiumRefund } from '@shared/api/bookingReviews'
import { useToast } from '@shared/ui/toast'

export type ReviewAction = {
  label: string
  run: (reason: string) => Promise<unknown>
  reason?: boolean
  danger?: boolean
  // Nút chính của bước này
  primary?: boolean
  // Lý do gợi ý sẵn, người dùng sửa được
  initialReason?: string
}

export function useReviewActions(id: number) {
  const { t } = useTranslation()
  const { revalidate } = useRevalidator()
  const toast = useToast()
  const pending = useRef(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})

  const clear = () => { setError(''); setFields({}) }
  const run = async (action: () => Promise<unknown>) => {
    if (pending.current) return false
    pending.current = true
    setBusy(true)
    clear()
    try {
      await action()
      toast(t('staffReview.saved'))
      await revalidate()
      return true
    } catch (cause) {
      const formFields = cause instanceof Error && 'fields' in cause ? cause.fields as Record<string, string> : {}
      setFields(formFields)
      const message = cause instanceof Error && cause.message ? cause.message : t('staffReview.failed')
      setError(message)
      toast(Object.keys(formFields).length ? t('staffReview.checkFields') : message, 'error')
      return false
    } finally {
      pending.current = false
      setBusy(false)
    }
  }

  return {
    run, busy, error, fields, clear,
    intake: (): ReviewAction => ({ label: 'intake', primary: true, run: () => bookingReviewsApi.intake(id) }),
    returnForResubmission: (): ReviewAction => ({
      label: 'return', reason: true, run: reason => bookingReviewsApi.returnForResubmission(id, { reason }),
    }),
    askDocument: (name: string): ReviewAction => ({
      label: 'askDocument', reason: true, initialReason: t('staffReview.askReason', { name }),
      run: reason => bookingReviewsApi.returnForResubmission(id, { reason }),
    }),
    reject: (): ReviewAction => ({
      label: 'rejectOrder', reason: true, danger: true, run: reason => bookingReviewsApi.reject(id, { reason }),
    }),
    startQuoting: (): ReviewAction => ({ label: 'startQuoting', primary: true, run: () => bookingReviewsApi.startQuoting(id) }),
    approveDocument: (documentId: number): ReviewAction => ({
      label: 'approveDocument', primary: true, run: () => bookingDocumentsApi.approve(id, documentId),
    }),
    rejectDocument: (documentId: number): ReviewAction => ({
      label: 'rejectDocument', reason: true, danger: true, run: reason => bookingDocumentsApi.reject(id, documentId, { reason }),
    }),
    upload: (item: ChecklistItem, file: File, expiry: string) =>
      run(() => bookingDocumentsApi.uploadForPartner(id, item, file, expiry)),
    confirmInsurance: (insuranceId: number): ReviewAction => ({
      label: 'confirmInsurance', primary: true, run: () => bookingReviewsApi.confirmInsurance(id, insuranceId),
    }),
    requestRevision: (insuranceId: number): ReviewAction => ({
      label: 'requestRevision', reason: true, run: reason => bookingReviewsApi.requestInsuranceRevision(id, insuranceId, { reason }),
    }),
    reopenInsurance: (insuranceId: number): ReviewAction => ({
      label: 'reopenInsurance', reason: true, run: reason => bookingReviewsApi.reopenInsurance(id, insuranceId, { reason }),
    }),
    recordEvidence: (insuranceId: number, body: InsuranceEvidence) =>
      run(() => bookingReviewsApi.recordEvidence(id, insuranceId, body)),
    recordPremiumRefund: (insuranceId: number, body: InsurancePremiumRefund) =>
      run(() => bookingReviewsApi.recordPremiumRefund(id, insuranceId, body)),
  }
}

export type ReviewActions = ReturnType<typeof useReviewActions>
