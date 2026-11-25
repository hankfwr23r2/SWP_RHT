import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'

export function ReviewRowActions({ row }: { row: { id: number } }) {
  const { t } = useTranslation()
  return <Link to={'/specialist/review/' + row.id}>{t('staffReview.open')}</Link>
}
