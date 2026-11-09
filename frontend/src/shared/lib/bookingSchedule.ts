// Requests contain a calendar day in Vietnam, regardless of the browser's timezone.
export function earliestRescheduleDay(now: number, leadDays: number): string {
  const today = new Date(now + 7 * 60 * 60 * 1000).toISOString().slice(0, 10)
  const day = new Date(`${today}T00:00:00Z`)
  day.setUTCDate(day.getUTCDate() + Math.max(1, leadDays))
  return day.toISOString().slice(0, 10)
}
