// Ghi sức khỏe từng con đang trên xe tại trạm nghỉ (docs/MAN_HINH_VA_TAC_DONG.md mục 6.2)
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { tripExecutionApi, type CrewTrip, type HealthStatus } from '@shared/api/tripExecution'
import { useLoad } from '@shared/hooks/useLoad'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Select } from '@shared/ui/Select'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { useToast } from '@shared/ui/toast'
import { HorseFeedingDetails } from './HorseFeedingDetails'
import { horsesPassing } from './tripRunModel'
import s from './MyTrips.module.css'

type Stop = NonNullable<NonNullable<CrewTrip['trip']>['stops']>[number]

const STATUSES: HealthStatus[] = ['NORMAL', 'WATCH', 'SICK', 'CRITICAL']

export function StopHealthPanel({ run, stop }: { run: CrewTrip; stop: Stop }) {
  const { t } = useTranslation()
  const toast = useToast()
  const horses = horsesPassing(run, stop)
  const [drafts, setDrafts] = useState<Record<number, HealthStatus>>({})
  const [busy, setBusy] = useState<number>()
  // Con đã ghi sức khỏe ở điểm này
  const { data: logged, reload } = useLoad(async () => {
    const lists = await Promise.all(horses.map(h => tripExecutionApi.healthLogs(h.leg?.bookingHorseId ?? 0)))
    return new Set(lists.flat().filter(log => log.tripStopId === stop.id).map(log => log.bookingHorseId))
  }, [stop.id, horses.length])

  const save = async (bookingHorseId: number, status: HealthStatus) => {
    setBusy(bookingHorseId)
    try {
      await tripExecutionApi.recordHealth({
        bookingHorseId, tripStopId: stop.id, healthStatus: status,
      })
      toast(t('myTrips.run.health.saved'), 'success')
      await reload()
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e), 'error')
    } finally {
      setBusy(undefined)
    }
  }

  return (
    <section className={s.body} aria-label={t('myTrips.run.health.title')}>
      <p className={s.line}>{t('myTrips.run.health.title')}</p>
      {horses.map(horse => {
        const id = horse.leg?.bookingHorseId ?? 0
        const status = drafts[id] ?? 'NORMAL'
        const done = logged?.has(id)
        return (
          <div key={id} className={s.body}>
            <p className={s.line}>{horse.horseName} · {horse.bookingCode}</p>
            <HorseFeedingDetails horse={horse} />
            <StatusBadge tone={done ? 'success' : 'warning'} label={done ? t('myTrips.run.health.logged') : t('myTrips.run.health.notLogged')} />
            <Field label={t('myTrips.run.health.status')}>
              {c => <Select {...c} value={status} onChange={value => setDrafts(current => ({ ...current, [id]: value as HealthStatus }))}
                options={STATUSES.map(v => ({ value: v, label: t(`myTrips.run.health.statusValue.${v}`) }))} />}
            </Field>
            <Button variant="primary" size="lg" fullWidth loading={busy === id} onClick={() => save(id, status)}>
              {t('myTrips.run.health.save')}
            </Button>
          </div>
        )
      })}
    </section>
  )
}
