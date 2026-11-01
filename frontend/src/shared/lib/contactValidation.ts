import type { CountryCode } from '../config/network'

const NAME_PATTERN = /^[\p{L}\p{M}]+(?:[ '\u2019-][\p{L}\p{M}]+)*$/u
const CONTACT_NAME_PATTERN = /^(?=.*[\p{L}])[\p{L}\p{M}\d]+(?:[ .,'&()/+-][\p{L}\p{M}\d]+)*$/u
const PHONE_PATTERN = /^(?:(?:\+?84|0)[\s().-]*(?:3|5|7|8|9)(?:[\s().-]*\d){8}|(?:\+?855|0)[\s().-]*(?:1|6|7|8|9)(?:[\s().-]*\d){7,8}|(?:\+?856|0)[\s().-]*20(?:[\s().-]*\d){8})$/
export const PHONE_CALLING_CODES: Record<CountryCode, string> = {
  VN: '+84',
  KH: '+855',
  LA: '+856',
}

export function isValidPersonName(value: string): boolean {
  const name = value.trim()
  return name.length >= 2 && name.length <= 100 && NAME_PATTERN.test(name)
}

export function isValidContactName(value: string): boolean {
  const name = value.trim()
  return name.length >= 2 && name.length <= 100 && CONTACT_NAME_PATTERN.test(name)
}

export function isValidRegionalPhone(value: string): boolean {
  return PHONE_PATTERN.test(value.trim())
}

export function toInternationalPhone(country: CountryCode, value: string): string {
  const digits = value.replace(/\D/g, '')
  if (!digits) return ''
  const nationalNumber = digits.startsWith('0') ? digits.slice(1) : digits
  return `${PHONE_CALLING_CODES[country]}${nationalNumber}`
}

export function isValidPhoneForCountry(country: CountryCode, value: string): boolean {
  return isValidRegionalPhone(toInternationalPhone(country, value))
}

// Đăng ký khách chỉ nhận Gmail (khớp ContactPatterns.GMAIL ở backend)
export function isValidGmail(value: string): boolean {
  return /^[A-Za-z0-9.+_-]+@gmail\.com$/i.test(value.trim())
}

export function isValidEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value.trim())
}
