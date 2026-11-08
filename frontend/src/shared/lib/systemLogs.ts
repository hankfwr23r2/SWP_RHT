// All audit filters and timestamps use Vietnam time, independent of the device timezone.
export const auditDate = (date: Date) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(date)

export function auditDefaults(now = new Date()) {
  return { from: auditDate(new Date(now.getTime() - 6 * 86400000)), to: auditDate(now) }
}

export function auditQuery(params: URLSearchParams, defaults: { from: string; to: string }) {
  const from = params.get('from') ?? defaults.from
  const to = params.get('to') ?? defaults.to
  const validDate = (value: string) => /^\d{4}-\d{2}-\d{2}$/.test(value)
    && Number.isFinite(Date.parse(`${value}T00:00:00+07:00`))
    && auditDate(new Date(`${value}T00:00:00+07:00`)) === value
  if (!validDate(from) || !validDate(to) || from > to) throw new Error('invalidDates')
  const page = Number(params.get('page') ?? '0')
  const size = Number(params.get('size') ?? '20')
  if (!Number.isInteger(page) || page < 0 || page > 100000 || ![20, 50, 100].includes(size)) throw new Error('invalidPage')
  const query: Record<string, string | number> = {
    from: new Date(`${from}T00:00:00+07:00`).toISOString(),
    to: new Date(Date.parse(`${to}T00:00:00+07:00`) + 86400000).toISOString(), page, size,
  }
  for (const key of ['target', 'action', 'actor', 'key', 'role']) {
    const value = params.get(key)?.trim()
    if (value) query[key] = value
  }
  return query
}

export function auditChanges(before: string | null, after: string | null, legacy: boolean) {
  if (legacy) return []
  const parse = (value: string | null): Record<string, unknown> => {
    const result: unknown = value ? JSON.parse(value) : {}
    if (!result || typeof result !== 'object' || Array.isArray(result)) throw new Error('invalidSnapshot')
    return result as Record<string, unknown>
  }
  try {
    const oldData = parse(before)
    const newData = parse(after)
    return [...new Set([...Object.keys(oldData), ...Object.keys(newData)])]
      .filter(key => JSON.stringify(oldData[key]) !== JSON.stringify(newData[key]))
      .map(field => ({ field, before: oldData[field], after: newData[field] }))
  } catch {
    return null
  }
}
