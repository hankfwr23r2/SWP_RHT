import { useState } from 'react'
import { useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import { settlementApi, type CostItem } from '@shared/api/settlement'
import { Badge } from '@shared/ui/Badge'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { ErrorState } from '@shared/ui/ErrorState'
import { money, moment } from '@backoffice/shared/staffFormat'
import { useStaffAction } from '@backoffice/shared/useStaffAction'
import type { OrderData } from '../loaders'
import s from '@backoffice/shared/StaffPanels.module.css'

// Quyết toán: duyệt từng chi phí, lập khoản thu thêm cho khách, đóng đơn
export function SettlementPanel({ data }: { data: OrderData }) {
  const { t } = useTranslation()
  const { revalidate, state } = useRevalidator()
  const { run, busy, error } = useStaffAction()
  const [chosen, setChosen] = useState<number[]>([])
  const [dialog, setDialog] = useState<'charge' | 'complete' | null>(null)
  const settlement = data.settlement

  if (!settlement) return <Card title={t('mgr.settle.title')}><ErrorState onRetry={revalidate} retrying={state !== 'idle'} /></Card>
  const costs = settlement.costs ?? []
  const chargeable = (cost: CostItem) => cost.id != null && (settlement.chargeableCostIds ?? []).includes(cost.id)
  const picked = chosen.filter(id => costs.some(cost => cost.id === id && chargeable(cost)))
  const toggle = (id: number) => setChosen(picked.includes(id) ? picked.filter(item => item !== id) : [...picked, id])
  const approve = (id: number) => run(() => settlementApi.approveCost(id), t('mgr.settle.approved'))
  const charge = async () => {
    setDialog(null)
    if (await run(() => settlementApi.extraCharge(data.id, picked), t('mgr.settle.charged'))) setChosen([])
  }
  const complete = async () => { setDialog(null); await run(() => settlementApi.complete(data.id), t('mgr.settle.completed')) }

  return (
    <Card title={t('mgr.settle.title')}>
      <div className={s.stack}>
        <dl className={s.facts}>
          <div><dt>{t('mgr.settle.quotation')}</dt><dd>{money(settlement.quotationTotal, settlement.currencyCode)}</dd></div>
          <div><dt>{t('mgr.settle.costs')}</dt><dd>{money(settlement.approvedCustomerCosts, settlement.currencyCode)}</dd></div>
          <div><dt>{t('mgr.settle.outstanding')}</dt><dd><strong>{money(settlement.outstanding, settlement.currencyCode)}</strong></dd></div>
        </dl>
        {settlement.hasOpenIncident && <p className={s.alert}>{t('mgr.settle.openIncident')}</p>}
        {!costs.length && <p className={s.hint}>{t('mgr.settle.noCosts')}</p>}
        <ul className={s.items}>
          {costs.map(cost => (
            <li key={cost.id} className={s.item}>
              <div>
                {chargeable(cost) ? (
                  <label className={s.check}>
                    <input type="checkbox" checked={picked.includes(cost.id!)} onChange={() => toggle(cost.id!)} />
                    <strong>{cost.category && t(`mgr.settle.category.${cost.category}`)}</strong>
                  </label>
                ) : <strong>{cost.category && t(`mgr.settle.category.${cost.category}`)}</strong>}
                <p className={s.hint}>{[cost.description, cost.payerType && t(`mgr.settle.payer.${cost.payerType}`), cost.recordedByName, cost.incurredAt && moment(cost.incurredAt)].filter(Boolean).join(' · ')}</p>
                {cost.payerType === 'CUSTOMER' && !cost.customerConsulted && <p className={s.error}>{t('mgr.settle.notConsulted')}</p>}
              </div>
              <span className={s.amount}>{money(cost.amount, cost.currencyCode)}</span>
              {cost.extraChargePaymentId != null ? <Badge tone="info">{t('mgr.settle.inCharge')}</Badge>
                : cost.approvedAt ? <Badge tone="success">{t('mgr.settle.isApproved')}</Badge>
                  : <Button size="sm" loading={busy} onClick={() => approve(cost.id!)}>{t('mgr.settle.approve')}</Button>}
            </li>
          ))}
        </ul>
        {error && <p className={s.error} role="alert">{error}</p>}
        {(picked.length > 0 || settlement.status === 'DELIVERED') && (
          <div className={s.row}>
            <Button variant="primary" disabled={!picked.length || busy} onClick={() => setDialog('charge')}>{t('mgr.settle.charge')}</Button>
            {settlement.status === 'DELIVERED' && <Button disabled={busy || settlement.outstanding !== 0} onClick={() => setDialog('complete')}>{t('mgr.settle.complete')}</Button>}
          </div>
        )}
      </div>
      {dialog === 'charge' && <ConfirmDialog title={t('mgr.settle.chargeTitle')} message={t('mgr.settle.chargeHint', { count: picked.length })}
        confirmLabel={t('mgr.settle.charge')} onConfirm={charge} onCancel={() => setDialog(null)} />}
      {dialog === 'complete' && <ConfirmDialog title={t('mgr.settle.completeTitle')} message={t('mgr.settle.completeHint')}
        confirmLabel={t('mgr.settle.complete')} onConfirm={complete} onCancel={() => setDialog(null)} />}
    </Card>
  )
}
