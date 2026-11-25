import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { IncidentPlan } from '@shared/api/incidentOps'
import { tripExecutionApi } from '@shared/api/tripExecution'
import { useLoad } from '@shared/hooks/useLoad'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import s from './MyTrips.module.css'

interface Props {
  busy: boolean
  plan?: IncidentPlan
  canOpen: boolean
  onClose: () => void
  onOpen: (partyId: number, planId?: number) => Promise<unknown>
}

function useStablePartners(fixedPartner: boolean) {
  return useLoad(() => fixedPartner ? Promise.resolve([]) : tripExecutionApi.stablePartners(), [fixedPartner], { toastOnError: false })
}

export function StableHandoverDialog({ busy, plan, canOpen, onClose, onOpen }: Props) {
  const { t } = useTranslation()
  const [selected, setSelected] = useState('')
  const { data: partners, loaded, error, reload } = useStablePartners(Boolean(plan))
  const partyId = plan ? plan.handlerPartyId : Number(selected)
  return (
    <Modal title={t(plan ? 'myTrips.run.stayIn.incidentTitle' : 'myTrips.run.stayIn.title')} onClose={onClose} footer={(
      <>
        <Button onClick={onClose} disabled={busy}>{t('ui.cancel')}</Button>
        <Button variant="primary" disabled={!partyId || !canOpen} loading={busy}
          onClick={() => { if (partyId) void onOpen(partyId, plan?.id) }}>
          {t(plan ? 'myTrips.run.stayIn.incidentTitle' : 'myTrips.run.handover.STAY_IN')}
        </Button>
      </>
    )}>
      {plan ? <p>{t('myTrips.run.stayIn.approvedPartner', { name: plan.handlerPartyName })}</p> : (
        <>
          <p className={s.muted}>{t('myTrips.run.stayIn.hint')}</p>
          {!loaded ? <Skeleton variant="block" lines={2} /> : error ? <ErrorState message={error.message} onRetry={reload} />
            : !partners?.length ? <EmptyState title={t('myTrips.run.stayIn.noPartners')} description={t('myTrips.run.stayIn.noPartnersHint')} /> : (
            <Field label={t('myTrips.run.stayIn.partner')} required>
              {c => <Select {...c} value={selected} onChange={setSelected} placeholder={t('myTrips.run.stayIn.choose')}
                options={partners.map(p => ({ value: String(p.id), label: p.address ? `${p.displayName} · ${p.address}` : p.displayName ?? '' }))} />}
            </Field>
          )}
        </>
      )}
    </Modal>
  )
}
