import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { BookingDocument } from '@shared/api/bookingDocuments'
import type { BookingDetail, BookingStatus } from '@shared/api/bookingOrders'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { EmptyState } from '@shared/ui/EmptyState'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { showDate } from '../reviewDisplay'
import type { ReviewAction, ReviewActions } from '../useReviewActions'
import { ActionButtons } from './ActionButtons'
import { InsuranceEvidenceDialog } from './InsuranceEvidenceDialog'
import { InsurancePremiumRefundDialog } from './InsurancePremiumRefundDialog'
import s from '../Review.module.css'

type Horse = NonNullable<BookingDetail['horses']>[number]
const insuranceTone = { DRAFT: 'neutral', REVISION_REQUESTED: 'warning', CONFIRMED: 'success' } as const

function HorseInsurance({ horse, editable, canEdit, documents, actions }: {
  horse: Horse; editable: boolean; canEdit: boolean; documents?: BookingDocument[]; actions: ReviewActions
}) {
  const { t, i18n } = useTranslation()
  const [recording, setRecording] = useState(false)
  const [refunding, setRefunding] = useState(false)
  const insurance = horse.insurance
  const { id, status, coverageOption, insurerName, policyNumber, validFrom, validTo, revisionReason,
    coverageAmount, premiumAmount, currencyCode, quoteExpiresAt, waiverTermsAcceptanceId } = insurance ?? {}
  const policyFile = documents?.find(row => row.bookingHorseId === horse.id && row.typeCode === 'INSURANCE_POLICY')
  const options: ReviewAction[] = []
  const draft = editable && status === 'DRAFT' && id != null && coverageOption != null
  if (draft) {
    if (coverageOption !== 'PERSONAL' || policyFile?.fileUrl) options.push(actions.confirmInsurance(id))
    if (coverageOption === 'PERSONAL') options.push(actions.requestRevision(id))
  }
  if (editable && status === 'CONFIRMED' && id != null) options.push(actions.reopenInsurance(id))
  const money = (amount: number) => [new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 2 }).format(amount), currencyCode].filter(Boolean).join(' ')
  const date = (value: string) => showDate(value)

  return <li className={s.item}>
    <div className={s.stack}>
      <div className={s.row}>
        <h3>{horse.horseName}</h3>
        {status && <StatusBadge tone={insuranceTone[status]} label={t(`staffReview.insuranceStatus.${status}`)} />}
      </div>
      {!insurance ? <p>{t('staffReview.noInsurance')}</p> : <dl className={s.facts}>
        {coverageOption && <div><dt>{t('staffReview.coverage')}</dt><dd>{t(`bookingForm.services.coverage.${coverageOption}`)}</dd></div>}
        {insurerName && <div><dt>{t('staffReview.insurer')}</dt><dd>{insurerName}</dd></div>}
        {policyNumber && <div><dt>{t('staffReview.policyNumber')}</dt><dd>{policyNumber}</dd></div>}
        {coverageOption === 'PERSONAL' && documents && <div>
          <dt>{t('staffReview.policyFile')}</dt>
          <dd>{policyFile?.fileUrl
            ? <a className={s.file} href={policyFile.fileUrl} target="_blank" rel="noopener noreferrer">
              {t('staffReview.viewFile', { name: policyFile.fileName ?? '' })}</a>
            : t('staffReview.policyFileMissing')}</dd>
        </div>}
        {validFrom && <div><dt>{t('staffReview.validFrom')}</dt><dd>{date(validFrom)}</dd></div>}
        {validTo && <div><dt>{t('staffReview.validTo')}</dt><dd>{date(validTo)}</dd></div>}
        {coverageAmount != null && <div><dt>{t('staffReview.coverageAmount')}</dt><dd>{money(coverageAmount)}</dd></div>}
        {premiumAmount != null && <div><dt>{t('staffReview.premiumAmount')}</dt><dd>{money(premiumAmount)}</dd></div>}
        {insurance.refundedPremiumAmount != null && <div><dt>{t('staffReview.refundedPremiumAmount')}</dt><dd>{money(insurance.refundedPremiumAmount)}</dd></div>}
        {quoteExpiresAt && <div><dt>{t('staffReview.quoteExpiry')}</dt><dd>{date(quoteExpiresAt)}</dd></div>}
        {waiverTermsAcceptanceId != null && <div><dt>{t('staffReview.waiver')}</dt><dd>{t('staffReview.waiverAccepted')}</dd></div>}
        {revisionReason && <div><dt>{t('staffReview.reason')}</dt><dd>{revisionReason}</dd></div>}
      </dl>}
      <div className={s.actions}>
        {draft && coverageOption !== 'NONE' ? <Button size="sm" disabled={actions.busy} onClick={() => {
          actions.clear()
          setRecording(true)
        }}>{t(coverageOption === 'PERSONAL' ? 'staffReview.recordPolicy' : 'staffReview.recordEvidence')}</Button> : null}
        <ActionButtons options={options} actions={actions} />
        {canEdit && horse.status === 'EXCLUDED' && status === 'CONFIRMED' && coverageOption === 'ARRANGED' && id != null &&
          <Button size="sm" disabled={actions.busy} onClick={() => setRefunding(true)}>{t('staffReview.recordPremiumRefund')}</Button>}
      </div>
      {recording && id != null && insurance &&
        <InsuranceEvidenceDialog insurance={insurance} id={id} actions={actions} onClose={() => setRecording(false)} />}
      {refunding && id != null &&
        <InsurancePremiumRefundDialog id={id} actions={actions} onClose={() => setRefunding(false)} />}
    </div>
  </li>
}

export function InsurancesCard({ horses, status, canEdit, documents, actions }: {
  horses?: BookingDetail['horses']; status?: BookingStatus; canEdit: boolean;
  documents?: BookingDocument[]; actions: ReviewActions
}) {
  const { t } = useTranslation()
  const reviewing = status === 'UNDER_REVIEW' || status === 'NEEDS_RESUBMISSION'
  const quoting = status === 'QUOTING'
  return <Card title={t('staffReview.insurance')}>
    {horses?.length ? <ul className={s.list}>{horses.map(horse =>
      <HorseInsurance key={horse.id} horse={horse} canEdit={canEdit} documents={documents} actions={actions}
        editable={canEdit && horse.status === 'ACTIVE' && (reviewing || (quoting && horse.insurance?.coverageOption === 'ARRANGED'))} />
    )}</ul> : <EmptyState title={t('staffReview.noHorses')} description={t('staffReview.noInsuranceHint')} />}
  </Card>
}
