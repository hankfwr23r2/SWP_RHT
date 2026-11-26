// Sự cố của lượt xe nhìn từ tổ xe: báo sự cố, xác nhận ngựa đủ sức đi tiếp, ghi chi phí (docs/MAN_HINH_VA_TAC_DONG.md mục 7.1, 7.4)
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import type { CrewTrip } from '@shared/api/tripExecution'
import { incidentsApi } from '@shared/api/incidentOps'
import { useLoad } from '@shared/hooks/useLoad'
import { formatVND } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Skeleton } from '@shared/ui/Skeleton'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { useToast } from '@shared/ui/toast'
import { EvidencePanel } from '@backoffice/shared/EvidencePanel'
import { IncidentCostForm } from '@backoffice/shared/IncidentCostForm'
import { IncidentSeverityBadge } from '@backoffice/shared/IncidentSeverity'
import { VetResultControl } from '@backoffice/shared/VetResultControl'
import { TripIncidentEvidence } from './TripIncidentEvidence'
import { TripIncidentReportDialog } from './TripIncidentReportDialog'
import { useTripIncidents } from './useTripIncidents'
import s from './MyTrips.module.css'

type TripIncidentQuery = ReturnType<typeof useTripIncidents>

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

export function TripIncidentPanel({ run, query }: { run: CrewTrip; query?: TripIncidentQuery }) {
  return query
    ? <TripIncidentPanelContent run={run} query={query} />
    : <StandaloneTripIncidentPanel run={run} />
}

function StandaloneTripIncidentPanel({ run }: { run: CrewTrip }) {
  const query = useTripIncidents(run.trip?.id ?? 0, run.trip)
  return <TripIncidentPanelContent run={run} query={query} />
}

function TripIncidentPanelContent({ run, query }: { run: CrewTrip; query: TripIncidentQuery }) {
  const { t } = useTranslation()
  const { session } = useAuth()
  const canReport = run.trip?.status === 'IN_PROGRESS'
  const [reporting, setReporting] = useState(false)
  const [costVersion, setCostVersion] = useState(0)
  const { data: incidents, loaded, error, reload, report } = query
  const { act, busy, startRest, confirmFit } = useTripIncidentActions(reload)

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
              <IncidentSeverityBadge severity={incident.severity} />
              {session?.role === 'escort' && incident.status === 'PLAN_ACTIVE'
                && latest?.status === 'APPROVED' && latest.resolutionType === 'VET_TREATMENT' && (
                <VetResultControl key={latest.id} incidentId={id} planId={latest.id} partnerName={latest.handlerPartyName} disabled={busy} onSaved={reload} />
              )}
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
      {reporting && <TripIncidentReportDialog run={run} onClose={() => setReporting(false)} onReport={report} />}
    </Card>
  )
}
