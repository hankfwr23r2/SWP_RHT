import { useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'

import { Button } from '@shared/ui/Button'

export function ReviewRowActions({ row }: { row: { id: number } }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  return <Button size="sm" fullWidth onClick={() => navigate('/specialist/review/' + row.id)}>{t('staffReview.open')}</Button>
}
