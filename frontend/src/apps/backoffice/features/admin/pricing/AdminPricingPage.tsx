import { Input } from '@shared/ui/Input'
import { EmptyState } from '@shared/ui/EmptyState'
import { ListPanel } from '@shared/ui/ListPanel'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { Table } from '@shared/ui/Table'
import { PriceListForm } from './components/PriceListForm'
import { PriceChoice } from './components/PricingFields'
import { PricingDataBoundary, PricingLayout, PricingStatus } from './components/PricingLayout'
import type { PricingData } from './loaders'
import { usePricingActions } from './usePricingActions'
import s from './AdminPricing.module.css'

const statuses = ['DRAFT', 'ACTIVE', 'ARCHIVED'] as const

function PriceLists({ lists = [] }: PricingData) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const actions = usePricingActions()
  const [status, setStatus] = useState('')
  const [creating, setCreating] = useState(false)
  const [query, setQuery] = useState('')
  const term = query.trim().toLowerCase()
  const visible = lists.filter(list => (!status || list.status === status) &&
    (!term || [list.name, list.currencyCode].some(value => value?.toLowerCase().includes(term))))
  return <>
    <ListPanel count={visible.length} toolbar={<div className={s.header}>
      <Input className={s.listSearch} type="search" value={query} onChange={event => setQuery(event.target.value)}
          placeholder={t('listPanel.search')} aria-label={t('listPanel.search')} />
      <PriceChoice name="status" value={status} onChange={setStatus} options={[
        { value: '', label: t('staffNetwork.allStatuses') },
        ...statuses.map(value => ({ value, label: t(`staffNetwork.priceStatus.${value}`) })),
      ]} />
      {(!!lists.length || !!status) && <Button variant="primary" onClick={() => { actions.reset(); setCreating(true) }}>{t('staffNetwork.addPriceList')}</Button>}
    </div>}>
    {visible.length ? <Table caption={t('staffNetwork.pricing')} rows={visible} rowKey={row => row.id ?? row.name ?? ''} columns={[
      { key: 'name', header: t('staffNetwork.fields.name'), render: row => <strong>{row.name}</strong> },
      { key: 'currency', header: t('staffNetwork.fields.currencyCode'), render: row => row.currencyCode },
      { key: 'effective', header: t('staffNetwork.fields.validFrom'), render: row => t('staffNetwork.effectiveRange', { from: row.validFrom, to: row.validTo ?? t('staffNetwork.noEnd') }) },
      { key: 'status', header: t('listPanel.status'), render: row => <PricingStatus status={row.status} /> },
      { key: 'view', header: '', render: row => row.id != null && <Button size="sm" onClick={() => navigate(`/admin/pricing/${row.id}`)}>{t('staffNetwork.viewPriceList')}</Button> },
    ]} /> : <EmptyState compact title={t('staffNetwork.empty')} description={t(term ? 'listPanel.trySearch' : status ? 'staffNetwork.emptyFilter' : 'staffNetwork.emptyPriceLists')}
      action={!lists.length && !status && <Button variant="primary" onClick={() => { actions.reset(); setCreating(true) }}>{t('staffNetwork.addPriceList')}</Button>} />}
    </ListPanel>
    {creating && <PriceListForm list={null} busy={actions.busy} fields={actions.fields}
      onSave={body => actions.saveList(body)} close={() => setCreating(false)} />}
  </>
}

export default function AdminPricingPage() {
  const { t } = useTranslation()
  return <PricingLayout title={t('staffNetwork.pricing')}>
    <PricingDataBoundary<PricingData> panel>{data => <PriceLists {...data} />}</PricingDataBoundary>
  </PricingLayout>
}
