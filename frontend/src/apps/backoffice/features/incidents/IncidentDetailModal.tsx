// Chi tiết sự cố: phương án theo phiên bản, chi phí. Điều phối lập phương án, xử lý xong, đóng; quản lý duyệt, từ chối, duyệt chi phí
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi } from '@shared/api/dispatch'
import { incidentsApi, type Incident, type ResolutionType } from '@shared/api/incidentOps'
import { useLoad } from '@shared/hooks/useLoad'
import { formatVND } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { Textarea } from '@shared/ui/Textarea'
import { useToast } from '@shared/ui/toast'
import { EvidencePanel } from '@backoffice/shared/EvidencePanel'
import { IncidentCostForm } from '@backoffice/shared/IncidentCostForm'
import { IncidentCostControls } from './IncidentCostControls'
import { IncidentPlanApproval } from './IncidentPlanApproval'
import { IncidentEvidenceSection } from './IncidentEvidenceSection'
import { PlanRejectDialog } from './PlanRejectDialog'
import s from './Incidents.module.css'

const RESOLUTIONS: ResolutionType[] = ['CONTINUE', 'VET_TREATMENT', 'STABLE_NEARBY', 'TRANSFER_VEHICLE', 'RETURN_TO_HUB', 'CANCEL']

interface Props { incidentId: number; role: 'coordinator' | 'manager'; onClose: () => void; onChanged: () => void }

