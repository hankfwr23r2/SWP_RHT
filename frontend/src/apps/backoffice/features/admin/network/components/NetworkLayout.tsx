import { Suspense, type ReactNode } from 'react'
import { Await, useLoaderData, useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Skeleton } from '@shared/ui/Skeleton'
import { StatusBadge, type StatusTone } from '@shared/ui/StatusBadge'
import s from '../Network.module.css'

export type Confirmation = { label: string; message: string; run: () => Promise<boolean> }

export function NetworkLayout({ title, actions, children }: { title: string; actions?: ReactNode; children: ReactNode }) {
  return <div className={s.page}>
    <header className={s.header}><h1>{title}</h1>{actions}</header>
    {children}
  </div>
}

export function NetworkData<T>({ children }: { children: (data: T) => ReactNode }) {
  const { pending } = useLoaderData() as { pending: Promise<T & { errors: string[] }> }
  const { revalidate, state } = useRevalidator()
  const { t } = useTranslation()
  return <Suspense fallback={<Card><Skeleton lines={5} /></Card>}>
    <Await resolve={pending}>{(data: T & { errors: string[] }) => data.errors.length
      ? <Card><ErrorState message={t('staffNetwork.loadError')} onRetry={revalidate} retrying={state === 'loading'} /></Card>
      : children(data)}
    </Await>
  </Suspense>
}

export function NetworkEmpty({ hint }: { hint: string }) {
  const { t } = useTranslation()
  return <Card><EmptyState title={t('staffNetwork.empty')} description={hint} /></Card>
}

export function NetworkConfirm({ action, close }: { action: Confirmation | null; close: () => void }) {
  if (!action) return null
  return <ConfirmDialog title={action.label} confirmLabel={action.label} message={action.message}
    onCancel={close} onConfirm={async () => { if (await action.run()) close() }} />
}

const tones: Record<string, StatusTone> = {
  ACTIVE: 'success', PAUSED: 'warning', OPEN: 'success', IN_PROGRESS: 'info', CANCELLED: 'danger',
}

export function NetworkStatus({ status }: { status?: string }) {
  const { t } = useTranslation()
  return <StatusBadge tone={tones[status ?? ''] ?? 'neutral'} label={t(`staffNetwork.status.${status}`, { defaultValue: t('staffNetwork.unknown') })} />
}

export function EditButton({ onClick, disabled }: { onClick: () => void; disabled?: boolean }) {
  const { t } = useTranslation()
  return <Button size="sm" disabled={disabled} onClick={onClick}>{t('staffNetwork.edit')}</Button>
}
