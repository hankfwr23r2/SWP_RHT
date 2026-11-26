import { ListPanel } from '@shared/ui/ListPanel'
// Theo dõi (MAN_HINH 4.11): mọi con công ty hoặc đối tác đang giữ, con trễ khám đứng đầu; tự tải lại mỗi phút
import { lazy, Suspense, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { custodyApi, type CustodyRow } from '@shared/api/custody'
import { dispatchApi } from '@shared/api/dispatch'
import { HealthCheckTaskAction } from '@backoffice/shared/HealthCheckTaskAction'
import { StayHealthAction } from '@backoffice/shared/StayHealthAction'
import { HorseHealthHistory } from '@backoffice/shared/HorseHealthHistory'
import { BookingJourney } from '@shared/components/HorseJourney'
import { useLoad } from '@shared/hooks/useLoad'
import { formatDateTime } from '@shared/lib/format'
import { Badge } from '@shared/ui/Badge'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { PageHeader } from '@shared/ui/PageHeader'
import { Skeleton } from '@shared/ui/Skeleton'
import { useToast } from '@shared/ui/toast'
import { matchesCustody } from './custody'
import { PickupBalanceDialog } from './PickupBalanceDialog'
import s from './Tracking.module.css'

const RouteMap = lazy(() => import('@shared/components/RouteEditorMap'))
const NO_ORDER: string[] = []
const REFRESH_MS = 60_000

export default function TrackingPage() {
  const { t } = useTranslation()
  const [query, setQuery] = useState('')
  const toast = useToast()
  const [proof, setProof] = useState<File>()
  const [savingProof, setSavingProof] = useState(false)
  const [delivered, setDelivered] = useState<CustodyRow>()
  const [history, setHistory] = useState<CustodyRow>()
  const [journey, setJourney] = useState<CustodyRow>()
  const [balance, setBalance] = useState<CustodyRow>()
  const { data, loaded, error, reload } = useLoad(custodyApi.board, [], { toastOnError: false })
  // Giữ hàm tải lại mới nhất để đồng hồ chỉ đặt một lần
  const latest = useRef(reload)
  useEffect(() => { latest.current = reload })
  useEffect(() => {
    const timer = setInterval(() => void latest.current(), REFRESH_MS)
    return () => clearInterval(timer)
  }, [])
  const rows = (data ?? []).filter(row => matchesCustody(row, query))
  const points = useMemo(() => (data ?? []).flatMap(row => row.point?.lat != null && row.point.lng != null
    ? [{ id: String(row.bookingHorseId), name: `${row.horseName} · ${row.bookingCode}`, latitude: row.point.lat, longitude: row.point.lng }] : []), [data])

  const content = () => {
    if (!loaded) return <Skeleton variant="block" lines={4} />
    if (error || !data) return <ErrorState message={error?.message} onRetry={reload} />
    if (!rows.length) return <EmptyState compact icon={<i className="fa-solid fa-horse-head" />} title={t(query ? 'listPanel.noMatch' : 'tracking.empty')} description={t(query ? 'listPanel.trySearch' : 'tracking.emptyHint')}
      action={query ? <Button onClick={() => setQuery('')}>{t('horses.clearSearch')}</Button> : undefined} />
    return (
      <div className={s.layout}>
        <ul className={s.list} aria-label={t('tracking.list')}>
          {rows.map(row => (
            <li key={row.bookingHorseId} className={`${s.card} ${row.careOverdue ? s.late : ''}`}>
              <div className={s.head}>
                <strong><i className="fa-solid fa-horse-head" aria-hidden="true" /> {row.horseName}</strong>
                <span>{row.bookingCode}</span>
              </div>
              <div className={s.chips}>
                <Badge tone="info">{row.stayType ? t(`dispatch.stays.stayType.${row.stayType}`) : row.legType ? t(`tracking.leg.${row.legType}`) : t('tracking.waiting')}</Badge>
                {row.healthStatus && <Badge tone={row.healthStatus === 'NORMAL' ? 'success' : 'warning'}>{t(`tracking.health.${row.healthStatus}`, { defaultValue: row.healthStatus })}</Badge>}
                {row.careOverdue && <Badge tone="danger">{t('tracking.overdue')}</Badge>}
              </div>
              <p className={s.line}><i className="fa-solid fa-location-dot" aria-hidden="true" /> {row.placeName ?? t('tracking.unknownPlace')}{row.tripCode && ` · ${row.tripCode}`}</p>
              <p className={s.line}><i className="fa-solid fa-user-check" aria-hidden="true" /> {t(row.holder === 'PARTY' ? 'tracking.heldByPartner' : 'tracking.heldBy', { name: row.holderName })}
                {row.holderSince && ` · ${t('tracking.since', { at: formatDateTime(new Date(row.holderSince)) })}`}</p>
              {row.responsibleCoordinatorName && <p className={s.line}>{t('coordAssignment.owner')} · {row.responsibleCoordinatorName}</p>}
              <p className={s.line}><i className="fa-solid fa-heart-pulse" aria-hidden="true" /> {row.lastCheckAt ? t('tracking.lastCheck', { at: formatDateTime(new Date(row.lastCheckAt)) }) : t('tracking.noCheck')}
                {row.nextCheckAt && row.holder === 'COMPANY' && ` · ${t('tracking.nextCheck', { at: formatDateTime(new Date(row.nextCheckAt)) })}`}</p>
              <div className={s.chips}>
                {row.agentLegId && <Button size="sm" variant="primary" onClick={() => { setProof(undefined); setDelivered(row) }}>{t('dispatch.agent.complete')}</Button>}
                {row.healthLogStayId != null && row.bookingHorseId != null && (
                  <StayHealthAction horseStayId={row.healthLogStayId} bookingHorseId={row.bookingHorseId}
                    horseName={row.horseName} reported onSaved={reload} />
                )}
                {row.bookingHorseId != null && row.holder === 'COMPANY' && (row.healthLogStayId != null || row.vehicleTripId != null) && (
                  <HealthCheckTaskAction bookingHorseId={row.bookingHorseId} horseName={row.horseName}
                    horseStayId={row.healthLogStayId} vehicleTripId={row.healthLogStayId == null ? row.vehicleTripId : undefined} />
                )}
                {row.bookingHorseId != null && <Button size="sm" onClick={() => setHistory(row)}>{t('stayHealth.history')}</Button>}
                <Button size="sm" onClick={() => setJourney(row)}>{t('dispatch.board.journey')}</Button>
                {row.balanceContactAllowed && (
                  <Button size="sm" onClick={() => setBalance(row)}>{t('tracking.balance.open')}</Button>
                )}
              </div>
            </li>
          ))}
        </ul>
        {points.length > 0 && <Suspense fallback={<Skeleton variant="block" lines={4} />}><RouteMap points={points} orderedIds={NO_ORDER} /></Suspense>}
      </div>
    )
  }

  return (
    <div className={s.page}>
      <PageHeader title={t('tracking.title')} description={t('tracking.subtitle')} />
      <ListPanel count={data ? rows.length : undefined} toolbar={<Input type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder={t('tracking.search')} aria-label={t('tracking.search')} />}>
        {content()}
      </ListPanel>
      {delivered && (
        <Modal title={t('dispatch.agent.complete')} onClose={() => setDelivered(undefined)}
          footer={<Button variant="primary" disabled={!proof} loading={savingProof} onClick={async () => {
            if (!proof || !delivered.agentLegId) return
            setSavingProof(true)
            try {
              await dispatchApi.completeAgentLeg(delivered.agentLegId, proof)
              toast(t('tracking.agentDelivered'), 'success')
              setDelivered(undefined)
              await reload()
            } catch (e) {
              toast(e instanceof Error ? e.message : String(e), 'error')
            } finally {
              setSavingProof(false)
            }
          }}>{t('dispatch.agent.complete')}</Button>}>
          <p>{t('tracking.agentDeliveredMessage', { horse: delivered.horseName, agent: delivered.holderName })}</p>
          <Field label={t('handoverEvidence.agentProof')} required>
            {c => <Input {...c} type="file" accept="image/*,application/pdf" onChange={e => setProof(e.target.files?.[0])} />}
          </Field>
        </Modal>
      )}
      {history?.bookingHorseId != null && (
        <HorseHealthHistory bookingHorseId={history.bookingHorseId} horseName={history.horseName} onClose={() => setHistory(undefined)} />
      )}
      {journey && (
        <Modal title={t('dispatch.bookings.journeyTitle', { code: journey.bookingCode })} onClose={() => setJourney(undefined)}>
          <BookingJourney bookingId={journey.bookingId!} />
        </Modal>
      )}
      {balance?.bookingId && (
        <PickupBalanceDialog bookingId={balance.bookingId} row={balance} onClose={() => setBalance(undefined)}
          onDone={() => { setBalance(undefined); void reload() }} />
      )}
    </div>
  )
}