export function IncidentDetailModal({ incidentId, role, onClose, onChanged }: Props) {
  const { t } = useTranslation()
  const toast = useToast()
  const [busy, setBusy] = useState(false)
  const [resolution, setResolution] = useState<ResolutionType>('CONTINUE')
  const [instructions, setInstructions] = useState('')
  const [estimatedCost, setEstimatedCost] = useState('')
  const [handler, setHandler] = useState('')
  const [rejecting, setRejecting] = useState<{ id: number; version: number }>()
  const [vehicleId, setVehicleId] = useState('')
  const { data, loaded, error, reload } = useLoad(async () => {
    const [incident, costs, evidence] = await Promise.all([incidentsApi.incident(incidentId), incidentsApi.costs(incidentId), incidentsApi.photos(incidentId)])
    return { incident, costs, evidence }
  }, [incidentId], { toastOnError: false })
  const { data: parties } = useLoad(async () => (role === 'coordinator' ? incidentsApi.parties() : []), [role])
  const { data: vehicles } = useLoad(async () => (role === 'coordinator' ? dispatchApi.vehicles() : []), [role])

  const run = async (action: () => Promise<unknown>, done: string) => {
    setBusy(true)
    try {
      await action()
      toast(done, 'success')
      onChanged()
      await reload()
      return true
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e), 'error')
      return false
    } finally {
      setBusy(false)
    }
  }

  if (!loaded) return <Modal title={t('incidents.view')} onClose={onClose} wide><Skeleton variant="block" lines={4} /></Modal>
  if (error || !data) return <Modal title={t('incidents.view')} onClose={onClose} wide><ErrorState message={error?.message} onRetry={reload} /></Modal>
  const incident: Incident = data.incident
  const plans = incident.plans ?? []
  const latest = plans[plans.length - 1]
  const canReturn = incident.incidentType !== 'HANDOVER_TIMEOUT'
  const open = incident.status !== 'RESOLVED' && incident.status !== 'CLOSED'

  return (
    <Modal wide title={incident.title} subtitle={`${t(`incidents.type.${incident.incidentType}`)} · ${t(`incidents.cause.${incident.confirmedCauseType ?? incident.causeType}`)}`} onClose={onClose}
      footer={role === 'coordinator' && (
        <>
          {incident.status === 'PLAN_ACTIVE' && <Button variant="primary" loading={busy} onClick={() => run(() => incidentsApi.resolve(incidentId), t('incidents.done.resolved'))}>{t('incidents.resolve')}</Button>}
          {incident.status === 'RESOLVED' && <Button loading={busy} onClick={() => run(() => incidentsApi.close(incidentId), t('incidents.done.closed'))}>{t('incidents.close')}</Button>}
        </>
      )}>
      <div className={s.section}>
        <div className={s.row}>
          <StatusBadge tone={open ? 'warning' : 'success'} label={t(`incidents.status.${incident.status}`)} />
          {incident.departureId && <p>{t('incidents.departure', {
            route: incident.departureRouteName, date: incident.departureDate,
          })}</p>}
          {incident.requiresFit && <StatusBadge tone={incident.fitConfirmedAt ? 'success' : 'neutral'}
            label={incident.fitConfirmedAt ? t('incidents.fitConfirmed') : t('incidents.notFit')} />}
        </div>
        <p className={s.muted}>{[incident.vehicleTripCode, incident.horseName, incident.bookingCode].filter(Boolean).join(' · ')}</p>
        <p>{incident.description}</p>
        <IncidentEvidenceSection evidence={data.evidence} busy={busy}
          canConfirm={role === 'coordinator' && incident.status === 'PLAN_ACTIVE' && !!incident.requiresFit && !incident.fitConfirmedAt}
          onConfirm={() => run(() => incidentsApi.confirmFit(incidentId), t('myTrips.run.incident.fitDone'))}
          onUpload={role === 'coordinator' && incident.status !== 'CLOSED' ? async (file, type) => {
            const evidence = await incidentsApi.attachPhoto(incidentId, file, undefined, type)
            await reload()
            return evidence
          } : undefined} />
      </div>

      <section className={s.section} aria-labelledby="incident-plans">
        <h3 id="incident-plans" className={s.title}>{t('incidents.plans')}</h3>
        {plans.length ? (
          <ol className={s.list}>
            {plans.map(plan => (
              <li key={plan.id} className={s.item}>
                <div className={s.row}>
                  <strong>{t('incidents.version', { n: plan.version })} · {t(`incidents.resolution.${plan.resolutionType}`)}</strong>
                  <StatusBadge tone={plan.status === 'APPROVED' || plan.status === 'DONE' ? 'success' : plan.status === 'REJECTED' ? 'danger' : 'neutral'}
                    label={t(`incidents.planStatus.${plan.status}`)} />
                </div>
                <p className={s.muted}>{plan.instructions}{plan.handlerPartyName ? ` · ${plan.handlerPartyName}` : ''}{plan.estimatedCost ? ` · ${formatVND(plan.estimatedCost)}` : ''}</p>
                {plan.rejectionReason && <p className={s.muted}>{plan.rejectionReason}</p>}
                {role === 'manager' && plan.status === 'PROPOSED' && plan.id === latest?.id && (
                  <div className={s.row}>
                    <IncidentPlanApproval incident={incident} busy={busy} onApprove={cause =>
                      run(() => incidentsApi.approve(plan.id ?? 0, cause), t('incidents.done.approved'))} />
                    <Button variant="danger" onClick={() => setRejecting({ id: plan.id ?? 0, version: plan.version ?? 0 })}>{t('incidents.reject')}</Button>
                  </div>
                )}
              </li>
            ))}
          </ol>
        ) : <p className={s.muted}>{t('incidents.noPlans')}</p>}
        {/* Thực hiện phương án đã duyệt (B4): đổi xe cho lượt đang chạy, đưa ngựa về hub */}
        {role === 'coordinator' && latest?.status === 'APPROVED' && latest.resolutionType === 'TRANSFER_VEHICLE' && incident.vehicleTripId && (
          <div className={s.form}>
            <p className={s.muted}>{t('incidents.swapVehicleHint')}</p>
            <Field label={t('incidents.replacementVehicle')}>
              {c => <Select {...c} value={vehicleId} onChange={setVehicleId} placeholder={t('incidents.replacementVehicle')}
                options={(vehicles ?? []).map(v => ({ value: `${v.id}`, label: v.licensePlate ?? '', disabled: v.status !== 'ACTIVE' }))} />}
            </Field>
            <Button variant="primary" disabled={!vehicleId} loading={busy}
              onClick={() => run(() => incidentsApi.swapVehicle(incidentId, Number(vehicleId)), t('incidents.done.swapped'))}>{t('incidents.swapVehicle')}</Button>
          </div>
        )}
        {role === 'coordinator' && canReturn && latest?.status === 'APPROVED' && latest.resolutionType === 'RETURN_TO_HUB' && (
          <div className={s.form}>
            <p className={s.muted}>{t('incidents.returnToHubHint')}</p>
            <Button variant="primary" loading={busy} onClick={() => run(() => incidentsApi.returnToHub(incidentId), t('incidents.done.returned'))}>{t('incidents.returnToHub')}</Button>
          </div>
        )}
        {role === 'coordinator' && open && (
          <div className={s.form}>
            <Field label={t('incidents.resolutionType')}>
              {c => <Select {...c} value={resolution} onChange={value => setResolution(value as ResolutionType)}
                options={RESOLUTIONS.filter(r => canReturn || r !== 'RETURN_TO_HUB').map(r => ({ value: r, label: t(`incidents.resolution.${r}`) }))} />}
            </Field>
            <Field label={t('incidents.handler')}>
              {c => <Select {...c} value={handler} onChange={setHandler} placeholder={t('incidents.noHandler')}
                options={[{ value: '', label: t('incidents.noHandler') }, ...(parties ?? []).map(p => ({ value: `${p.id}`, label: p.displayName ?? '' }))]} />}
            </Field>
            <Field label={t('incidents.estimatedCost')}>
              {c => <Input {...c} inputMode="numeric" value={estimatedCost} onChange={e => setEstimatedCost(e.target.value)} />}
            </Field>
            <Field label={t('incidents.instructions')} required>
              {c => <Textarea {...c} value={instructions} onChange={e => setInstructions(e.target.value)} />}
            </Field>
            <Button variant="primary" disabled={!instructions.trim()} loading={busy}
              onClick={() => run(() => incidentsApi.propose(incidentId, {
                resolutionType: resolution, instructions: instructions.trim(),
                estimatedCost: estimatedCost ? Number(estimatedCost) : undefined, handlerPartyId: handler ? Number(handler) : undefined,
              }), t('incidents.done.proposed')).then(ok => { if (ok) setInstructions('') })}>
              {t('incidents.propose')}
            </Button>
          </div>
        )}
      </section>

      <section className={s.section} aria-labelledby="incident-costs">
        <h3 id="incident-costs" className={s.title}>{t('incidents.costs')}</h3>
        {role === 'coordinator' && incident.status !== 'CLOSED' && (
          <IncidentCostForm incidentId={incidentId} busy={busy} className={s.form}
            onSave={draft => run(() => incidentsApi.recordCost(draft), t('myTrips.run.incident.costSaved'))} />
        )}
        {data.costs.length ? (
          <ul className={s.list}>
            {data.costs.map(cost => (
              <li key={cost.id} className={s.item}>
                <div className={s.row}>
                  <strong>{t(`incidents.category.${cost.category}`)} · {formatVND(cost.amount ?? 0)}</strong>
                  <StatusBadge tone={cost.payerType === 'COMPANY' ? 'neutral' : 'info'} label={t(`incidents.payer.${cost.payerType}`)} />
                  {cost.payerType !== 'COMPANY' && <StatusBadge tone={cost.customerConsulted ? 'success' : 'warning'}
                    label={cost.customerConsulted ? t('incidents.consulted') : t('incidents.notConsulted')} />}
                  {!cost.approvedAt && !incident.causeConfirmedAt && incident.incidentType !== 'HANDOVER_TIMEOUT' &&
                    <StatusBadge tone="neutral" label={t('incidentDecisions.provisional')} />}
                  {cost.approvedAt && <StatusBadge tone="success" label={t('incidents.approved')} />}
                </div>
                <p className={s.muted}>{cost.description}</p>
                <EvidencePanel title={t('incidents.receipts')} load={() => incidentsApi.receipts(cost.id ?? 0)}
                  upload={role === 'coordinator' && !cost.approvedAt ? file => incidentsApi.attachReceipt(cost.id ?? 0, file) : undefined} />
                <IncidentCostControls key={cost.id} cost={cost} role={role} busy={busy}
                  onApprove={reason => run(() => incidentsApi.approveCost(cost.id ?? 0, reason), t('incidents.done.costApproved'))}
                  onPhoneConsent={note => run(() => incidentsApi.phoneConsent(cost.id ?? 0, note), t('incidentDecisions.saved'))} />
              </li>
            ))}
          </ul>
        ) : <p className={s.muted}>{t('incidents.noCosts')}</p>}
      </section>

      {rejecting && <PlanRejectDialog version={rejecting.version} busy={busy} onClose={() => setRejecting(undefined)}
        onReject={async reason => {
          if (await run(() => incidentsApi.reject(rejecting.id, reason), t('incidents.done.rejected'))) setRejecting(undefined)
        }} />}
    </Modal>
  )
}
