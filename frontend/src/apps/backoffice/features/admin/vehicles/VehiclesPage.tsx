import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CatalogState } from '@backoffice/shared/CatalogState'
import { useCatalogData } from '@backoffice/shared/useCatalogData'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { PageHeader } from '@shared/ui/PageHeader'
import { Table } from '@shared/ui/Table'
import s from '@backoffice/shared/Catalog.module.css'
import type { loader } from './loaders'
import { CatalogStatus } from '@backoffice/shared/CatalogStatus'
import type { Vehicle } from '@shared/api/vehicles'
import { VehicleForm } from './VehicleForm'
import { VehicleStatusForm } from './VehicleStatusForm'
import type { VehicleAction } from './useVehicles'

export default function VehiclesPage() {
  const { t } = useTranslation()
  const state = useCatalogData<typeof loader>()
  const [editor, setEditor] = useState<Vehicle | 'new'>()
  const [transition, setTransition] = useState<{ id: number; plate?: string; event: VehicleAction }>()
  const rows = state.data?.vehicles ?? []
  const classes = state.data?.classes ?? []
  const hubs = state.data?.hubs ?? []
  const text = (field: string) => t(`staffCatalog.fields.${field}`)
  const title = t('staffCatalog.vehicles.title')
  return <section className={s.page}>
    <PageHeader title={title} actions={<Button variant="primary" disabled={!state.data || state.failed} onClick={() => setEditor('new')}>{t('staffCatalog.vehicles.add')}</Button>} />
    <Card>
      <CatalogState {...state} empty={!rows.length} description={t('staffCatalog.vehicles.empty')}>
        <Table caption={title} rows={rows} rowKey={row => row.id ?? row.licensePlate ?? ''}
          columns={[
            { key: 'plate', header: text('licensePlate'), render: row => <strong>{row.licensePlate}</strong> },
            { key: 'class', header: text('vehicleClassId'), render: row => {
              const vehicleClass = classes.find(item => item.id === row.vehicleClassId)
              return <div>
                <div>{vehicleClass?.name ?? '—'}</div>
                {vehicleClass?.maxStallCount != null && <small>{t('staffCatalog.vehicles.classLimit', { count: vehicleClass.maxStallCount })}</small>}
              </div>
            } },
            { key: 'hub', header: text('homeHubLocationId'), render: row => hubs.find(item => item.id === row.homeHubLocationId)?.name ?? row.homeHubLocationCode ?? '—' },
            { key: 'standard', header: text('standardStallCount'), render: row => row.standardStallCount ?? '—' },
            { key: 'wide', header: text('wideStallCount'), render: row => row.wideStallCount ?? '—' },
            { key: 'status', header: text('status'), render: row => <CatalogStatus status={row.status} /> },
            { key: 'actions', header: text('actions'), render: row => {
              const id = row.id
              if (id == null) return null
              const buttons: VehicleAction[] = row.status === 'ACTIVE' ? ['startMaintenance', 'retire']
                : row.status === 'MAINTENANCE' ? ['reactivate', 'retire'] : []
              return <div className={s.actions}>
                <Button size="sm" onClick={() => setEditor(row)}>{t('staffCatalog.edit')}</Button>
                {buttons.map(event => <Button key={event} size="sm" onClick={() => setTransition({ id, plate: row.licensePlate, event })}>
                  {t(`staffCatalog.vehicles.actions.${event}`)}
                </Button>)}
              </div>
            } },
          ]} />
      </CatalogState>
    </Card>
    {editor && <VehicleForm vehicle={editor === 'new' ? undefined : editor} classes={classes} hubs={hubs} reload={state.reload} close={() => setEditor(undefined)} />}
    {transition && <VehicleStatusForm {...transition} reload={state.reload} close={() => setTransition(undefined)} />}
  </section>
}
