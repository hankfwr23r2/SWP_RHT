import { useState } from 'react'
import { useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import { quotationsApi } from '@shared/api/quotations'
import { currentQuotation } from '@shared/components/orderSteps'
import { Badge } from '@shared/ui/Badge'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { ErrorState } from '@shared/ui/ErrorState'
import { money, moment, showDate } from '../format'
import type { OrderData } from '../loaders'
import { useOrderAction } from '../useOrderAction'
import { ReasonDialog } from './ReasonDialog'
import s from '../Orders.module.css'

export function QuoteCard({ data }: { data: OrderData }) {
  const { t } = useTranslation()
  const { revalidate, state } = useRevalidator()
  const { run, busy, error } = useOrderAction()
  const [dialog, setDialog] = useState<'accept' | 'reject' | null>(null)
  const quote = currentQuotation(data.quotations)

  if (!data.quotations) return <Card title={t('myOrders.quote.title')}><ErrorState onRetry={revalidate} retrying={state !== 'idle'} /></Card>
  if (!quote) return null
  const currency = quote.currencyCode
  const accept = async () => { setDialog(null); await run(() => quotationsApi.accept(quote.id!), t('myOrders.quote.accepted')) }
  const reject = async (reason: string) => {
    if (await run(() => quotationsApi.reject(quote.id!, reason), t('myOrders.quote.rejected'))) setDialog(null)
  }

  return (
    <Card title={t('myOrders.quote.titleVersion', { version: quote.version })}
      actions={quote.status && <Badge tone={quote.status === 'ACCEPTED' ? 'success' : quote.status === 'SENT' ? 'warning' : 'neutral'}>{t(`myOrders.quote.status.${quote.status}`)}</Badge>}>
      <div className={s.stack}>
        <dl className={s.facts}>
          {quote.pickupDate && <div><dt>{t('myOrders.quote.pickup')}</dt><dd>{showDate(quote.pickupDate)}</dd></div>}
          {quote.departureDate && <div><dt>{t('myOrders.quote.departure')}</dt><dd>{showDate(quote.departureDate)}</dd></div>}
          {quote.estimatedDeliveryDate && <div><dt>{t('myOrders.quote.delivery')}</dt><dd>{showDate(quote.estimatedDeliveryDate)}</dd></div>}
          {quote.status === 'SENT' && quote.expiresAt && <div><dt>{t('myOrders.quote.expires')}</dt><dd>{moment(quote.expiresAt)}</dd></div>}
        </dl>
        <table className={s.lines}>
          <thead><tr><th scope="col">{t('myOrders.quote.item')}</th><th scope="col">{t('myOrders.quote.amount')}</th></tr></thead>
          <tbody>
            {(quote.lines ?? []).map(line => (
              <tr key={line.lineNumber}>
                <td>{line.description}{line.passThrough && <> <Badge>{t('myOrders.quote.passThrough')}</Badge></>}</td>
                <td>{money(line.amount, currency)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr><td>{t('myOrders.quote.total')}</td><td>{money(quote.totalAmount, currency)}</td></tr>
            <tr><td>{t('myOrders.quote.deposit')}</td><td>{money(quote.depositAmount, currency)}</td></tr>
          </tfoot>
        </table>
        {error && <p className={s.error} role="alert">{error}</p>}
        {quote.status === 'SENT' && (
          <div className={s.row}>
            <Button variant="primary" loading={busy} onClick={() => setDialog('accept')}>{t('myOrders.quote.accept')}</Button>
            <Button disabled={busy} onClick={() => setDialog('reject')}>{t('myOrders.quote.reject')}</Button>
          </div>
        )}
      </div>
      {dialog === 'accept' && <ConfirmDialog title={t('myOrders.quote.acceptTitle')}
        message={t('myOrders.quote.acceptMessage', { total: money(quote.totalAmount, currency), deposit: money(quote.depositAmount, currency) })}
        confirmLabel={t('myOrders.quote.accept')} onConfirm={accept} onCancel={() => setDialog(null)} />}
      {dialog === 'reject' && <ReasonDialog title={t('myOrders.quote.rejectTitle')} message={t('myOrders.quote.rejectMessage')}
        confirmLabel={t('myOrders.quote.reject')} busy={busy} error={error} onConfirm={reject} onCancel={() => setDialog(null)} />}
    </Card>
  )
}
