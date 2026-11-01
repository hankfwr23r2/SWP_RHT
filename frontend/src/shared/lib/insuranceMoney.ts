export function formatInsuranceMoney(value?: number): string {
  return value == null ? '' : new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(value)
}
export function parseInsuranceMoney(raw: string): number | undefined {
  const digits = raw.replace(/[^0-9]/g, '')
  if (!digits) return undefined
  return Math.min(Number(digits), 1_000_000_000_000)
}
