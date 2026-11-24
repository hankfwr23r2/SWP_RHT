export type BookingServiceCategory = 'stall' | 'feed' | 'water' | 'insurance'
export type BookingRequiredServiceCategory = Exclude<BookingServiceCategory, 'water'>
export type ServiceBillingUnit = 'PER_TRIP' | 'PER_HORSE' | 'PER_HORSE_DAY'

export const SERVICE_BILLING_UNIT_LABEL: Record<ServiceBillingUnit, string> = {
  PER_TRIP: 'mỗi chuyến',
  PER_HORSE: 'mỗi ngựa/chuyến',
  PER_HORSE_DAY: 'mỗi ngựa × ngày',
}
