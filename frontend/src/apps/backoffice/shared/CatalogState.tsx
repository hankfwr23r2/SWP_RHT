
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Skeleton } from '@shared/ui/Skeleton'

export function CatalogState({ loading, failed, retrying, reload, compact, empty, description, children }: {
  loading?: boolean
  failed?: boolean
  retrying?: boolean
  reload: () => unknown
  compact?: boolean
  empty?: boolean
  description?: ReactNode
  children: ReactNode
}) {
  const { t } = useTranslation()
  if (loading) return <Skeleton lines={6} />
  if (failed) return <ErrorState message={t('staffCatalog.loadFailed')} onRetry={reload} retrying={retrying} />
  if (empty) return <EmptyState compact={compact} title={t('staffCatalog.empty')} description={description ?? t('staffCatalog.emptyReadOnly')} />
  return children
}
