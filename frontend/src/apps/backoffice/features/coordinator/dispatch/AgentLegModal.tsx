// Chặng do đại lý nước ngoài chở (GĐ7.4–7.5): điều phối lập chặng và nhập hộ mốc đại lý báo. Đại lý không có tài khoản.
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi, type DispatchBooking, type DispatchHorse, type HorseLeg } from '@shared/api/dispatch'
import { useLoad } from '@shared/hooks/useLoad'
import { formatDateTime } from '@shared/lib/format'
import { Badge } from '@shared/ui/Badge'
import { Button } from '@shared/ui/Button'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { useToast } from '@shared/ui/toast'
import { activeLegs, localToIso } from './dispatchModel'
import s from './Dispatch.module.css'

interface Props { booking: DispatchBooking; horse: DispatchHorse; onClose: () => void; onChanged: () => void }

export function AgentLegModal({ booking, horse, onClose, onChanged }: Props) {
  const { t } = useTranslation()
  const toast = useToast()
  const isImport = booking.bookingType === 'IMPORT'
  const [agentId, setAgentId] = useState('')
  const [gateId, setGateId] = useState('')
  const [start, setStart] = useState('')
  const [end, setEnd] = useState('')
  const [busy, setBusy] = useState(false)
  const { data, loaded, error, reload } = useLoad(async () => {
    const [parties, locations] = await Promise.all([dispatchApi.parties(), dispatchApi.locations()])
    return {
      agents: parties.filter(p => (p.roles ?? []).some(r => r.roleType === 'DEST_AGENT')),
      gates: locations.filter(l => l.locationType === 'BORDER_GATE'),
    }
  }, [], { toastOnError: false })
  const agentLegs = activeLegs(horse).filter(leg => leg.agentName)

  const run = async (action: () => Promise<unknown>, done: string) => {
    setBusy(true)
    try {
      await action()
      toast(done, 'success')
      onChanged()
      return true
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e), 'error')
      return false
    } finally {
      setBusy(false)
    }
  }

  const legActions = (leg: HorseLeg) => (
    <span className={s.legs}>
      {leg.status === 'PLANNED' && leg.legType === 'PICKUP' && (
        <Button size="sm" loading={busy} onClick={() => run(() => dispatchApi.startAgentLeg(leg.id ?? 0), t('dispatch.agent.started'))}>{t('dispatch.agent.start')}</Button>
      )}
      {leg.status === 'IN_PROGRESS' && leg.legType === 'DELIVERY' && (
        <Button size="sm" variant="primary" loading={busy}
          onClick={() => run(() => dispatchApi.completeAgentLeg(leg.id ?? 0), t('dispatch.agent.completed'))}>{t('dispatch.agent.complete')}</Button>
      )}
      {leg.status === 'PLANNED' && (
        <Button size="sm" variant="ghost" loading={busy} onClick={() => run(() => dispatchApi.cancelAgentLeg(leg.id ?? 0), t('dispatch.agent.cancelled'))}>{t('dispatch.agent.cancel')}</Button>
      )}
    </span>
  )

  const canCreate = Boolean(agentId && start && end && (!isImport || gateId))
  return (
    <Modal title={t('dispatch.agent.title', { horse: horse.horseName, code: booking.code })} onClose={onClose}
      footer={(
        <>
          <Button onClick={onClose}>{t('ui.close')}</Button>
          <Button variant="primary" disabled={!canCreate} loading={busy}
            onClick={() => run(() => dispatchApi.createAgentLeg(horse.bookingHorseId ?? 0, {
              agentPartyId: Number(agentId), gateLocationId: isImport ? Number(gateId) : undefined,
              plannedStartAt: localToIso(start), plannedEndAt: localToIso(end),
            }), t('dispatch.agent.created')).then(ok => { if (ok) { setStart(''); setEnd('') } })}>
            {t('dispatch.agent.create')}
          </Button>
        </>
      )}>
      <div className={s.list}>
        <p className={s.muted}>{t(isImport ? 'dispatch.agent.importHint' : 'dispatch.agent.exportHint')}</p>
        {agentLegs.length > 0 && (
          <ul className={s.list} aria-label={t('dispatch.agent.legs')}>
            {agentLegs.map(leg => (
              <li key={leg.id} className={s.item}>
                <span>{`${leg.agentName} · ${leg.fromName} → ${leg.toName}`}</span>
                <Badge tone={leg.status === 'COMPLETED' ? 'success' : leg.status === 'IN_PROGRESS' ? 'info' : 'neutral'}>{t(`dispatch.agent.status.${leg.status}`)}</Badge>
                <span className={s.muted}>{leg.plannedStartAt ? formatDateTime(new Date(leg.plannedStartAt)) : ''}</span>
                {legActions(leg)}
              </li>
            ))}
          </ul>
        )}
        {!loaded ? <Skeleton variant="block" lines={2} /> : error || !data ? <ErrorState title={t('dispatch.loadError')} message={error?.message} onRetry={reload} /> : (
          <div className={s.form}>
            <Field label={t('dispatch.agent.agent')} required>
              {c => <Select {...c} value={agentId} onChange={setAgentId} placeholder={t('dispatch.detail.choose')}
                options={data.agents.map(p => ({ value: String(p.id), label: p.displayName ?? '' }))} />}
            </Field>
            {isImport && (
              <Field label={t('dispatch.agent.gate')} required>
                {c => <Select {...c} value={gateId} onChange={setGateId} placeholder={t('dispatch.detail.choose')}
                  options={data.gates.map(l => ({ value: String(l.id), label: l.name ?? '' }))} />}
              </Field>
            )}
            <Field label={t('dispatch.agent.plannedStart')} required>
              {c => <Input {...c} type="datetime-local" value={start} onChange={e => setStart(e.target.value)} />}
            </Field>
            <Field label={t('dispatch.agent.plannedEnd')} required>
              {c => <Input {...c} type="datetime-local" value={end} onChange={e => setEnd(e.target.value)} />}
            </Field>
          </div>
        )}
      </div>
    </Modal>
  )
}
