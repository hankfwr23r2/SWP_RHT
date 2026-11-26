export type Time = string | number | Date

const DATE_ONLY = /^(\d{4})-(\d{2})-(\d{2})$/
const localDateTime = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Asia/Ho_Chi_Minh',
  year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
})

function dateParts(value: Time) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? {} : Object.fromEntries(localDateTime.formatToParts(date).map(part => [part.type, part.value]))
}

export const formatVND = (n: number) => Math.round(n).toLocaleString('en-US') + ' ₫'

export function formatDate(value: Time) {
  const iso = typeof value === 'string' ? DATE_ONLY.exec(value) : null
  if (iso) return [iso[3], iso[2], iso[1]].join('/')
  const { day, month, year } = dateParts(value)
  return year ? [day, month, year].join('/') : ''
}

export function formatClock(value: Time) {
  const { hour, minute } = dateParts(value)
  return hour ? [hour, minute].join(':') : ''
}

export function formatDateTime(value: Time) {
  if (typeof value === 'string' && DATE_ONLY.test(value)) return formatDate(value)
  const { day, month, year, hour, minute } = dateParts(value)
  return year ? [day, month, year].join('/') + ' ' + [hour, minute].join(':') : ''
}

export function parseDisplayDate(value: string): string | undefined {
  const parts = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value)
  if (!parts || parts[3] === '0000') return undefined
  const iso = [parts[3], parts[2], parts[1]].join('-')
  const date = new Date(iso)
  return !Number.isNaN(date.getTime()) && date.toISOString().startsWith(iso) ? iso : undefined
}

const DIGITS = ['không', 'một', 'hai', 'ba', 'bốn', 'năm', 'sáu', 'bảy', 'tám', 'chín']

function readGroup3(g: number, full: boolean): string {
  const h = Math.floor(g / 100)
  const t = Math.floor((g % 100) / 10)
  const o = g % 10
  if (h === 0 && t === 0 && o === 0) return ''
  let s = ''
  if (h > 0 || full) s += `${DIGITS[h]} trăm `
  if (t === 0 && o > 0) {
    if (h > 0 || full) s += 'lẻ '
    s += DIGITS[o]
  } else if (t === 1) {
    s += 'mười '
    if (o === 5) s += 'lăm'
    else if (o > 0) s += DIGITS[o]
  } else if (t > 1) {
    s += `${DIGITS[t]} mươi `
    if (o === 1) s += 'mốt'
    else if (o === 4) s += 'tư'
    else if (o === 5) s += 'lăm'
    else if (o > 0) s += DIGITS[o]
  }
  return s.trim()
}

export function formatVNDWords(amount: number): string {
  const n = Math.round(Math.abs(amount))
  if (n === 0) return 'Không đồng'

  const units = ['', 'nghìn', 'triệu', 'tỷ', 'nghìn tỷ', 'triệu tỷ']
  const groups: number[] = []
  let temp = n
  while (temp > 0) {
    groups.push(temp % 1000)
    temp = Math.floor(temp / 1000)
  }

  let result = ''
  for (let i = groups.length - 1; i >= 0; i--) {
    const g = groups[i]
    if (g > 0) {
      const gStr = readGroup3(g, i < groups.length - 1)
      if (gStr) {
        result += `${gStr} ${units[i]} `
      }
    }
  }

  result = result.trim().replace(/\s+/g, ' ')
  if (!result) return 'Không đồng'
  // Viết hoa chữ cái đầu tiên
  const capitalized = result.charAt(0).toUpperCase() + result.slice(1)
  return `${capitalized} đồng chẵn.`
}
