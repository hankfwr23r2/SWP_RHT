import { useTranslation } from 'react-i18next'
import { CatalogState } from '@backoffice/shared/CatalogState'
import { useCatalogData } from '@backoffice/shared/useCatalogData'
import { Card } from '@shared/ui/Card'
import { PageHeader } from '@shared/ui/PageHeader'
import { Table } from '@shared/ui/Table'
import s from '@backoffice/shared/Catalog.module.css'
import type { loader } from './loaders'

// Danh mục loại ngựa, chỉ xem
export default function AdminHorseTypesPage() {
  const { t } = useTranslation()
  const state = useCatalogData<typeof loader>()
  const rows = state.data?.horseTypes ?? []
  const title = t('staffCatalog.horseTypes.title')
  return <section className={s.page}>
    <PageHeader title={title} />
    <Card>
      <CatalogState {...state} empty={!rows.length} description={t('staffCatalog.horseTypes.empty')}>
        <Table caption={title} rows={rows} rowKey={row => row.id ?? row.name ?? ''} columns={[
          { key: 'name', header: t('staffCatalog.horseTypes.name'), render: row => <strong>{row.name}</strong> },
          { key: 'description', header: t('staffCatalog.horseTypes.description'), render: row => row.description ?? '—' },
        ]} />
      </CatalogState>
    </Card>
  </section>
}
