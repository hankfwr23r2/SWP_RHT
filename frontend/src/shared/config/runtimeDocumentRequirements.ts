import type { DocumentRequirement } from '@shared/api/documentRequirements'
import { HORSE_DOC_TYPES, type HorseDocType } from './booking-rules'

export let ACTIVE_DOCUMENT_REQUIREMENTS: DocumentRequirement[] = []
let requirementsLoaded = false

export function applyDocumentRequirements(requirements: DocumentRequirement[]) {
  requirementsLoaded = true
  ACTIVE_DOCUMENT_REQUIREMENTS = requirements
}

export function requiredHorseDocumentTypes(tripType?: 'domestic' | 'international', country?: string) {
  if (!requirementsLoaded) return [...HORSE_DOC_TYPES]
  return horseTypesForScope(tripType, country)
}

function horseTypesForScope(tripType?: 'domestic' | 'international', country?: string) {
  const normalizedCountry = normalizeCountry(country)
  return [...new Set(ACTIVE_DOCUMENT_REQUIREMENTS
    .filter(item => item.documentGroup === 'HORSE' && item.required && item.status === 'ACTIVE')
    .filter(item => !tripType ? item.tripType === 'ALL' : item.tripType === 'ALL' || item.tripType === tripType.toUpperCase())
    .filter(item => !country ? item.country === 'ALL' : item.country === 'ALL' || item.country === normalizedCountry)
    .map(item => item.documentType)
    .filter((type): type is HorseDocType => type === 'passport' || type === 'vaccine' || type === 'lab'))]
}

export function requiredClearanceDocumentTypes(tripType: 'domestic' | 'international', country?: string) {
  if (!requirementsLoaded) return null
  const normalizedCountry = normalizeCountry(country)
  return [...new Set(ACTIVE_DOCUMENT_REQUIREMENTS
    .filter(item => item.documentGroup === 'CLEARANCE' && item.required && item.status === 'ACTIVE')
    .filter(item => item.tripType === 'ALL' || item.tripType === tripType.toUpperCase())
    .filter(item => item.country === 'ALL' || !country || item.country === normalizedCountry)
    .map(item => item.documentType)
    .filter((type): type is ClearanceType => type in CLEARANCE_TYPE_SET))]
}

const CLEARANCE_TYPE_SET = {
  health_cert: true,
  poa: true,
  customs_declaration: true,
  import_permit: true,
  quarantine_cert: true,
  ata_carnet: true,
  commercial_invoice: true,
} as const
type ClearanceType = keyof typeof CLEARANCE_TYPE_SET

function normalizeCountry(country?: string) {
  const value = country?.trim().toUpperCase()
  if (value === 'CAMBODIA' || value === 'CAMPUCHIA') return 'KH'
  if (value === 'LAOS' || value === 'LAO' || value === 'LÀO') return 'LA'
  if (value === 'VIETNAM' || value === 'VIỆT NAM' || value === 'VN') return 'VN'
  return value
}
