import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { stablingApi } from '@shared/api/stabling'
import { useAutoRefresh, useLoad } from '@shared/hooks/useLoad'
import { formatDateTime, formatVND } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Skeleton } from '@shared/ui/Skeleton'
import { useToast } from '@shared/ui/toast'
import s from '../Orders.module.css'

export function StablingClockCard({ bookingId, onChanged }: { bookingId: number; onChanged: () => void }) {
  const { t } = useTranslation()
  const toast = useToast()
  const { data, loaded, error, reload } = useLoad(() => stablingApi.clocks(bookingId), [bookingId], { toastOnError: false })
  useAutoRefresh(reload)
  const [action, setAction] = useState<{ stayId: number; collect: boolean }>()
  const [expected, setExpected] = useState('')
  const [invalid, setInvalid] = useState(false)

  const confirm = async () => {
    if (!action) return
    const time = new Date(expected)
    if (action.collect && Number.isNaN(time.getTime())) {
      setInvalid(true)
      return
    }
    try {
      if (action.collect) await stablingApi.collect(action.stayId, { expectedAt: time.toISOString() })
      else await stablingApi.ready(action.stayId)
      toast(t('stablingClock.saved'), 'success')
      setAction(undefined)
      void reload()
      onChanged()
    } catch (cause) {
      if (action.collect) setInvalid(true)
      toast(cause instanceof Error ? cause.message : t('stablingClock.failed'), 'error')
      throw cause
    }
  }

  return <Card title={t('stablingClock.title')}>
    {!loaded ? <Skeleton variant="block" lines={3} />
      : error ? <ErrorState onRetry={reload} />
      : !data?.length ? <EmptyState title={t('stablingClock.empty')} description={t('stablingClock.emptyHint')} />
      : <div className={s.stack}>{data.map(row => <section key={row.stayId}>
        <h3>{row.horseName}</h3>
        <p>{t(row.running ? 'stablingClock.running' : 'stablingClock.paused')} · {t('stablingClock.elapsed', {
          hours: Math.floor((row.elapsedSeconds ?? 0) / 3600), minutes: Math.floor((row.elapsedSeconds ?? 0) / 60) % 60,
        })}</p>
        <p>{t('stablingClock.amount', { amount: formatVND(row.amount ?? 0) })}</p>
        {row.nextChargeAt && <p>{t('stablingClock.next', { at: formatDateTime(new Date(row.nextChargeAt)) })}</p>}
        {row.collectionExpectedAt && <p>{t('stablingClock.collectionAt', { at: formatDateTime(new Date(row.collectionExpectedAt)) })}</p>}
        <div className={s.stack}>
          {row.canReady && row.stayId != null && <Button onClick={() => setAction({ stayId: row.stayId!, collect: false })}>{t('stablingClock.ready')}</Button>}
          {row.canCollect && row.stayId != null && <Button onClick={() => {
            setExpected('')
            setInvalid(false)
            setAction({ stayId: row.stayId!, collect: true })
          }}>{t('stablingClock.collect')}</Button>}
        </div>
      </section>)}</div>}
    {action && <ConfirmDialog title={t(action.collect ? 'stablingClock.collect' : 'stablingClock.ready')}
      confirmLabel={t(action.collect ? 'stablingClock.collect' : 'stablingClock.ready')}
      onCancel={() => setAction(undefined)} onConfirm={confirm} message={action.collect ? <>
        <p>{t('stablingClock.collectMessage')}</p>
        <Field label={t('stablingClock.collectionTime')} error={invalid ? t('stablingClock.invalidTime') : undefined}>
          {props => <Input {...props} type="datetime-local" value={expected} onChange={event => {
            setExpected(event.target.value)
            setInvalid(false)
          }} />}
        </Field>
      </> : t('stablingClock.readyMessage')} />}
  </Card>
}
