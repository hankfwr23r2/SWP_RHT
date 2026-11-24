import { partiesApi, type Party, type PartyCreate, type PartyRole, type PartyRoleCreate } from '@shared/api/parties'
import { useCatalogAction } from '@backoffice/shared/useCatalogAction'
import { useCatalogForm } from '@backoffice/shared/useCatalogForm'

export const partyTypes = ['PERSON', 'ORGANIZATION'] satisfies PartyCreate['partyType'][]
export const assignableRoles = ['DEST_AGENT', 'STABLE', 'VET', 'RESCUE', 'INSURER'] satisfies PartyRoleCreate['roleType'][]

export function usePartyEditor(party: Party | undefined, reload: () => unknown, close: () => void) {
  const form = useCatalogForm({
    partyType: party?.partyType ?? '', displayName: party?.displayName ?? '', countryCode: party?.countryCode ?? '',
    taxCode: party?.taxCode ?? '', email: party?.email ?? '', phone: party?.phone ?? '', address: party?.address ?? '',
  })
  const action = useCatalogAction(reload)
  async function save() {
    const { values } = form
    const body: PartyCreate = {
      partyType: values.partyType as PartyCreate['partyType'],
      displayName: values.displayName.trim(),
      countryCode: values.countryCode,
      taxCode: values.taxCode.trim() || undefined,
      email: values.email.trim() || undefined,
      phone: values.phone.trim() || undefined,
      address: values.address.trim() || undefined,
    }
    const id = party?.id
    if (await action.run(() => id == null ? partiesApi.create(body) : partiesApi.update(id, body))) close()
  }
  return { ...form, ...action, save }
}

export function usePartyRoleEditor(id: number, role: PartyRole | undefined, reload: () => unknown, close: () => void) {
  const form = useCatalogForm({ roleType: role?.roleType ?? '', validFrom: role?.validFrom ?? '', validTo: role?.validTo ?? '' })
  const action = useCatalogAction(reload, 'validTo')
  async function save() {
    const body = { validFrom: form.values.validFrom, validTo: form.values.validTo || undefined }
    const roleType = form.values.roleType as PartyRoleCreate['roleType']
    if (await action.run(() => role?.roleType
      ? partiesApi.updateRole(id, role.roleType, body)
      : partiesApi.addRole(id, { ...body, roleType }))) close()
  }
  return { ...form, ...action, save }
}
