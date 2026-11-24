import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import type { PriceItem } from '@shared/api/priceLists'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { PriceItemForm, type PriceOptions } from './components/PriceItemForm'
import { PriceListForm } from './components/PriceListForm'
import { PricingConfirm, PricingDataBoundary, PricingEmpty, PricingLayout, PricingStatus, type Confirmation } from './components/PricingLayout'
import type { PricingDetailData } from './loaders'
import { usePricingActions } from './usePricingActions'
import s from './AdminPricing.module.css'

function byId(items: { id?: number; name?: string; code?: string }[]) {
  return items.flatMap(item => item.id == null ? [] : [{ value: String(item.id), label: item.name ?? item.code }])
}

function PriceListDetail({ list, items = [], services = [], routes = [], stallTypes = [], classes = [], canChooseClass }: PricingDetailData) {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const actions = usePricingActions()
  const [editing, setEditing] = useState(false)
  const [item, setItem] = useState<PriceItem | null | undefined>()
  const [confirmation, confirm] = useState<Confirmation | null>(null)
  if (list?.id == null) return <PricingEmpty hint={t('staffNetwork.missingPriceList')} />
  const id = list.id

  const options: PriceOptions = {
    services: byId(services), routes: byId(routes), classes: byId(classes),
    stalls: stallTypes.flatMap(stall => stall.code ? [{ value: stall.code, label: stall.name ?? stall.code }] : []),
  }
  const draft = list.status === 'DRAFT'
  const currency = list.currencyCode ?? ''
  const number = new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 2 })
  const percent = new Intl.NumberFormat(i18n.language, { style: 'percent', maximumFractionDigits: 2 })
  const rate = (value?: number) => value == null ? t('staffNetwork.unknown') : percent.format(value)
  const lookup = (choices: PriceOptions[keyof PriceOptions], value: string | number | undefined, fallback = t('staffNetwork.unknown')) =>
    value == null ? t('staffNetwork.any') : choices.find(choice => choice.value === String(value))?.label ?? fallback

  function editItem(value: PriceItem | null) { actions.reset(); setItem(value) }

  return <>
    <Card title={list.name} actions={<PricingStatus status={list.status} />}>
      <div className={s.details}>
        <div>{currency} · {t('staffNetwork.effectiveRange', { from: list.validFrom, to: list.validTo ?? t('staffNetwork.noEnd') })}</div>
        <dl className={s.rates}>
          <div><dt>{t('staffNetwork.fields.marginRate')}</dt><dd>{rate(list.marginRate)}</dd></div>
          <div><dt>{t('staffNetwork.fields.fuelBufferRate')}</dt><dd>{rate(list.fuelBufferRate)}</dd></div>
          <div><dt>{t('staffNetwork.fields.depositRate')}</dt><dd>{rate(list.depositRate)}</dd></div>
        </dl>
        <div className={s.actions}>
          {draft && <>
            <Button size="sm" disabled={actions.busy} onClick={() => { actions.reset(); setEditing(true) }}>{t('staffNetwork.editPriceList')}</Button>
            <Button size="sm" variant="primary" disabled={actions.busy} onClick={() => confirm({
              label: t('staffNetwork.activate'), message: t('staffNetwork.confirm.activatePrice'), run: () => actions.activate(id),
            })}>{t('staffNetwork.activate')}</Button>
            <Button size="sm" disabled={actions.busy} onClick={() => confirm({
              label: t('staffNetwork.deleteDraft'), message: t('staffNetwork.confirm.deleteDraft'), danger: true,
              run: async () => {
                if (!await actions.remove(id)) return false
                await navigate('/admin/pricing')
                return true
              },
            })}>{t('staffNetwork.deleteDraft')}</Button>
          </>}
          {list.status === 'ACTIVE' && <Button size="sm" disabled={actions.busy} onClick={() => confirm({
            label: t('staffNetwork.archive'), message: t('staffNetwork.confirm.archivePrice'), run: () => actions.archive(id),
          })}>{t('staffNetwork.archive')}</Button>}
        </div>
      </div>
    </Card>

    <section className={s.list} aria-label={t('staffNetwork.priceItems')}>
      <header className={s.header}>
        <h2>{t('staffNetwork.priceItems')}</h2>
        {draft && <Button onClick={() => editItem(null)} disabled={actions.busy || !services.length}>{t('staffNetwork.addItem')}</Button>}
      </header>
      {items.length ? items.map(row => <Card key={row.id} title={lookup(options.services, row.billableServiceId, t('staffNetwork.serviceRetained'))}
        actions={row.unitPrice == null ? undefined : <strong>{number.format(row.unitPrice)} {currency}</strong>}>
        <div className={s.details}>
          <dl className={s.rates}>
            <div><dt>{t('staffNetwork.fields.routeId')}</dt><dd>{lookup(options.routes, row.routeId)}</dd></div>
            <div><dt>{t('staffNetwork.fields.vehicleClassId')}</dt><dd>{lookup(options.classes, row.vehicleClassId, t('staffNetwork.classRetained'))}</dd></div>
            <div><dt>{t('staffNetwork.fields.stallTypeCode')}</dt><dd>{lookup(options.stalls, row.stallTypeCode)}</dd></div>
            <div><dt>{t('staffNetwork.kmBand')}</dt><dd>{row.kmFrom == null ? t('staffNetwork.any')
              : t('staffNetwork.kmRange', { from: row.kmFrom, to: row.kmTo ?? t('staffNetwork.noLimit') })}</dd></div>
          </dl>
          {draft && <div className={s.actions}>
            <Button size="sm" disabled={actions.busy} onClick={() => editItem(row)}>{t('staffNetwork.editItem')}</Button>
            <Button size="sm" disabled={actions.busy} onClick={() => {
              const itemId = row.id
              if (itemId != null) confirm({
                label: t('staffNetwork.deleteItem'), message: t('staffNetwork.confirm.deleteItem'), danger: true,
                run: () => actions.removeItem(id, itemId),
              })
            }}>{t('staffNetwork.deleteItem')}</Button>
          </div>}
        </div>
      </Card>) : <PricingEmpty hint={t(!draft ? 'staffNetwork.noPriceItems' : !services.length ? 'staffNetwork.noBillableServices' : 'staffNetwork.emptyPriceItems')} />}
    </section>

    {editing && <PriceListForm list={list} busy={actions.busy} fields={actions.fields} onSave={body => actions.saveList(body, id)} close={() => setEditing(false)} />}
    {item !== undefined && <PriceItemForm item={item} options={options} canChooseClass={canChooseClass} busy={actions.busy}
      fields={actions.fields} onSave={body => actions.saveItem(id, body, item?.id)} close={() => setItem(undefined)} />}
    <PricingConfirm action={confirmation} close={() => confirm(null)} />
  </>
}

export default function PriceListDetailPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  return <PricingLayout title={t('staffNetwork.viewPriceList')} actions={<Button onClick={() => navigate('/admin/pricing')}>{t('staffNetwork.backPrices')}</Button>}>
    <PricingDataBoundary<PricingDetailData>>{data => <PriceListDetail key={data.list?.id} {...data} />}</PricingDataBoundary>
  </PricingLayout>
}
