import { useLoad } from '@shared/hooks/useLoad'
import {
  regulationsApi, type CountryRule, type CountryRuleCreate, type DocumentType, type DocumentTypeCreate,
  type DocumentRequirement, type DocumentRequirementCreate,
} from '@shared/api/regulations'
import { useCatalogAction } from '@backoffice/shared/useCatalogAction'
import { useCatalogForm } from '@backoffice/shared/useCatalogForm'

export const owners = [
  'HORSE', 'BOOKING', 'BOOKING_HORSE', 'HORSE_LEG', 'VEHICLE_TRIP', 'INSURANCE', 'INCIDENT',
  'COST_ITEM', 'HANDOVER', 'VEHICLE', 'STAFF', 'PARTY', 'CUSTOMER',
] satisfies DocumentTypeCreate['ownerType'][]
export const directions = ['INTERPROVINCE', 'EXPORT', 'IMPORT'] satisfies CountryRuleCreate['direction'][]
export const submitters = ['CUSTOMER', 'CONSIGNEE', 'COMPANY', 'AGENT'] satisfies DocumentRequirementCreate['submitterType'][]

export function useDocumentTypesEditor(item: DocumentType | undefined, reload: () => unknown, close: () => void) {
  const form = useCatalogForm({ code: item?.code ?? '', name: item?.name ?? '', ownerType: item?.ownerType ?? '' })
  const action = useCatalogAction(reload)
  async function save() {
    const { values } = form
    const body: DocumentTypeCreate = {
      code: values.code.trim(), name: values.name.trim(), ownerType: values.ownerType as DocumentTypeCreate['ownerType'],
    }
    const id = item?.id
    if (await action.run(() => id == null ? regulationsApi.createType(body) : regulationsApi.updateType(id, body))) close()
  }
  return { ...form, ...action, save }
}

export function useRuleEditor(item: CountryRule | undefined, reload: () => unknown, close: () => void) {
  const form = useCatalogForm({
    countryCode: item?.countryCode ?? '', direction: item?.direction ?? '',
    permitLeadDays: String(item?.permitLeadDays ?? ''), certificateLeadDays: String(item?.certificateLeadDays ?? ''),
    validFrom: item?.validFrom ?? '', validTo: item?.validTo ?? '',
  })
  const action = useCatalogAction(reload)
  async function save() {
    const { values } = form
    const body: CountryRuleCreate = {
      countryCode: values.countryCode,
      direction: values.direction as CountryRuleCreate['direction'],
      permitLeadDays: Number(values.permitLeadDays),
      certificateLeadDays: Number(values.certificateLeadDays),
      validFrom: values.validFrom,
      validTo: values.validTo || undefined,
    }
    const id = item?.id
    if (await action.run(() => id == null ? regulationsApi.createRule(body) : regulationsApi.updateRule(id, body))) close()
  }
  return { ...form, ...action, save }
}

export function useRequirements(ruleId: number) {
  return useLoad(() => regulationsApi.requirements(ruleId), [ruleId], { toastOnError: false })
}

export function useRequirementEditor(ruleId: number, item: DocumentRequirement | undefined, reload: () => unknown, close: () => void) {
  const form = useCatalogForm({
    documentTypeId: String(item?.documentTypeId ?? ''), gateLocationId: String(item?.gateLocationId ?? ''),
    submitterType: item?.submitterType ?? '', mandatory: String(item?.mandatory ?? true),
  })
  const action = useCatalogAction(reload)
  async function save() {
    const { values } = form
    const body: DocumentRequirementCreate = {
      documentTypeId: Number(values.documentTypeId),
      gateLocationId: values.gateLocationId ? Number(values.gateLocationId) : undefined,
      submitterType: values.submitterType as DocumentRequirementCreate['submitterType'],
      mandatory: values.mandatory === 'true',
    }
    const id = item?.id
    if (await action.run(() => id == null
      ? regulationsApi.createRequirement(ruleId, body)
      : regulationsApi.updateRequirement(ruleId, id, body))) close()
  }
  return { ...form, ...action, save }
}
