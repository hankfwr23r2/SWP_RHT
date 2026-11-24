import { useTranslation } from 'react-i18next'
import { Card } from '@shared/ui/Card'
import { EmptyState } from '@shared/ui/EmptyState'
import { PageHeader } from '@shared/ui/PageHeader'
import s from '@backoffice/shared/Catalog.module.css'

export default function AdminHorseTypesPage() {
  const { t } = useTranslation()
  return <section className={s.page}>
    <PageHeader title={t('staffCatalog.horseTypes.title')} />
    <Card>
      <EmptyState title={t('staffCatalog.horseTypes.unavailable')} description={t('staffCatalog.horseTypes.access')} />
    </Card>
  </section>
}
