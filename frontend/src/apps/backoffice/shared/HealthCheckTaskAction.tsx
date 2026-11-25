import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { HealthCheckTarget, HealthCheckTaskDraft } from '@shared/api/healthCheckTasks'
import { formatDateTime } from '@shared/lib/format'
import { Button } from '@shared/ui/Button'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { Textarea } from '@shared/ui/Textarea'
import { useHealthCheckAssignment } from './useHealthCheckAssignment'
import s from './HealthCheckTask.module.css'

type Props = HealthCheckTarget & { horseName?: string }
const TYPES = ['ROUTINE', 'FOLLOW_UP', 'BEFORE_DEPARTURE'] as const

export function HealthCheckTaskAction({ horseName, ...target }: Props) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  return <>
    <Button size="sm" onClick={() => setOpen(true)}>{t('healthCheckTasks.assign')}</Button>
    {open && <AssignmentDialog target={target} horseName={horseName} onClose={() => setOpen(false)} />}
  </>
}

function AssignmentDialog({ target, horseName, onClose }: {
  target: HealthCheckTarget
  horseName?: string
  onClose: () => void
}) {
  const { t } = useTranslation()
  const { assignees, tasks, errors, assign, cancel } = useHealthCheckAssignment(target)
  const [escort, setEscort] = useState('')
  const [type, setType] = useState<HealthCheckTaskDraft['checkType']>('ROUTINE')
  const [due, setDue] = useState('')
  const [note, setNote] = useState('')
  const [confirm, setConfirm] = useState(false)
  const [cancelId, setCancelId] = useState<number>()
  const [reason, setReason] = useState('')

  if (cancelId != null) return (
    <ConfirmDialog title={t('healthCheckTasks.cancel')} tone="danger"
      message={<>
        <p>{t('healthCheckTasks.cancelPrompt')}</p>
        <Field label={t('healthCheckTasks.reason')} error={errors.reason} required>
          {control => <Textarea {...control} value={reason} onChange={event => setReason(event.target.value)} maxLength={500} />}
        </Field>
      </>}
      confirmLabel={t('healthCheckTasks.cancel')} confirmDisabled={!reason.trim()}
      onCancel={() => setCancelId(undefined)}
      onConfirm={async () => { await cancel(cancelId, reason); setCancelId(undefined) }} />
  )

  if (confirm) return (
    <ConfirmDialog title={t('healthCheckTasks.assign')} message={t('healthCheckTasks.assignPrompt', { horse: horseName })}
      confirmLabel={t('healthCheckTasks.assign')} onCancel={() => setConfirm(false)}
      onConfirm={async () => {
        try {
          await assign({ ...target, assignedTo: Number(escort), checkType: type, dueAt: new Date(due).toISOString(), note })
          setNote('')
          setDue('')
        } finally {
          setConfirm(false)
        }
      }} />
  )

  return (
    <Modal title={t('healthCheckTasks.assign')} subtitle={horseName} onClose={onClose}>
      <div className={s.stack}>
        {!assignees.loaded ? <Skeleton lines={3} /> : assignees.error ? (
          <ErrorState title={t('healthCheckTasks.loadError')} onRetry={assignees.reload} />
        ) : !assignees.data?.length ? (
          <EmptyState title={t('healthCheckTasks.noEscorts')} description={t('healthCheckTasks.noEscortsHint')} />
        ) : (
          <form className={s.stack} onSubmit={event => { event.preventDefault(); setConfirm(true) }}>
            <Field label={t('healthCheckTasks.escort')} error={errors.assignedTo} required>
              {control => <Select {...control} value={escort} onChange={setEscort} placeholder={t('tripWords.choose')}
                options={assignees.data?.flatMap(person => person.id == null ? [] : [{ value: String(person.id), label: person.name }]) ?? []} />}
            </Field>
            <Field label={t('healthCheckTasks.type')} error={errors.checkType} required>
              {control => <Select {...control} value={type} onChange={value => setType(value as HealthCheckTaskDraft['checkType'])}
                options={TYPES.map(value => ({ value, label: t(`healthCheckTasks.types.${value}`) }))} />}
            </Field>
            <Field label={t('healthCheckTasks.due')} error={errors.dueAt} required>
              {control => <Input {...control} type="datetime-local" value={due} onChange={event => setDue(event.target.value)} required />}
            </Field>
            <Field label={t('stayHealth.note')} error={errors.note}>
              {control => <Textarea {...control} value={note} onChange={event => setNote(event.target.value)} maxLength={500} />}
            </Field>
            <Button type="submit" variant="primary" disabled={!escort || !due}>{t('healthCheckTasks.assign')}</Button>
          </form>
        )}
        <h3>{t('healthCheckTasks.title')}</h3>
        {!tasks.loaded ? <Skeleton lines={2} /> : tasks.error ? (
          <ErrorState title={t('healthCheckTasks.loadError')} onRetry={tasks.reload} />
        ) : !tasks.data?.length ? <EmptyState title={t('healthCheckTasks.empty')} description={t('healthCheckTasks.mineHint')} /> : (
          <ul className={s.stack}>
            {tasks.data.map(task => (
              <li key={task.id}>
                <strong>{task.assigneeName}</strong> ? {task.place} ? {t(`healthCheckTasks.types.${task.checkType}`)}
                <p>{t(`healthCheckTasks.status.${task.status}`)}{task.overdue && ` ? ${t('healthCheckTasks.overdue')}`}
                  {task.dueAt && ` ? ${formatDateTime(new Date(task.dueAt))}`}</p>
                {task.cancelReason && <p>{task.cancelReason}</p>}
                {task.canCancel && task.id != null && <Button size="sm" variant="danger" onClick={() => {
                  setReason('')
                  setCancelId(task.id)
                }}>{t('healthCheckTasks.cancel')}</Button>}
              </li>
            ))}
          </ul>
        )}
      </div>
    </Modal>
  )
}
