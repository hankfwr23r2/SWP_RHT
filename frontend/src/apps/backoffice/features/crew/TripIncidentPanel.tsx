// Sự cố của lượt xe nhìn từ tổ xe: báo sự cố, xác nhận ngựa đủ sức đi tiếp, ghi chi phí (docs/MAN_HINH_VA_TAC_DONG.md mục 7.1, 7.4)
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { CrewTrip } from '@shared/api/tripExecution'
import { incidentsApi, type CauseType, type Evidence, type IncidentType } from '@shared/api/incidentOps'
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
import s from './MyTrips.module.css'

const TYPES: IncidentType[] = ['HEALTH', 'VEHICLE_BREAKDOWN', 'ACCIDENT', 'DELAY', 'DOCUMENT', 'OTHER']
const CAUSES: CauseType[] = ['COMPANY_FAULT', 'HORSE_CONDITION', 'CUSTOMER_DOCS', 'AGENT_FAULT', 'FORCE_MAJEURE', 'RECIPIENT_ABSENT']

// Vị trí hiện tại cho ảnh hiện trường; máy không cho lấy vị trí thì gửi ảnh không kèm tọa độ
const currentPosition = () => new Promise<{ latitude: number; longitude: number } | undefined>(resolve => {
  if (!navigator.geolocation) return resolve(undefined)
  navigator.geolocation.getCurrentPosition(({ coords }) => resolve({ latitude: Number(coords.latitude.toFixed(6)), longitude: Number(coords.longitude.toFixed(6)) }),
    () => resolve(undefined), { timeout: 5000, maximumAge: 60_000 })
})

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

function useTripIncidentEvidence(incidentId: number) {
  const query = useLoad(() => incidentsApi.photos(incidentId), [incidentId], { toastOnError: false })
  const save = async (upload: Promise<Evidence[]>) => {
    const evidence = await upload
    query.updateData(() => evidence)
    return evidence
  }
  return {
    ...query,
    uploadPhoto: async (file: File) => save(incidentsApi.attachPhoto(incidentId, file, await currentPosition())),
    uploadRecord: (file: File) => save(incidentsApi.attachPhoto(incidentId, file, undefined, 'VEHICLE_SWAP_RECORD')),
  }
}

function TripIncidentEvidence({ incidentId, canUploadRecord }: { incidentId: number; canUploadRecord: boolean }) {
  const { t } = useTranslation()
  const { data, loaded, error, reload, uploadPhoto, uploadRecord } = useTripIncidentEvidence(incidentId)
  if (!loaded) return <Skeleton variant="block" lines={2} />
  if (error) return <ErrorState message={error.message} onRetry={reload} />
  const records = (data ?? []).filter(file => file.documentTypeCode === 'VEHICLE_SWAP_RECORD')

  return (
    <>
      <EvidencePanel title={t('incidents.photos')} evidence={(data ?? []).filter(file => file.documentTypeCode !== 'VEHICLE_SWAP_RECORD')}
        upload={uploadPhoto} />
      {(canUploadRecord || records.length > 0) && <EvidencePanel title={t('incidents.vehicleSwapRecord')} evidence={records}
        accept="image/*" fileLabel={t('incidents.vehicleSwapRecordFile')} uploadLabel={t('incidents.uploadVehicleSwapRecord')}
        upload={canUploadRecord ? uploadRecord : undefined} />}
    </>
  )
}

export function TripIncidentPanel({ run }: { run: CrewTrip }) {
  const { t } = useTranslation()
  const toast = useToast()
  const tripId = run.trip?.id ?? 0
  const canReport = run.trip?.status === 'IN_PROGRESS'
  const [reporting, setReporting] = useState(false)
  const [busy, setBusy] = useState(false)
  const [form, setForm] = useState({ scope: '', type: 'OTHER' as IncidentType, cause: 'COMPANY_FAULT' as CauseType, title: '', description: '' })
  const [costVersion, setCostVersion] = useState(0)
  const { data: incidents, loaded, error, reload } = useLoad(() => incidentsApi.ofTrip(tripId), [tripId], { toastOnError: false })
  useAutoRefresh(reload)

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
          return (
            <li key={id} className={s.body}>
              <p className={s.line}>{incident.title}{incident.horseName ? ` · ${incident.horseName}` : ''}</p>
              <StatusBadge tone={open ? 'warning' : 'success'} label={t(`incidents.status.${incident.status}`)} />
              {incident.status === 'PLAN_ACTIVE' && incident.requiresFit && !incident.fitConfirmedAt && (
                <Button variant="primary" size="lg" fullWidth loading={busy}
                  onClick={() => act(() => incidentsApi.confirmFit(id), t('myTrips.run.incident.fitDone'))}>{t('myTrips.run.incident.confirmFit')}</Button>
              )}
              {incident.status !== 'CLOSED' && (
                <TripIncidentEvidence incidentId={id}
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
