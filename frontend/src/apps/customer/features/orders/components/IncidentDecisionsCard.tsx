import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Badge } from '@shared/ui/Badge'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Skeleton } from '@shared/ui/Skeleton'
import { money, moment } from '../format'
import { useIncidentDecisions, type Decision } from '../useIncidentDecisions'
import s from '../Orders.module.css'

export function IncidentDecisionsCard({ bookingId }: { bookingId: number }) {
  const { t } = useTranslation()
  const [decision, setDecision] = useState<Decision>()
  const { data, loaded, error, reload, respond, busy, actionError } = useIncidentDecisions(bookingId)
  const agree = t('incidentDecisions.agree')
  const decline = t('incidentDecisions.decline')

  return (
    <Card title={t('incidentDecisions.title')}>
      {!loaded && <Skeleton variant="block" lines={3} />}
      {error && <ErrorState onRetry={reload} />}
      {data && data.costs.length === 0 && data.plans.length === 0 && <EmptyState title={t('incidentDecisions.empty')} description={t('incidentDecisions.emptyHint')} />}
      {data && <div className={s.stack}>
        {data.costs.map(cost => (
          <div key={cost.id}>
            <p>{cost.description} ? {money(cost.amount, cost.currencyCode)}</p>
            {cost.customerAgreed != null && <Badge>{t(cost.customerAgreed ? 'incidentDecisions.agreed' : 'incidentDecisions.declined')}</Badge>}
            {cost.customerResponseDueAt && cost.customerAgreed == null && !cost.approvedAt && cost.id != null && (
              <>
                <p>{t('incidentDecisions.deadline')} ? {moment(cost.customerResponseDueAt)}</p>
                <div className={s.row}>
                  <Button disabled={busy} onClick={() => setDecision({ id: cost.id!, kind: 'cost', agree: true })}>{agree}</Button>
                  <Button disabled={busy} onClick={() => setDecision({ id: cost.id!, kind: 'cost', agree: false })}>{decline}</Button>
                </div>
              </>
            )}
          </div>
        ))}
        {data.plans.map(plan => (
          <div key={plan.id}>
            <p>{plan.instructions} ? {money(plan.estimatedCost, 'VND')}</p>
            {plan.customerAgreedAt ? <Badge>{t('incidentDecisions.agreed')}</Badge> : plan.id != null &&
              <Button disabled={busy} onClick={() => setDecision({ id: plan.id!, kind: 'plan', agree: true })}>{agree}</Button>}
          </div>
        ))}
      </div>}
      {actionError && <p role="alert" className={s.error}>{actionError}</p>}
      {decision && <ConfirmDialog title={decision.agree ? agree : decline}
        message={t('incidentDecisions.consequence')} confirmLabel={decision.agree ? agree : decline}
        onConfirm={async () => { if (await respond(decision, t('incidentDecisions.saved'))) setDecision(undefined) }}
        onCancel={() => setDecision(undefined)} />}
    </Card>
  )
}
