// Sự cố của lượt xe nhìn từ tổ xe: báo sự cố, xác nhận ngựa đủ sức đi tiếp, ghi chi phí (docs/MAN_HINH_VA_TAC_DONG.md mục 7.1, 7.4)
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import type { CrewTrip } from '@shared/api/tripExecution'
import { incidentsApi, type CauseType, type IncidentType } from '@shared/api/incidentOps'
import { useAutoRefresh, useLoad } from '@shared/hooks/useLoad'
import { formatVND } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Skeleton } from '@shared/ui/Skeleton'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { Textarea } from '@shared/ui/Textarea'
import { useToast } from '@shared/ui/toast'
import { EvidencePanel } from '@backoffice/shared/EvidencePanel'
import { IncidentCostForm } from '@backoffice/shared/IncidentCostForm'
import { TripIncidentEvidence } from './TripIncidentEvidence'
import s from './MyTrips.module.css'

const TYPES: IncidentType[] = ['HEALTH', 'VEHICLE_BREAKDOWN', 'ACCIDENT', 'DELAY', 'DOCUMENT', 'OTHER']
const CAUSES: CauseType[] = ['COMPANY_FAULT', 'HORSE_CONDITION', 'CUSTOMER_DOCS', 'AGENT_FAULT', 'FORCE_MAJEURE', 'RECIPIENT_ABSENT']

// Chi phí đã ghi của sự cố, mỗi khoản kèm biên lai; tải biên lai khi quản lý chưa duyệt
function IncidentCosts({ incidentId }: { incidentId: number }) {
  const { t } = useTranslation()
  const { data } = useLoad(() => incidentsApi.costs(incidentId), [incidentId], { toastOnError: false })
  return (
    <ul className={s.list} aria-label={t('incidents.costs')}>
      {(data ?? []).map(cost => (
        <li key={cost.id} className={s.body}>
          <p className={s.line}>{t(`incidents.category.${cost.category}`)} · {cost.description} · {formatVND(cost.amount ?? 0)}</p>
          <EvidencePanel title={t('incidents.receipts')} load={() => incidentsApi.receipts(cost.id ?? 0)}
            upload={cost.approvedAt ? undefined : file => incidentsApi.attachReceipt(cost.id ?? 0, file)} />
        </li>
      ))}
    </ul>
  )
}

function useTripIncidentActions(reload: () => Promise<void>) {
  const { t } = useTranslation()
  const toast = useToast()
  const [busy, setBusy] = useState(false)

  const act = async (action: () => Promise<unknown>, done: string) => {
    setBusy(true)
    try {
      await action()
      toast(done, 'success')
      await reload()
      return true
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e), 'error')
      return false
    } finally {
      setBusy(false)
    }
  }

  return {
    act,
    busy,
    startRest: (id: number) => act(() => incidentsApi.startRest(id), t('myTrips.run.incident.restStarted')),
    confirmFit: (id: number) => act(() => incidentsApi.confirmFit(id), t('myTrips.run.incident.fitDone')),
  }
}

