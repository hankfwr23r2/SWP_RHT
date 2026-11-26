import { Input } from '@shared/ui/Input'
import { ListPanel } from '@shared/ui/ListPanel'
// Sự cố đang xử lý cho điều phối viên và quản lý (docs/MAN_HINH_VA_TAC_DONG.md mục 7.2, 7.3)
import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { invalidateStaffWork } from '@backoffice/shared/useStaffWork'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { incidentsApi, type Incident } from '@shared/api/incidentOps'
import { useLoad } from '@shared/hooks/useLoad'
import { formatDateTime } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { PageHeader } from '@shared/ui/PageHeader'
import { Skeleton } from '@shared/ui/Skeleton'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { Table, type TableColumn } from '@shared/ui/Table'
import { IncidentSeverityBadge } from '@backoffice/shared/IncidentSeverity'
import { IncidentDetailModal } from './IncidentDetailModal'
import { HandoverCauseDialog } from './HandoverCauseDialog'
import s from './Incidents.module.css'

export default function IncidentsBoardPage() {
  const { t } = useTranslation()
  const { session } = useAuth()
  const role = session?.role === 'manager' ? 'manager' : 'coordinator'
  const [params, setParams] = useSearchParams()
  const [selectedId, setOpenId] = useState<number>()
  const openId = selectedId ?? (Number(params.get('incidentId')) || undefined)
  const closeIncident = () => {
    setOpenId(undefined)
    if (params.has('incidentId')) {
      setParams(previous => { previous.delete('incidentId'); return previous }, { replace: true })
    }
  }
  const [amending, setAmending] = useState<Incident>()
  const { data, loaded, error, reload } = useLoad(incidentsApi.open, [], { toastOnError: false })
  const [query, setQuery] = useState('')
  const term = query.trim().toLowerCase()
  const shown = data?.filter(row => !term || [row.title, row.vehicleTripCode, row.horseName].some(value => value?.toLowerCase().includes(term)))
  const columns: TableColumn<Incident>[] = [
    { key: 'title', header: t('incidents.colTitle'), render: i => i.title },
    { key: 'type', header: t('incidents.colType'), render: i => t(`incidents.type.${i.incidentType}`) },
    { key: 'severity', header: t('incidents.severity.label'), render: i => <IncidentSeverityBadge severity={i.severity} /> },
    { key: 'trip', header: t('incidents.colTrip'), render: i => i.vehicleTripCode ?? '' },
    { key: 'horse', header: t('incidents.colHorse'), render: i => i.horseName ?? '' },
    { key: 'told', header: t('incidents.handoverCause.told'), render: i => i.deliveryFailure?.toldArrivalAt ? formatDateTime(new Date(i.deliveryFailure.toldArrivalAt)) : '' },
    { key: 'arrived', header: t('incidents.handoverCause.arrived'), render: i => i.deliveryFailure?.actualArrivalAt ? formatDateTime(new Date(i.deliveryFailure.actualArrivalAt)) : '' },
    { key: 'delay', header: t('incidents.handoverCause.delay'), render: i => i.deliveryFailure?.delayMinutes ?? '' },
    { key: 'status', header: t('incidents.colStatus'), render: i => <StatusBadge tone={i.status === 'PLAN_PENDING' ? 'warning' : 'info'} label={t(`incidents.status.${i.status}`)} /> },
    { key: 'open', header: '', align: 'end', render: i => <Button size="sm" onClick={() => setOpenId(i.id)}>{t('incidents.view')}</Button> },
    { key: 'amend', header: '', align: 'end', render: i => i.deliveryFailure?.amendable && i.id !== undefined &&
      (role === 'manager' || !i.coordinatorStaffCode || i.coordinatorStaffCode === session?.staffCode) &&
      (role === 'manager' || (i.confirmedCauseType ?? i.causeType) !== 'FORCE_MAJEURE') &&
      <Button size="sm" onClick={() => setAmending(i)}>{t('incidents.handoverCause.edit')}</Button> },
  ]
  const content = () => {
    if (!loaded) return <Skeleton variant="block" lines={4} />
    if (error || !data) return <ErrorState title={t('incidents.loadError')} message={error?.message} onRetry={reload} />
    if (!shown?.length) return <EmptyState compact icon={<i className="fa-solid fa-triangle-exclamation" />} title={t(term ? 'listPanel.noMatch' : 'incidents.emptyTitle')} description={t(term ? 'listPanel.trySearch' : 'incidents.emptyDescription')} />
    return <Table caption={t('incidents.caption')} columns={columns} rows={shown} rowKey={i => i.id ?? 0} />
  }
  return (
    <div className={s.page}>
      <PageHeader title={t('incidents.title')} description={t('incidents.description')} />
      <ListPanel count={!error ? shown?.length : undefined} toolbar={<Input type="search" value={query} onChange={event => setQuery(event.target.value)}
          placeholder={t('listPanel.search')} aria-label={t('listPanel.search')} />}>{content()}</ListPanel>
      {openId !== undefined && <IncidentDetailModal incidentId={openId} role={role} staffCode={session?.staffCode}
        onClose={closeIncident} onChanged={() => { invalidateStaffWork(); void reload() }} />}
      {amending && <HandoverCauseDialog incident={amending} role={role} onClose={() => setAmending(undefined)} onChanged={reload} />}
    </div>
  )
}
