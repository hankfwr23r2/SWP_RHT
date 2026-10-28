import { HOUR } from '../config/business-rules'
import type { Time } from './dates'

const pad = (n: number) => String(n).padStart(2, '0')

export const formatVND = (n: number) => Math.round(n).toLocaleString('en-US') + ' ₫'
export const formatDate = (t: Time) => { const d = new Date(t); return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}` }
export const formatDateTime = (t: Time) => { const d = new Date(t); return `${formatDate(d)} ${pad(d.getHours())}:${pad(d.getMinutes())}` }
export const formatClock = (t: Time) => { const d = new Date(t); return `${pad(d.getHours())}:${pad(d.getMinutes())}` }

// "x giờ yy phút" còn lại tới mốc t (không âm)
export function timeLeftText(t: number, now = Date.now()) {
  const ms = Math.max(0, t - now)
  return `${Math.floor(ms / HOUR)} giờ ${pad(Math.floor((ms % HOUR) / 60000))} phút`
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
