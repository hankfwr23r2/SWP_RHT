import type { Booking, IncidentExpense } from '../types/booking'

// The incident ledger is authoritative. Reviews and aggregate actualCost are not extra charges.
export function incidentExpenseItems(b: Pick<Booking, 'incidents' | 'settlement'>): (IncidentExpense & { kind?: NonNullable<Booking['incidents']>[number]['kind'] })[] {
  const seen = new Set<string>()
  if (!b.incidents?.length && b.settlement?.items?.length) {
    return b.settlement.items.flatMap((item, index) => {
      const id = item.id ?? `legacy-${index}`
      if (seen.has(id) || !item.photo || !Number.isFinite(item.amount) || item.amount <= 0) return []
      seen.add(id)
      return [{ id, category: item.category ?? 'other', label: item.label ?? item.description ?? 'Chứng từ lưu trữ',
        amount: item.amount, photo: item.photo, payer: 'carrier' as const, at: b.settlement?.reviewedAt ?? b.settlement?.issuedAt ?? 0, by: '' }]
    })
  }
  return (b.incidents ?? []).flatMap(incident => {
    const entries = incident.expenses ?? []
    const expenses = entries.length ? entries : incident.actualCost && incident.actualCost > 0 && incident.invoiceUrl
      ? [{ id: 'receipt', category: 'other' as const, label: 'Chi phí sự cố', photo: incident.invoiceUrl, amount: incident.actualCost, payer: 'carrier' as const, at: incident.resolvedAt ?? incident.reportedAt, by: incident.reportedBy }]
      : []
    return expenses.flatMap((expense, index) => {
      const key = `${incident.id}/${expense.id || index}`
      if (seen.has(key) || !expense.photo || !Number.isFinite(expense.amount) || expense.amount <= 0) return []
      seen.add(key)
      return [{ ...expense, id: key, payer: 'carrier' as const, kind: incident.kind }]
    })
  })
}
