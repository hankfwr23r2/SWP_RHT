import { PROVINCES_DATA } from '@shared/config/administrative'
import { fromIsoDay, isDepartureAllowed, toIsoDay } from '@shared/lib/booking'
import { isValidContactName, isValidRegionalPhone } from '@shared/lib/contactValidation'
import { isValidLocationDetail } from '@shared/lib/locationValidation'
import { countriesOf, type BookingDraft } from './draft'

function hasValidAddress(address: string, country: 'VN' | 'KH' | 'LA'): boolean {
  if (typeof address !== 'string') return false
  const [detail, wardName, provinceName] = address.split(',').map(part => part.trim())
  if (!detail || !wardName || !provinceName || !isValidLocationDetail(detail)) return false

  const province = PROVINCES_DATA.find(item =>
    item.country === country && item.name.toLocaleLowerCase() === provinceName.toLocaleLowerCase(),
  )
  return !!province && province.wards.some(ward => ward.name.toLocaleLowerCase() === wardName.toLocaleLowerCase())
}

/** Validates the persisted form of step 1 before later booking steps can be opened. */
export function isBookingRouteReady(draft: BookingDraft): boolean {
  const countries = countriesOf(draft)
  if (!countries || !['out', 'in'].includes(draft.direction)) return false

  if (draft.type === 'international' && draft.partner !== 'KH' && draft.partner !== 'LA') return false

  if (!/^\d{4}-\d{2}-\d{2}$/.test(draft.departDate)) return false
  const departure = fromIsoDay(draft.departDate)
  if (!Number.isFinite(departure) || toIsoDay(departure) !== draft.departDate || !isDepartureAllowed(departure)) return false

  if (!hasValidAddress(draft.originId, countries.origin) || !hasValidAddress(draft.destId, countries.dest)) return false

  const validParty = (party: BookingDraft['consignor']) =>
    typeof party?.name === 'string'
    && isValidContactName(party.name)
    && typeof party.phone === 'string'
    && isValidRegionalPhone(party.phone)
    && typeof party.idNumber === 'string'
    && !!party.idNumber.trim()

  return validParty(draft.consignor) && validParty(draft.consignee)
}
