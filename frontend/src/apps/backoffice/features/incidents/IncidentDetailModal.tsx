import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { incidentsApi } from '@shared/api/incidentOps'
import { useAutoRefresh, useLoad } from '@shared/hooks/useLoad'
import { formatDateTime, formatVND } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { ErrorState } from '@shared/ui/ErrorState'
import { Modal } from '@shared/ui/Modal'
import { Skeleton } from '@shared/ui/Skeleton'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { useToast } from '@shared/ui/toast'
import { EvidencePanel } from '@backoffice/shared/EvidencePanel'
import { IncidentCostForm } from '@backoffice/shared/IncidentCostForm'
import { IncidentSeverityBadge } from '@backoffice/shared/IncidentSeverity'
import { VetResultControl } from '@backoffice/shared/VetResultControl'
import { IncidentCostControls } from './IncidentCostControls'
import { IncidentPlanApproval } from './IncidentPlanApproval'
import { IncidentProposalForm } from './IncidentProposalForm'
import { IncidentEvidenceSection } from './IncidentEvidenceSection'
import { IncidentSeverityControls } from './IncidentSeverityControls'
import { PlanRejectDialog } from './PlanRejectDialog'
import { VehicleSwapControls } from './VehicleSwapControls'
import s from './Incidents.module.css'

interface Props { incidentId: number; role: 'coordinator' | 'manager'; onClose: () => void; onChanged: () => void }
export function IncidentDetailModal({ incidentId, role, onClose, onChanged }: Props) {
  const { t } = useTranslation()
  const toast = useToast()
  const [busy, setBusy] = useState(false)
  const [cancelPlan, setCancelPlan] = useState<number>()
  const [rejecting, setRejecting] = useState<{ id: number; version: number }>()
  const { data, loaded, error, reload } = useLoad(async () => {
    const [incident, costs, evidence] = await Promise.all([incidentsApi.incident(incidentId), incidentsApi.costs(incidentId), incidentsApi.photos(incidentId)])
    return { incident, costs, evidence }
  }, [incidentId], { toastOnError: false })
  const { data: parties } = useLoad(async () => (role === 'coordinator' ? incidentsApi.parties() : []), [role])
  useAutoRefresh(reload)

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
  const { incident } = data
  const plans = incident.plans ?? []
  const latest = plans.at(-1)
  const canReturn = incident.incidentType !== 'HANDOVER_TIMEOUT'
  const open = incident.status !== 'RESOLVED' && incident.status !== 'CLOSED'
  const canExecute = role === 'coordinator' && incident.status === 'PLAN_ACTIVE' && latest?.status === 'APPROVED'

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
          <IncidentSeverityBadge severity={incident.severity} />
          {incident.departureId && <p>{t('incidents.departure', {
            route: incident.departureRouteName, date: incident.departureDate,
          })}</p>}
          {incident.requiresFit && <StatusBadge tone={incident.fitConfirmedAt ? 'success' : 'neutral'}
            label={incident.fitConfirmedAt ? t('incidents.fitConfirmed') : t('incidents.notFit')} />}
        </div>
        {incident.restStartedAt && <p className={s.muted}>{t('incidents.restStartedAt', { time: formatDateTime(new Date(incident.restStartedAt)) })}</p>}
        <p className={s.muted}>{[incident.vehicleTripCode, incident.horseName, incident.bookingCode].filter(Boolean).join(' · ')}</p>
        <p>{incident.description}</p>
        <IncidentSeverityControls incidentId={incidentId} severity={incident.severity} busy={busy}
          canUnlock={role === 'coordinator' && incident.blocksTrip} onChanged={async () => { onChanged(); await reload() }} />
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
        {role === 'coordinator' && open && latest?.status === 'APPROVED' && latest.resolutionType === 'TRANSFER_VEHICLE' && incident.vehicleTripId && (
          <VehicleSwapControls busy={busy} hasRecord={data.evidence.some(file => file.documentTypeCode === 'VEHICLE_SWAP_RECORD')}
            onSwap={vehicleId => run(() => incidentsApi.swapVehicle(incidentId, vehicleId), t('incidents.done.swapped'))} />
        )}
        {role === 'coordinator' && canReturn && latest?.status === 'APPROVED' && latest.resolutionType === 'RETURN_TO_HUB' && (
          <div className={s.form}>
            <p className={s.muted}>{t('incidents.returnToHubHint')}</p>
            <Button variant="primary" loading={busy} onClick={() => run(() => incidentsApi.returnToHub(incidentId), t('incidents.done.returned'))}>{t('incidents.returnToHub')}</Button>
          </div>
        )}
        {canExecute && latest.resolutionType === 'VET_TREATMENT' && (
          <VetResultControl key={latest.id} incidentId={incidentId} planId={latest.id} partnerName={latest.handlerPartyName} disabled={busy}
            onSaved={async () => { onChanged(); await reload() }} />
        )}
        {canExecute && latest.resolutionType === 'CANCEL' && (
          <Button variant="danger" disabled={busy} onClick={() => setCancelPlan(latest.id)}>{t('incidents.cancelLegs.action')}</Button>
        )}
        {role === 'coordinator' && open && (
          <IncidentProposalForm parties={parties} canReturn={canReturn} busy={busy}
            onPropose={draft => run(() => incidentsApi.propose(incidentId, draft), t('incidents.done.proposed'))} />
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
      {cancelPlan !== undefined && <ConfirmDialog tone="danger" title={t('incidents.cancelLegs.action')} message={t('incidents.cancelLegs.confirm')}
        confirmLabel={t('incidents.cancelLegs.action')} confirmDisabled={busy || !canExecute || latest.id !== cancelPlan}
        onCancel={() => setCancelPlan(undefined)} onConfirm={async () => {
          if (await run(() => incidentsApi.cancelLegs(incidentId, cancelPlan), t('incidents.cancelLegs.saved'))) setCancelPlan(undefined)
        }} />}
    </Modal>
  )
}
