import { useState } from 'react'
import { useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import { quotationsApi, type Quotation } from '@shared/api/quotations'
import { Badge } from '@shared/ui/Badge'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { money, moment, showDate } from '@backoffice/shared/staffFormat'
import { useStaffAction } from '@backoffice/shared/useStaffAction'
import type { OrderData } from '../loaders'
import { AdjustDialog } from './AdjustDialog'
import s from '@backoffice/shared/StaffPanels.module.css'

const TONE = { DRAFT: 'neutral', SENT: 'warning', ACCEPTED: 'success', REJECTED: 'danger', EXPIRED: 'neutral', SUPERSEDED: 'neutral' } as const

// Lập, điều chỉnh, gửi báo giá. Mọi con số do backend tính.
export function QuotePanel({ data }: { data: OrderData }) {
  const { t } = useTranslation()
  const { revalidate, state } = useRevalidator()
  const { run, busy, error, clearError } = useStaffAction()
  const [dialog, setDialog] = useState<'draft' | 'adjust' | 'send' | null>(null)
  const quoting = data.detail?.booking?.status === 'QUOTING'

  if (!data.quotations) return <Card title={t('mgr.quote.title')}><ErrorState onRetry={revalidate} retrying={state !== 'idle'} /></Card>
  if (!quoting && !data.quotations.length) return null
  const versions = [...data.quotations].sort((a, b) => (b.version ?? 0) - (a.version ?? 0))
  const latest = versions[0] as Quotation | undefined
  const editable = quoting && latest?.status === 'DRAFT'
  const open = (next: 'draft' | 'adjust' | 'send') => { clearError(); setDialog(next) }

  const draft = async () => { setDialog(null); await run(() => quotationsApi.draft(data.id), t('mgr.quote.drafted')) }
  const send = async () => { setDialog(null); await run(() => quotationsApi.send(latest!.id!), t('mgr.quote.sent')) }
  const adjust = async (amount: number, reason: string) => {
    if (await run(() => quotationsApi.adjust(latest!.id!, amount, reason), t('mgr.quote.adjusted'))) setDialog(null)
  }

  return (
    <Card title={t('mgr.quote.title')}>
      <div className={s.stack}>
        {!latest && <EmptyState icon={<i className="fa-solid fa-file-invoice-dollar" />} title={t('mgr.quote.empty')} description={t('mgr.quote.emptyHint')} />}
        {latest && <QuoteSheet quote={latest} />}
        {error && dialog !== 'adjust' && <p className={s.error} role="alert">{error}</p>}
        {quoting && (
          <div className={s.row}>
            {editable && <Button variant="primary" loading={busy} onClick={() => open('send')}>{t('mgr.quote.send')}</Button>}
            {editable && <Button disabled={busy} onClick={() => open('adjust')}>{t('mgr.quote.adjust')}</Button>}
            <Button variant={editable ? 'ghost' : 'primary'} disabled={busy} onClick={() => latest ? open('draft') : void draft()}>{t(latest ? 'mgr.quote.redraft' : 'mgr.quote.draft')}</Button>
          </div>
        )}
        {versions.length > 1 && (
          <details>
            <summary>{t('mgr.quote.history')}</summary>
            <ul className={s.items}>
              {versions.slice(1).map(quote => (
                <li key={quote.id} className={s.item}>
                  <div>{t('mgr.quote.version', { version: quote.version })}</div>
                  <span className={s.amount}>{money(quote.totalAmount, quote.currencyCode)}</span>
                  {quote.status && <Badge tone={TONE[quote.status]}>{quote.status === 'SENT' ? t('mgr.quote.waitingCustomer') : t(`myOrders.quote.status.${quote.status}`)}</Badge>}
                </li>
              ))}
            </ul>
          </details>
        )}
      </div>
      {dialog === 'draft' && <ConfirmDialog title={t('mgr.quote.redraftTitle')} message={t('mgr.quote.redraftHint')} confirmLabel={t('mgr.quote.redraft')}
        onConfirm={draft} onCancel={() => setDialog(null)} />}
      {dialog === 'send' && latest && <ConfirmDialog title={t('mgr.quote.sendTitle')}
        message={t('mgr.quote.sendHint', { total: money(latest.totalAmount, latest.currencyCode), deposit: money(latest.depositAmount, latest.currencyCode) })}
        confirmLabel={t('mgr.quote.send')} onConfirm={send} onCancel={() => setDialog(null)} />}
      {dialog === 'adjust' && <AdjustDialog busy={busy} error={error} onSave={adjust} onCancel={() => setDialog(null)} />}
    </Card>
  )
}

function QuoteSheet({ quote }: { quote: Quotation }) {
  const { t } = useTranslation()
  const currency = quote.currencyCode
  return (
    <>
      <div className={s.between}>
        <strong>{t('mgr.quote.version', { version: quote.version })}</strong>
        {quote.status && <Badge tone={TONE[quote.status]}>{quote.status === 'SENT' ? t('mgr.quote.waitingCustomer') : t(`myOrders.quote.status.${quote.status}`)}</Badge>}
      </div>
      <dl className={s.facts}>
        {quote.pickupDate && <div><dt>{t('myOrders.quote.pickup')}</dt><dd>{showDate(quote.pickupDate)}</dd></div>}
        {quote.departureDate && <div><dt>{t('myOrders.quote.departure')}</dt><dd>{showDate(quote.departureDate)}</dd></div>}
        {quote.estimatedDeliveryDate && <div><dt>{t('myOrders.quote.delivery')}</dt><dd>{showDate(quote.estimatedDeliveryDate)}</dd></div>}
        {quote.expiresAt && <div><dt>{t('myOrders.quote.expires')}</dt><dd>{moment(quote.expiresAt)}</dd></div>}
      </dl>
      <table className={s.lines}>
        <thead><tr><th scope="col">{t('myOrders.quote.item')}</th><th scope="col">{t('myOrders.quote.amount')}</th></tr></thead>
        <tbody>
          {(quote.lines ?? []).map(line => (
            <tr key={line.lineNumber}>
              <td>{line.description}{line.quantity != null && line.quantity !== 1 && <> × {line.quantity}</>}{line.passThrough && <> <Badge>{t('myOrders.quote.passThrough')}</Badge></>}</td>
              <td>{money(line.amount, currency)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr><td>{t('myOrders.quote.total')}</td><td>{money(quote.totalAmount, currency)}</td></tr>
          <tr><td>{t('mgr.quote.passThrough')}</td><td>{money(quote.passThroughAmount, currency)}</td></tr>
          <tr><td>{t('myOrders.quote.deposit')}</td><td>{money(quote.depositAmount, currency)}</td></tr>
        </tfoot>
      </table>
    </>
  )
}
