export function auditTimestamp(value: string | undefined, locale: string) {
  const timestamp = value ? new Date(value) : undefined
  if (!timestamp || Number.isNaN(timestamp.getTime())) return '—'
  return new Intl.DateTimeFormat(locale === 'vi' ? 'vi-VN' : 'en-GB', {
    timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'medium',
  }).format(timestamp) + ' (UTC+7)'
}
