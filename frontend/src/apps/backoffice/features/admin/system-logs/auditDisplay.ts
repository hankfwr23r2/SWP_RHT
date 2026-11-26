import { formatDateTime } from '@shared/lib/format'

export function auditTimestamp(value: string | undefined) {
  const formatted = value && formatDateTime(value)
  return formatted ? formatted + ' (UTC+7)' : '—'
}
