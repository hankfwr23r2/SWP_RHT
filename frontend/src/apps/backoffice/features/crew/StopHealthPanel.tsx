// Ghi sức khỏe từng con đang trên xe tại trạm nghỉ (docs/MAN_HINH_VA_TAC_DONG.md mục 6.2)
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { tripExecutionApi, type CrewTrip, type HealthStatus, type IntakeLevel } from '@shared/api/tripExecution'
import { useLoad } from '@shared/hooks/useLoad'
import { Button } from '@shared/ui/Button'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Select } from '@shared/ui/Select'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { useToast } from '@shared/ui/toast'
import { horsesPassing } from './tripRunModel'
import s from './MyTrips.module.css'

type Stop = NonNullable<NonNullable<CrewTrip['trip']>['stops']>[number]
interface Draft { status: HealthStatus; water: IntakeLevel | ''; feed: IntakeLevel | ''; temperature: string }

const STATUSES: HealthStatus[] = ['NORMAL', 'WATCH', 'SICK', 'CRITICAL']
const INTAKES: IntakeLevel[] = ['NORMAL', 'REDUCED', 'REFUSED']
const emptyDraft = (): Draft => ({ status: 'NORMAL', water: '', feed: '', temperature: '' })

export function StopHealthPanel({ run, stop }: { run: CrewTrip; stop: Stop }) {
  const { t } = useTranslation()
  const toast = useToast()
  const horses = horsesPassing(run, stop)
  const [drafts, setDrafts] = useState<Record<number, Draft>>({})
  const [busy, setBusy] = useState<number>()
  // Con đã ghi đủ uống và ăn ở điểm này
  const { data: logged, reload } = useLoad(async () => {
    const lists = await Promise.all(horses.map(h => tripExecutionApi.healthLogs(h.leg?.bookingHorseId ?? 0)))
    return new Set(lists.flat().filter(log => log.tripStopId === stop.id && log.waterIntake && log.feedIntake).map(log => log.bookingHorseId))
  }, [stop.id, horses.length])

  const save = async (bookingHorseId: number, draft: Draft) => {
    setBusy(bookingHorseId)
    try {
      await tripExecutionApi.recordHealth({
        bookingHorseId, tripStopId: stop.id, healthStatus: draft.status,
        waterIntake: draft.water || undefined, feedIntake: draft.feed || undefined,
        temperatureCelsius: draft.temperature ? Number(draft.temperature) : undefined,
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
        const draft = drafts[id] ?? emptyDraft()
        const update = (patch: Partial<Draft>) => setDrafts(current => ({ ...current, [id]: { ...draft, ...patch } }))
        const done = logged?.has(id)
        return (
          <div key={id} className={s.body}>
            <p className={s.line}>{horse.horseName} · {horse.bookingCode}</p>
            <StatusBadge tone={done ? 'success' : 'warning'} label={done ? t('myTrips.run.health.logged') : t('myTrips.run.health.notLogged')} />
            <Field label={t('myTrips.run.health.status')}>
              {c => <Select {...c} value={draft.status} onChange={value => update({ status: value as HealthStatus })}
                options={STATUSES.map(v => ({ value: v, label: t(`myTrips.run.health.statusValue.${v}`) }))} />}
            </Field>
            <Field label={t('myTrips.run.health.water')}>
              {c => <Select {...c} value={draft.water} onChange={value => update({ water: value as IntakeLevel })} placeholder={t('dispatch.detail.choose')}
                options={INTAKES.map(v => ({ value: v, label: t(`myTrips.run.health.intakeValue.${v}`) }))} />}
            </Field>
            <Field label={t('myTrips.run.health.feed')}>
              {c => <Select {...c} value={draft.feed} onChange={value => update({ feed: value as IntakeLevel })} placeholder={t('dispatch.detail.choose')}
                options={INTAKES.map(v => ({ value: v, label: t(`myTrips.run.health.intakeValue.${v}`) }))} />}
            </Field>
            <Field label={t('myTrips.run.health.temperature')}>
              {c => <Input {...c} inputMode="decimal" value={draft.temperature} onChange={e => update({ temperature: e.target.value })} />}
            </Field>
            <Button variant="primary" size="lg" fullWidth disabled={!draft.water || !draft.feed} loading={busy === id} onClick={() => save(id, draft)}>
              {t('myTrips.run.health.save')}
            </Button>
          </div>
        )
      })}
    </section>
  )
}
