// Đổi giữa ISO và giá trị ô datetime-local (giờ của máy)
export const localToIso = (value: string) => new Date(value).toISOString()

export function isoToLocal(value: string) {
  const date = new Date(value)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}
