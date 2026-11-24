import { useTranslation } from 'react-i18next'
import type { ChecklistItem } from '@shared/api/bookingDocuments'

export function ChecklistFacts({ item, horseName }: { item: ChecklistItem; horseName?: string }) {
  const { t } = useTranslation()
  const submitter = item.submitterType ?? item.requirement?.submitterType
  return <div>
    <strong>{item.requirement?.documentTypeName ?? t('staffReview.unknownDocument')}
      {horseName && ` · ${horseName}`}
    </strong>
    <p>{submitter && t(`staffReview.submitter.${submitter}`)}
      {item.requiredForQuoting && ` · ${t('staffReview.beforeQuoting')}`}
      {item.requiredBeforePickup && ` · ${t('staffReview.beforePickup')}`}
      {!item.requirement?.mandatory && ` · ${t('staffReview.optional')}`}
    </p>
  </div>
}
