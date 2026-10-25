import { applyRuntimeBookingRules } from './booking-rules'
import { applyRuntimeBusinessRules } from './business-rules'

const REQUIRED_CONFIG_KEYS = [
  'DEPOSIT_RATE',
  'QUOTE_VALID_HOURS',
  'DEMURRAGE_PER_HOUR',
  'MAX_CONTINUOUS_HOURS',
  'MIN_REST_MINUTES',
  'VITAL_CHECK_INTERVAL_HOURS',
  'MIN_LEAD_DAYS',
] as const

export function applyRuntimeBusinessConfig(configs: Record<string, string>) {
  const readNumber = (key: (typeof REQUIRED_CONFIG_KEYS)[number]) => {
    const raw = configs[key]
    if (raw === undefined || raw.trim() === '') {
      throw new Error(`Thiếu tham số nghiệp vụ bắt buộc: ${key}.`)
    }
    const value = Number(raw)
    if (!Number.isFinite(value)) {
      throw new Error(`Tham số nghiệp vụ ${key} không phải số hợp lệ.`)
    }
    return value
  }
  const values = {
    DEPOSIT_RATE: readNumber('DEPOSIT_RATE'),
    QUOTE_VALID_HOURS: readNumber('QUOTE_VALID_HOURS'),
    DEMURRAGE_PER_HOUR: readNumber('DEMURRAGE_PER_HOUR'),
    MAX_CONTINUOUS_HOURS: readNumber('MAX_CONTINUOUS_HOURS'),
    MIN_REST_MINUTES: readNumber('MIN_REST_MINUTES'),
    VITAL_CHECK_INTERVAL_HOURS: readNumber('VITAL_CHECK_INTERVAL_HOURS'),
    MIN_LEAD_DAYS: readNumber('MIN_LEAD_DAYS'),
  }

  if (values.DEPOSIT_RATE <= 0 || values.DEPOSIT_RATE >= 1) {
    throw new Error('Tỷ lệ đặt cọc phải lớn hơn 0 và nhỏ hơn 1.')
  }
  applyRuntimeBookingRules({
    depositRate: values.DEPOSIT_RATE,
    quoteValidHours: values.QUOTE_VALID_HOURS,
    demurragePerHour: values.DEMURRAGE_PER_HOUR,
    maxContinuousHours: values.MAX_CONTINUOUS_HOURS,
    minRestMinutes: values.MIN_REST_MINUTES,
    vitalCheckIntervalHours: values.VITAL_CHECK_INTERVAL_HOURS,
  })
  applyRuntimeBusinessRules({ minLeadDays: values.MIN_LEAD_DAYS })
}
