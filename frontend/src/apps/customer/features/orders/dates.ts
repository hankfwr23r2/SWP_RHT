// Ngày nghiệp vụ theo giờ Việt Nam, dạng YYYY-MM-DD
const DAY_MS = 86_400_000

export const todayIso = (now = Date.now()) => new Date(now + 7 * 3_600_000).toISOString().slice(0, 10)
const shift = (iso: string, days: number) => new Date(Date.parse(`${iso}T00:00:00Z`) + days * DAY_MS).toISOString().slice(0, 10)
export const dayBefore = (iso: string) => shift(iso, -1)
export const dayAfter = (iso: string) => shift(iso, 1)
