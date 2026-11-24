import { useTranslation } from 'react-i18next'
import { StatusBadge, type StatusTone } from '@shared/ui/StatusBadge'

const tones: Record<string, StatusTone> = {
  ACTIVE: 'success',
  MAINTENANCE: 'warning',
}

export function CatalogStatus({ status }: { status?: string }) {
  const { t } = useTranslation()
  return <StatusBadge tone={tones[status ?? ''] ?? 'neutral'}
    label={t(`staffCatalog.status.${status}`, { defaultValue: t('staffCatalog.unknown') })} />
}
