import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { PriceListForm } from './components/PriceListForm'
import { PriceChoice } from './components/PricingFields'
import { PricingDataBoundary, PricingEmpty, PricingLayout, PricingStatus } from './components/PricingLayout'
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
  const visible = lists.filter(list => !status || list.status === status)
  return <>
    <div className={s.header}>
      <PriceChoice name="status" value={status} onChange={setStatus} options={[
        { value: '', label: t('staffNetwork.allStatuses') },
        ...statuses.map(value => ({ value, label: t(`staffNetwork.priceStatus.${value}`) })),
      ]} />
      <Button variant="primary" onClick={() => { actions.reset(); setCreating(true) }}>{t('staffNetwork.addPriceList')}</Button>
    </div>
    {visible.length ? <div className={s.list}>{visible.map(list => <Card key={list.id} title={list.name} actions={<PricingStatus status={list.status} />}>
      <div className={s.details}>
        <div>{list.currencyCode} · {t('staffNetwork.effectiveRange', { from: list.validFrom, to: list.validTo ?? t('staffNetwork.noEnd') })}</div>
        {list.id != null && <div><Button size="sm" onClick={() => navigate(`/admin/pricing/${list.id}`)}>{t('staffNetwork.viewPriceList')}</Button></div>}
      </div>
    </Card>)}</div> : <PricingEmpty hint={t(status ? 'staffNetwork.emptyFilter' : 'staffNetwork.emptyPriceLists')} />}
    {creating && <PriceListForm list={null} busy={actions.busy} fields={actions.fields}
      onSave={body => actions.saveList(body)} close={() => setCreating(false)} />}
  </>
}

export default function AdminPricingPage() {
  const { t } = useTranslation()
  return <PricingLayout title={t('staffNetwork.pricing')}>
    <PricingDataBoundary<PricingData>>{data => <PriceLists {...data} />}</PricingDataBoundary>
  </PricingLayout>
}