export function TripIncidentPanel({ run }: { run: CrewTrip }) {
  const { t } = useTranslation()
  const { session } = useAuth()
  const tripId = run.trip?.id ?? 0
  const canReport = run.trip?.status === 'IN_PROGRESS'
  const [reporting, setReporting] = useState(false)
  const [form, setForm] = useState({ scope: '', type: 'OTHER' as IncidentType, cause: 'COMPANY_FAULT' as CauseType, title: '', description: '' })
  const [costVersion, setCostVersion] = useState(0)
  const { data: incidents, loaded, error, reload } = useLoad(() => incidentsApi.ofTrip(tripId), [tripId], { toastOnError: false })
  const { act, busy, startRest, confirmFit } = useTripIncidentActions(reload)
  useAutoRefresh(reload)

  return (
    <Card title={t('incidents.title')} actions={canReport && <Button variant="danger" onClick={() => setReporting(true)}>{t('myTrips.run.incident.report')}</Button>}>
      {!loaded && <Skeleton variant="block" lines={3} />}
      {error && <ErrorState message={error.message} onRetry={reload} />}
      {loaded && !error && !incidents?.length && (
        <EmptyState title={t('incidents.emptyTitle')} description={t(canReport ? 'incidents.reportIfNeeded' : 'incidents.emptyDescription')} />
      )}
      <ul className={s.list}>
        {(incidents ?? []).map(incident => {
          const id = incident.id ?? 0
          const open = incident.status !== 'RESOLVED' && incident.status !== 'CLOSED'
          const latest = incident.plans?.at(-1)
          const canHandleFit = session?.role === 'escort' && incident.status === 'PLAN_ACTIVE' && incident.requiresFit && !incident.fitConfirmedAt
          return (
            <li key={id} className={s.body}>
              <p className={s.line}>{incident.title}{incident.horseName ? ` · ${incident.horseName}` : ''}</p>
              <StatusBadge tone={open ? 'warning' : 'success'} label={t(`incidents.status.${incident.status}`)} />
              {incident.status !== 'CLOSED' && (
                <TripIncidentEvidence incident={incident}
                  fitActions={canHandleFit ? { busy, startRest: () => startRest(id), confirmFit: () => confirmFit(id) } : undefined}
                  canUploadRecord={open && latest?.status === 'APPROVED' && latest.resolutionType === 'TRANSFER_VEHICLE'} />
              )}
              <IncidentCosts key={`${id}-${costVersion}`} incidentId={id} />
              {incident.status !== 'CLOSED' && (
                <IncidentCostForm incidentId={id} busy={busy} className={s.body} onSave={async draft => {
                  const saved = await act(() => incidentsApi.recordCost(draft), t('myTrips.run.incident.costSaved'))
                  if (saved) setCostVersion(version => version + 1)
                  return saved
                }} />
              )}
            </li>
          )
        })}
      </ul>
      {reporting && (
        <Modal title={t('myTrips.run.incident.reportTitle')} onClose={() => setReporting(false)}
          footer={(
            <>
              <Button onClick={() => setReporting(false)}>{t('ui.cancel')}</Button>
              <Button variant="danger" disabled={!form.title.trim() || !form.description.trim() || (form.type === 'HEALTH' && !form.scope)} loading={busy}
                onClick={() => act(() => incidentsApi.report({
                  vehicleTripId: tripId, horseLegId: form.scope ? Number(form.scope) : undefined,
                  incidentType: form.type, causeType: form.cause, title: form.title.trim(), description: form.description.trim(),
                }), t('myTrips.run.incident.reported')).then(ok => { if (ok) setReporting(false) })}>
                {t('myTrips.run.incident.send')}
              </Button>
            </>
          )}>
          <div className={s.body}>
            <Field label={t('myTrips.run.incident.scope')} required={form.type === 'HEALTH'}>
              {c => <Select {...c} value={form.scope} onChange={value => setForm({ ...form, scope: value })}
                options={[{ value: '', label: t('myTrips.run.incident.wholeTrip') },
                  ...(run.horses ?? []).map(h => ({ value: `${h.leg?.id}`, label: `${h.horseName} · ${h.bookingCode}` }))]} />}
            </Field>
            <Field label={t('incidents.colType')}>
              {c => <Select {...c} value={form.type} onChange={value => setForm({ ...form, type: value as IncidentType })}
                options={TYPES.map(v => ({ value: v, label: t(`incidents.type.${v}`) }))} />}
            </Field>
            <Field label={t('myTrips.run.incident.cause')}>
              {c => <Select {...c} value={form.cause} onChange={value => setForm({ ...form, cause: value as CauseType })}
                options={CAUSES.map(v => ({ value: v, label: t(`incidents.cause.${v}`) }))} />}
            </Field>
            <Field label={t('myTrips.run.incident.incidentTitle')} required>
              {c => <Input {...c} value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} data-autofocus />}
            </Field>
            <Field label={t('myTrips.run.incident.incidentDescription')} required>
              {c => <Textarea {...c} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} />}
            </Field>
          </div>
        </Modal>
      )}
    </Card>
  )
}
